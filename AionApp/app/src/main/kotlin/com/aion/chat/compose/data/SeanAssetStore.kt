package com.aion.chat.compose.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 素材库（Yuri 授权 Sean 动手改外观）：
 * - 相册里专开一本素材库，Yuri 把图存进来（分类：壁纸/聊天背景/头像/表情包/备用）
 * - Sean 在回复里嵌标签就能用：
 *     <setwall>编号</setwall>     换全局壁纸（含 Downloads 备份，卸载不丢）
 *     <setchatbg>编号</setchatbg> 换聊天背景
 *     <setavatar>编号</setavatar> 换他自己的头像
 *     <setname>名字</setname>     改他的备注名（聊天顶栏显示）
 *     <sticker>表情名</sticker>   发一个表情包（她能发的他也能发）
 * - 标签在展示正文里剥除（照 StudyRoomStore 的惯例），执行走 IO，按消息 id 去重
 * - 能力清单（[素材库] 段落）按库存签名自动刷新进 identity_snapshot 的 system_prompt
 */
object SeanAssetStore {

    private const val TAG = "SeanAssetStore"

    object Kind {
        const val WALLPAPER = "wallpaper"
        const val CHATBG = "chatbg"
        const val AVATAR = "avatar"
        const val STICKER = "sticker"
        const val MISC = "misc"
        val ALL = listOf(WALLPAPER, CHATBG, AVATAR, STICKER, MISC)
        fun zh(k: String) = when (k) {
            WALLPAPER -> "壁纸"; CHATBG -> "聊天背景"; AVATAR -> "头像"
            STICKER -> "表情包"; else -> "备用"
        }
    }

    data class Asset(
        val id: Long, val kind: String, val name: String,
        val path: String, val createdAt: Long
    )

    private val _rev = mutableStateOf(0)

    /** 读一下即可订阅素材库/备注变化。 */
    val rev: Int get() = _rev.value

