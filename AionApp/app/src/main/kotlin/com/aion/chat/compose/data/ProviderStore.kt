package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * 模型提供商（橘瓣式，Yuri 定稿）：
 * - 提供商列表 → 新增 → 名称 / API Key / API Base Url → 通了拉模型列表 → 勾选添加
 * - 多提供商共存，其中一个「设为聊天使用」（active）
 * - 落盘 chat_providers prefs；同时同步 routes.json 供引擎使用：
 *   启用的提供商各写一条路由，active 的额外镜像一份 route_id="main"
 *   （语音/戳一戳/记忆等旧调用方解析 "main" 永不断）
 */
object ProviderStore {

    data class Model(val key: String, val model: String)

    data class Provider(
        val id: String,
        val name: String,
        val baseUrl: String,
        val apiKey: String,
        val enabled: Boolean,
        val models: List<Model>
    )

    private val _rev = mutableStateOf(0)

    /** 读一下即可订阅提供商/激活模型变化。 */
    val rev: Int get() = _rev.value

    private fun prefs(context: Context) =
        context.getSharedPreferences("chat_providers", Context.MODE_PRIVATE)

    private fun bump() { _rev.value += 1 }

    // ── 读写 ──

    fun list(context: Context): List<Provider> = runCatching {
        val root = JSONObject(prefs(context).getString("data", "{}") ?: "{}")
        val arr = root.optJSONArray("providers") ?: return emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val models = mutableListOf<Model>()
            val mArr = o.optJSONArray("models")
            if (mArr != null) {
                for (j in 0 until mArr.length()) {
                    val m = mArr.getJSONObject(j)
                    val mk = m.optString("key", "")
                    val mm = m.optString("model", "")
                    if (mk.isNotBlank() && mm.isNotBlank()) models.add(Model(mk, mm))
                }
            }
            Provider(
                o.optString("id", ""), o.optString("name", ""),
                o.optString("base_url", ""), o.optString("api_key", ""),
                o.optBoolean("enabled", true), models
            )
        }.filter { it.id.isNotBlank() }
    }.getOrDefault(emptyList())

    fun activeId(context: Context): String = prefs(context).getString("active_id", "") ?: ""
    fun activeModelKey(context: Context): String = prefs(context).getString("active_model", "") ?: ""

    /** 当前聊天使用的（提供商, 模型）；没配/被停用时回落第一个启用的第一个模型。 */
    fun active(context: Context): Pair<Provider, Model>? {
        val all = list(context).filter { it.enabled && it.models.isNotEmpty() }
        if (all.isEmpty()) return null
        val aid = activeId(context)
        val amk = activeModelKey(context)
        all.firstOrNull { it.id == aid }?.let { p ->
            p.models.firstOrNull { it.key == amk }?.let { return p to it }
            return p to p.models.first()
        }
        return all.first().let { it to it.models.first() }
    }

    /** 全量保存（providers + active）并同步 routes.json。 */
    fun saveAll(
        context: Context,
        providers: List<Provider>,
        activeId: String,
        activeModelKey: String
    ) {
        val root = JSONObject()
            .put("providers", JSONArray().apply {
                providers.forEach { p ->
                    put(JSONObject()
                        .put("id", p.id).put("name", p.name)
                        .put("base_url", p.baseUrl).put("api_key", p.apiKey)
                        .put("enabled", p.enabled)
                        .put("models", JSONArray().apply {
                            p.models.forEach { m -> put(JSONObject().put("key", m.key).put("model", m.model)) }
                        }))
                }
            })
            .put("active_id", activeId)
            .put("active_model", activeModelKey)
        prefs(context).edit().putString("data", root.toString()).apply()
        syncRoutes(context, providers, activeId, activeModelKey)
        bump()
    }

    /** 删除提供商（若是 active，回落到下一个启用的）。 */
    fun delete(context: Context, id: String) {
        val rest = list(context).filter { it.id != id }
        val aid = if (activeId(context) == id) rest.firstOrNull { it.enabled }?.id ?: "" else activeId(context)
        val amk = if (aid.isNotEmpty()) rest.firstOrNull { it.id == aid }?.models?.firstOrNull()?.key ?: "" else ""
        saveAll(context, rest, aid, amk)
    }

    /** 设为聊天使用。 */
    fun setActive(context: Context, id: String, modelKey: String) {
        saveAll(context, list(context), id, modelKey)
    }

    /**
     * routes.json 同步：启用的提供商各一条路由；active 额外镜像 route_id="main"
     * （active 模型排第一——旧调用方 wiring.mainModelKey() 解析 "main" 永不断）。
     */
    private fun syncRoutes(
        context: Context,
        providers: List<Provider>,
        activeId: String,
        activeModelKey: String
    ) {
        val chat = JSONArray()
        providers.filter { it.enabled }.forEach { p ->
            val ordered = p.models.sortedByDescending { it.key == activeModelKey && p.id == activeId }
            val models = JSONArray().apply {
                ordered.forEach { m ->
                    put(JSONObject().put("key", m.key).put("model", m.model).put("vision", true))
                }
            }
            val route = JSONObject()
                .put("route_id", p.id).put("label", p.name)
                .put("provider", "openai_compatible")
                .put("base_url", p.baseUrl).put("api_key", p.apiKey)
                .put("models", models)
            chat.put(route)
            if (p.id == activeId) {
                chat.put(JSONObject(route.toString()).put("route_id", "main"))
            }
        }
        HomecomingRouteConfig.save(context, JSONObject().put("chat", chat))
    }

    // ── 模型列表拉取（GET {base}/models，OpenAI 兼容） ──

    fun fetchModelIds(baseUrl: String, apiKey: String): Pair<List<String>, String> {
        var base = baseUrl.trim().trimEnd('/')
        if (!base.endsWith("/models") && !base.contains("?")) {
            if (!base.endsWith("/v1")) base = "$base/v1"
        }
        val url = if (base.endsWith("/models")) base else "$base/models"
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            if (conn.responseCode !in 200..299) {
                return emptyList<String>() to "HTTP ${conn.responseCode}"
            }
            val arr = JSONObject(conn.inputStream.bufferedReader().readText())
                .optJSONArray("data") ?: return emptyList<String>() to "响应没有 data 字段"
            val ids = (0 until arr.length()).mapNotNull { i ->
                arr.getJSONObject(i).optString("id", "").takeIf { it.isNotBlank() }
            }
            ids to "通了 · ${ids.size} 个模型"
        } catch (e: Exception) {
            emptyList<String>() to "${e.javaClass.simpleName}: ${e.message?.take(90)}"
        }
    }
}
