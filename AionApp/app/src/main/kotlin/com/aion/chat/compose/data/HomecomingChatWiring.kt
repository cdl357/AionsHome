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
        if (!modeStore.isActive()) modeStore.activate()
        epochId = modeStore.currentEpoch()
        chats = HomecomingChatRepository(database, epochId, deviceId)
        identities = HomecomingIdentityRepository(database)
        memories = HomecomingMemoryRepository(database, epochId, deviceId)
        contextBuilder = HomecomingContextBuilder(identities, memories, chats::listMessages, 48_000)
        vault = buildVault(appContext)
        gateway = HomecomingModelGateway(vault, HomecomingModelGateway.OkHttpSseTransport())
        engine = HomecomingChatEngine(chats, contextBuilder, gateway)
    }

    private fun buildVault(context: Context): HomecomingRouteVault {
        val config = HomecomingRouteConfig.load(context)
        val plaintext = config?.toString()?.toByteArray()
            ?: "{\"chat\":[]}".toByteArray(Charsets.UTF_8)
        return HomecomingRouteVault.fromPlaintext(plaintext)
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

        fun chatBackgroundFile(context: Context): File = File(context.filesDir, "chat_bg.jpg")
    }
}
