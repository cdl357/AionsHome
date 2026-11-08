package com.aion.chat.compose.data

import android.content.Context
import com.aion.chat.homecoming.HomecomingBackupScheduler
import com.aion.chat.homecoming.HomecomingChatEngine
import com.aion.chat.homecoming.HomecomingChatRepository
import com.aion.chat.homecoming.HomecomingContextBuilder
import com.aion.chat.homecoming.HomecomingDatabase
import com.aion.chat.homecoming.HomecomingIdentityRepository
import com.aion.chat.homecoming.HomecomingMemoryRepository
import com.aion.chat.homecoming.HomecomingModeStore
import com.aion.chat.homecoming.HomecomingModelGateway
import com.aion.chat.homecoming.HomecomingRouteVault
import java.io.File

/**
 * Compose 聊天运行时：把 AionsHome 现有 homecoming 数据层原样组装起来（零重写）。
 * - 线路配置来自设置页保存的 routes.json（HomecomingRouteVault.fromPlaintext）
 * - 未配置线路时 vault 为空，聊天页如实显示「线路未配置」
 */
class HomecomingChatWiring(context: Context) {

    private val appContext = context.applicationContext

    val modeStore = HomecomingModeStore(appContext)
    val database = HomecomingDatabase(appContext)
    val deviceId: String = java.util.UUID.randomUUID().toString()
    val epochId: String
    val chats: HomecomingChatRepository
    val identities: HomecomingIdentityRepository
    val memories: HomecomingMemoryRepository
    val contextBuilder: HomecomingContextBuilder
    val gateway: HomecomingModelGateway
    val engine: HomecomingChatEngine
    val vault: HomecomingRouteVault

    init {
        // epoch 连续性：epoch 还在就续用（mode 位丢了只是恢复 active，不换代）——
        // 换代 = 聊天时间线按 epoch_id 过滤会整条变空（历史还在库里但全被过滤）
        if (modeStore.currentEpoch().isEmpty()) {
            modeStore.activate()
        } else if (!modeStore.isActive() && !modeStore.isFrozen() && !modeStore.isReturning()) {
            modeStore.resumeActive()
        }
        epochId = modeStore.currentEpoch()
        chats = HomecomingChatRepository(database, epochId, deviceId)
        // 历史 epoch 的消息并回当前 epoch（时间线为空才触发）——聊天记录不再"被消失"
        runCatching {
            chats.healEpoch(HomecomingChatWiring.TIMELINE)
            chats.healEpoch("companion_private")
        }
        // 消息树 v1：parent 链一次性回填（幂等；旧库升级后第一条消息起全链就位）
        runCatching { HomecomingChatRepository.backfillParents(appContext) }
        identities = HomecomingIdentityRepository(database)
        memories = HomecomingMemoryRepository(database, epochId, deviceId)
        contextBuilder = HomecomingContextBuilder(identities, memories, chats::listMessages, 48_000)
        vault = buildVault(appContext)
        val customReq = loadCustomRequest(appContext)
        val cp = appContext.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        val caching = cp.getBoolean("prompt_caching", true)
        val ttl = cp.getString("prompt_caching_ttl", "5m")
        gateway = HomecomingModelGateway(
            vault, HomecomingModelGateway.OkHttpSseTransport(),
            customReq?.first, customReq?.second, caching, ttl
        )
        engine = HomecomingChatEngine(chats, contextBuilder, gateway)
        com.aion.chat.compose.data.MemoryTools.initContext(appContext)
        // MCP 工具注册表异步刷新（不阻塞装配；网关同步读缓存，下一轮消息即可用）
        Thread {
            runCatching { com.aion.chat.compose.data.McpClient.refreshTools(appContext) }
        }.start()
    }

