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