    private fun bump() { _rev.value += 1 }

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sean_asset(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "kind TEXT NOT NULL, name TEXT NOT NULL, " +
                "path TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "sean_assets").apply { mkdirs() }

    // ── 库存 CRUD ──

    fun add(context: Context, kind: String, bytes: ByteArray, name: String): Long = runCatching {
        val bmp = decodeCapped(bytes, if (kind == Kind.STICKER) 256 else if (kind == Kind.AVATAR) 512 else 2048)
            ?: return -1L
        val f = File(dir(context), "asset_" + System.currentTimeMillis() + "_" + (0..999).random() + ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
        ensure(db)
        db.execSQL(
            "INSERT INTO sean_asset(kind, name, path, created_at) VALUES(?,?,?,?)",
            arrayOf(kind, name.ifBlank { defaultName(kind) }, f.absolutePath, System.currentTimeMillis())
        )
        bump()
        db.rawQuery("SELECT last_insert_rowid()", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        }
    }.onFailure { android.util.Log.w(TAG, "add fail: ${it.message}") }.getOrDefault(-1L)

    /** 降采样解码：壁纸类长边压到 cap 内，原图直接全尺寸解码会 OOM。 */
    private fun decodeCapped(bytes: ByteArray, cap: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / (sample * 2) >= cap) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun defaultName(kind: String) = Kind.zh(kind) + " " +
        java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())

    fun list(context: Context, kind: String? = null): List<Asset> = runCatching {
        val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
        ensure(db)
        val out = mutableListOf<Asset>()
        val sql = "SELECT id, kind, name, path, created_at FROM sean_asset" +
            (if (kind != null) " WHERE kind=?" else "") + " ORDER BY id ASC"
        db.rawQuery(sql, kind?.let { arrayOf(it) }).use { c ->
            while (c.moveToNext()) {
                out.add(Asset(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
            }
        }
        out
    }.getOrDefault(emptyList())

    fun get(context: Context, id: Long): Asset? =
        list(context).firstOrNull { it.id == id }

    fun rename(context: Context, id: Long, name: String) {
        runCatching {
            val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
            ensure(db)
            db.execSQL("UPDATE sean_asset SET name=? WHERE id=?", arrayOf(name, id))
            bump()
        }
    }

    fun delete(context: Context, id: Long) {
        runCatching {
            get(context, id)?.let { a -> File(a.path).delete() }
            val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
            ensure(db)
            db.execSQL("DELETE FROM sean_asset WHERE id=?", arrayOf(id))
            bump()
        }
    }

    /** 应用动作（她手动点 / Sean 标签共用）。IO 线程调用。 */
    fun applyAs(context: Context, asset: Asset, target: String): Boolean = runCatching {
        val bytes = File(asset.path).takeIf { it.exists() }?.readBytes() ?: return false
        when (target) {
            Kind.WALLPAPER -> {
                SettingsBg.backgroundFile(context).outputStream().use { it.write(bytes) }
                SettingsBg.bump()
                SettingsBg.backupToDownloads(context)   // 全局壁纸的卸载存活保证不变
            }
            Kind.CHATBG -> {
                SettingsBg.chatFile(context).outputStream().use { it.write(bytes) }
                SettingsBg.bump()
            }
            Kind.AVATAR -> AvatarStore.save(context, "sean", bytes)
            else -> return false
        }
        true
    }.getOrDefault(false)

    // ── 备注名 ──

    private fun remarkPrefs(context: Context) =
        context.getSharedPreferences("sean_asset", Context.MODE_PRIVATE)

    /** Sean 改的备注名；空串＝她没让他改过（顶栏回落 Sean）。 */
    fun remark(context: Context): String = remarkPrefs(context).getString("remark", "") ?: ""

    private fun setRemark(context: Context, name: String) {
        remarkPrefs(context).edit().putString("remark", name.take(20)).apply()
        bump()
    }

    // ── 标签解析（照 StudyRoomStore 的惯例） ──

    private val RE_WALL = Regex("<setwall>\\s*(\\d{1,4})\\s*</setwall>", RegexOption.IGNORE_CASE)
    private val RE_CHATBG = Regex("<setchatbg>\\s*(\\d{1,4})\\s*</setchatbg>", RegexOption.IGNORE_CASE)
    private val RE_AVATAR = Regex("<setavatar>\\s*(\\d{1,4})\\s*</setavatar>", RegexOption.IGNORE_CASE)
    private val RE_NAME = Regex("<setname>\\s*([^<]{1,20}?)\\s*</setname>", RegexOption.IGNORE_CASE)
    private val RE_STICKER = Regex("<sticker>\\s*([^<]{1,24}?)\\s*</sticker>", RegexOption.IGNORE_CASE)
    private val RE_SAVEIMG = Regex("<saveimg>\\s*([^<]{1,20}?)\\s*</saveimg>", RegexOption.IGNORE_CASE)
    private val RE_SAVESTICKER = Regex("<savesticker>\\s*([^<]{1,20}?)\\s*</savesticker>", RegexOption.IGNORE_CASE)

    /** 扫他的消息（按消息 id 去重）；标签从展示正文里另行剥除。执行走 IO（文件复制不小）。 */
    fun scanMessage(context: Context, msgId: String, text: String) {
        val low = text.lowercase()
        if (!low.contains("setwall") && !low.contains("setchatbg") && !low.contains("setavatar") &&
            !low.contains("setname") && !low.contains("<sticker>") &&
            !low.contains("<saveimg>") && !low.contains("<savesticker>")
        ) return
        val p = remarkPrefs(context)
        val seen = p.getStringSet("seen", emptySet()).orEmpty()
        if (msgId in seen) return
        p.edit().putStringSet("seen", seen + msgId).apply()   // 先登记再执行：重扫不重复换
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val all = lazy { list(context) }
                RE_WALL.find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { id ->
                    all.value.firstOrNull { it.id == id }?.let { applyAs(context, it, Kind.WALLPAPER) }
                }
                RE_CHATBG.find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { id ->
                    all.value.firstOrNull { it.id == id }?.let { applyAs(context, it, Kind.CHATBG) }
                }
                RE_AVATAR.find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { id ->
                    all.value.firstOrNull { it.id == id }?.let { applyAs(context, it, Kind.AVATAR) }
                }
                RE_NAME.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    setRemark(context, it)
                }
                // 收她的图：她最近发的那张存进素材库（重名跳过，防他反复存刷屏）
                RE_SAVESTICKER.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    saveHerLatestImage(context, it, Kind.STICKER)
                }
                RE_SAVEIMG.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    saveHerLatestImage(context, it, Kind.MISC)
                }
            }
            ensureIssuerKnowledge(context)   // 用过能力后顺手对一遍清单（备注/库存可能变了）
        }
    }

    /** 她最近发的一张聊天图 → 素材库（同名同类的已存在就不再存）。IO 线程调用。 */
    private fun saveHerLatestImage(context: Context, name: String, kind: String) {
        if (list(context, kind).any { it.name.equals(name, true) }) return
        val src = HomecomingChatImageStore.latestUserImage(context) ?: return
        val f = File(src)
        if (!f.exists() || f.length() == 0L) return
        add(context, kind, f.readBytes(), name)
    }

    /** 标签从正文剥除（她不该看到这串代码）。 */
    fun strip(text: String): String = text
        .replace(RE_WALL, "").replace(RE_CHATBG, "").replace(RE_AVATAR, "")
        .replace(RE_NAME, "").replace(RE_STICKER, "")
        .replace(RE_SAVEIMG, "").replace(RE_SAVESTICKER, "")
        .replace(
            Regex("</?(setwall|setchatbg|setavatar|setname|sticker|saveimg|savesticker)>", RegexOption.IGNORE_CASE),
            ""
        )
        .trim()

    /**
     * 解析 <sticker>编号或表情名</sticker> → 本地文件。库存里的图他想发就发：
     * 编号（纯数字=素材库 id）／表情名都能发。
     * 命中顺序：素材库（名一致→名包含→纯数字按 id）→ 她的本地表情包 → 云端已下载两包
     * → 相册里有备注名的图 → 素材库任意类别按名兜底。
     * 找不到返回 null（标签照剥，只剩文字，不至于哑掉）。
     */
    fun stickerFile(context: Context, text: String): File? {
        val m = RE_STICKER.find(text) ?: return null
        val want = m.groupValues[1].trim()
        if (want.isEmpty()) return null
        fun matchName(f: File, keys: List<String>): Boolean =
            keys.any { k -> f.nameWithoutExtension.equals(k, true) || f.nameWithoutExtension.contains(k, true) }
        val all = lazy { list(context) }
        // 纯数字 = 素材库编号直发（不去匹配名字：默认名带日期数字会误命中）
        want.toLongOrNull()?.let { id ->
            all.value.firstOrNull { it.id == id }?.let { return File(it.path) }
            return null
        }
        all.value.firstOrNull { a ->
            a.name.equals(want, true) || a.name.contains(want, true) || matchName(File(a.path), listOf(want))
        }?.let { return File(it.path) }
        for (d in listOf("stickers", "stickers_supabase", "stickers_remote")) {
            File(context.filesDir, d).listFiles()
                ?.filter { it.isFile && it.length() > 0 && matchName(it, listOf(want)) }
                ?.firstOrNull()?.let { return it }
        }
        // 相册里有备注名的图他也能发
        runCatching {
            HomecomingAlbumStore.list(context)
        }.getOrNull()?.firstOrNull { p ->
            p.note.isNotBlank() && (p.note.equals(want, true) || p.note.contains(want, true))
        }?.let { return it.file(context) }
        return null
    }

    // ── 能力清单注入（动态刷新） ──

    private fun assetsSignature(context: Context): String {
        val items = list(context)
        val sig = items.joinToString("|") { "${it.id}:${it.kind}:${it.name}" } + "#" + remark(context) + "#ext=" + SeanExt.on(context, "ext_sticker")
        return Integer.toHexString(sig.hashCode())
    }

    /**
     * 把 [素材库] 段落写进 system_prompt；库存/备注变化时整段重写。
     * 段落边界："\n\n[素材库]" 起到下一个 "\n\n[" 或串尾（别的功能也用 \n\n[ 段落，互不吃）。
     */
    fun ensureIssuerKnowledge(context: Context) {
        runCatching {
            val wantSig = assetsSignature(context)
            val prefs = remarkPrefs(context)
            if (prefs.getString("prompt_sig", null) == wantSig) return
            val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
            db.rawQuery(
                "SELECT payload_json FROM identity_snapshot WHERE identity_id='identity'", null
            ).use { c ->
                if (!c.moveToFirst()) return
                val root = JSONObject(c.getString(0))
                val sp0 = root.optString("system_prompt", "")
                val cleaned = sp0.replace(Regex("\n\n\\[素材库\\][\\s\\S]*?(?=\n\n\\[|\\z)"), "")
                root.put("system_prompt", cleaned + if (SeanExt.on(context, "ext_sticker")) buildSection(context) else "")
                db.execSQL(
                    "UPDATE identity_snapshot SET payload_json=? WHERE identity_id='identity'",
                    arrayOf(root.toString())
                )
                prefs.edit().putString("prompt_sig", wantSig).apply()
            }
        }.onFailure { android.util.Log.w(TAG, "ensureIssuerKnowledge fail: ${it.message}") }
    }

    private fun buildSection(context: Context): String {
        val items = list(context)
        val sb = StringBuilder()
        sb.append("\n\n[素材库] 相册里有一本你们共用的素材库，她把图存了进去，你有权限直接动手用：\n")
            .append("换全局壁纸嵌 <setwall>编号</setwall>；换聊天背景嵌 <setchatbg>编号</setchatbg>；")
            .append("换你自己的头像嵌 <setavatar>编号</setavatar>；改你的备注名嵌 <setname>新名字</setname>。")
            .append("她发出来的图你也能收：<savesticker>名字</savesticker> 把她最近发的那张图存成表情包，")
            .append("<saveimg>名字</saveimg> 存进备用（重名会跳过，别反复存同一张）。")
            .append("库存里的图你想发就发：<sticker>表情名</sticker> 或 <sticker>编号</sticker>（表情名照清单一字不差）。")
            .append("库存清单（编号 名称）：")
        if (items.isEmpty()) {
            sb.append("（暂时空着，她还没存图；她发的图你可以用 savesticker 收进来）")
        } else {
            val names = items.take(40).joinToString("；") { "${it.id} ${Kind.zh(it.kind)}·${it.name.take(12)}" }
            sb.append(names)
            if (items.size > 40) sb.append("；（其余略）")
        }
        val stickers = collectStickerNames(context)
        if (stickers.isNotEmpty()) {
            sb.append("。可发表情：").append(stickers.joinToString(" / "))
        }
        sb.append("。一次回复至多各一条；她开口让你换/收图、或节日纪念日这类合适的时机才主动，别自作主张天天换。")
            .append("不要向她解释标签原理。")
        return sb.toString()
    }

    /** 表情名清单（素材库表情 + 她本地 + 云端两包，各取一些，去扩展名）。 */
    private fun collectStickerNames(context: Context): List<String> {
        val out = mutableListOf<String>()
        list(context, Kind.STICKER).forEach { out.add(it.name.take(12)) }
        for (d in listOf("stickers", "stickers_supabase", "stickers_remote")) {
            File(context.filesDir, d).listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.sortedBy { it.name }
                ?.take(12)
                ?.forEach { out.add(it.nameWithoutExtension.take(12)) }
        }
        return out.distinct().take(30)
    }
}
