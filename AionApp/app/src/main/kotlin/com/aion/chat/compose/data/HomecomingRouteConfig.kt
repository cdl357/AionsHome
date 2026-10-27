package com.aion.chat.compose.data

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Compose 聊天的云线路配置（filesDir/routes.json，应用私有目录，不进代码仓）。
 * 配置格式与 HomecomingRouteVault.fromPlaintext 一致：
 * {"chat":[{"route_id":"main","label":"...","provider":"openai_compatible",
 *           "base_url":"...","api_key":"...","models":[{"key":"...","model":"...","vision":true}]}]}
 */
object HomecomingRouteConfig {

    fun file(context: Context): File = File(context.filesDir, "routes.json")

    fun stamp(context: Context): Long = file(context).lastModified()

    fun load(context: Context): JSONObject? = try {
        val f = file(context)
        if (f.exists() && f.length() > 0) JSONObject(f.readText()) else null
    } catch (e: Exception) { null }

    fun save(context: Context, root: JSONObject): Boolean = try {
        file(context).writeText(root.toString())
        true
    } catch (e: Exception) { false }

    private const val BACKUP_NAME = "aionshome-routes-backup.json"

    /**
     * 线路配置跨卸载：私有 routes.json 不在而 Downloads 备份在（每次保存都写了一份）→ 自动恢复。
     * 返回是否发生了恢复。在 MainActivity.onCreate 里、任何组合之前调用。
     */
    fun restoreFromDownloads(context: Context): Boolean = runCatching {
        if (file(context).exists() && file(context).length() > 0) return false
        val resolver = context.contentResolver
        var text: String? = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            resolver.query(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.Downloads._ID),
                android.provider.MediaStore.Downloads.DISPLAY_NAME + " = ?",
                arrayOf(BACKUP_NAME), null
            )?.use { cur ->
                if (cur.moveToFirst()) {
                    val uri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cur.getLong(0)
                    )
                    text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }
        } else {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val bf = java.io.File(dir, BACKUP_NAME)
            if (bf.exists()) text = bf.readText()
        }
        val t = text?.takeIf { it.isNotBlank() } ?: return false
        JSONObject(t)  // 校验是合法 JSON 再落盘
        save(context, org.json.JSONObject(t))
    }.getOrDefault(false)

    /** 首个线路（route_id=main）；未配置返回 null。 */
    fun mainRoute(context: Context): JSONObject? = try {
        val root = load(context) ?: return null
        val chat = root.optJSONArray("chat") ?: return null
        if (chat.length() == 0) null else chat.getJSONObject(0)
    } catch (e: Exception) { null }

    fun buildRoot(label: String, baseUrl: String, apiKey: String, modelKey: String): JSONObject {
        val model = JSONObject()
            .put("key", modelKey)
            .put("model", modelKey)
            .put("vision", true)
        val route = JSONObject()
            .put("route_id", "main")
            .put("label", label.ifBlank { "我的线路" })
            .put("provider", "openai_compatible")
            .put("base_url", baseUrl)
            .put("api_key", apiKey)
            .put("models", org.json.JSONArray().put(model))
        return JSONObject().put("chat", org.json.JSONArray().put(route))
    }
}