    private fun buildVault(context: Context): HomecomingRouteVault {
        // 配置毒化（旧格式/半截 JSON）不再让 safeCreate 返回 null：兜底空 vault，
        // 聊天页据此显示「未配置线路」，设置页重存即可恢复
        return try {
            val config = HomecomingRouteConfig.load(context)
            val plaintext = config?.toString()?.toByteArray()
                ?: "{\"chat\":[]}".toByteArray(Charsets.UTF_8)
            HomecomingRouteVault.fromPlaintext(plaintext)
        } catch (e: Exception) {
            AppCrashLog.write(context, e)
            runCatching { HomecomingRouteVault.fromPlaintext("{\"chat\":[]}".toByteArray(Charsets.UTF_8)) }
                .getOrDefault(HomecomingRouteVault.fromPlaintext("{\"chat\":[]}".toByteArray(Charsets.UTF_8)))
        }
    }

    fun hasRoute(): Boolean = vault.listDescriptors().isNotEmpty()

    fun mainRouteLabel(): String {
        val route = runCatching { vault.resolve("main") }.getOrNull() ?: return ""
        return route.label
    }

    fun mainModelKey(): String {
        val route = runCatching { vault.resolve("main") }.getOrNull() ?: return ""
        return route.models.firstOrNull()?.key ?: ""
    }

    fun listMessages(timelineId: String, limit: Int = 200): List<HomecomingChatRepository.Message> =
        chats.listMessages(timelineId, Long.MAX_VALUE, limit).sortedBy { it.createdAt }

    companion object {
        const val TIMELINE = "main_private"
        const val RESPONDER = "main"
        const val USER = "user"

        /** 自定义请求存 prefs("sean_ext").custom_request = {"headers":{...},"body":{...}} */
        fun loadCustomRequest(context: Context): Pair<Map<String, String>, org.json.JSONObject>? =
            runCatching {
                val raw = context.getSharedPreferences("sean_ext", Context.MODE_PRIVATE)
                    .getString("custom_request", null) ?: return null
                if (raw.isBlank()) return null
                val root = org.json.JSONObject(raw)
                val headers = mutableMapOf<String, String>()
                root.optJSONObject("headers")?.let { h ->
                    h.keys().forEach { k -> if (h.optString(k).isNotBlank()) headers[k] = h.optString(k) }
                }
                Pair(headers.toMap(), root.optJSONObject("body") ?: org.json.JSONObject())
            }.getOrNull()

        /** 返回 null=保存成功；非 null=错误说明（JSON 格式不对等） */
        fun saveCustomRequest(context: Context, headersJson: String, bodyJson: String): String? {
            return runCatching {
                val headers = if (headersJson.isBlank()) org.json.JSONObject() else org.json.JSONObject(headersJson)
                val body = if (bodyJson.isBlank()) org.json.JSONObject() else org.json.JSONObject(bodyJson)
                val out = org.json.JSONObject().put("headers", headers).put("body", body)
                context.getSharedPreferences("sean_ext", Context.MODE_PRIVATE)
                    .edit().putString("custom_request", out.toString()).apply()
                null
            }.getOrElse { e -> e.message ?: "JSON 格式不对" }
        }

        fun chatBackgroundFile(context: Context): File = File(context.filesDir, "chat_bg.jpg")

        /**
         * 唯一合法的构造入口：引擎组装（数据库/身份/记忆/线路加密）任一步失败
         * 都只记崩溃日志并返回 null，绝不把异常抛进 UI 合成期（点页闪退的根源）。
         */
        /** 最近一次装配失败的原因（聊天页胶囊直接显示，不用再翻日志）。 */
        var lastCreateError: String? = null

        fun safeCreate(context: Context): HomecomingChatWiring? =
            runCatching { HomecomingChatWiring(context) }
                .onFailure {
                    AppCrashLog.write(context, it)
                    lastCreateError = it.javaClass.simpleName +
                        (it.message?.take(80)?.let { m -> "：$m" } ?: "")
                }
                .getOrNull()
    }
}
