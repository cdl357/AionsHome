package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 工作流的 7 个 LLM 工具（照橘瓣 workflow_* 工具族，AionsHome 版）：
 * create / list / get / update / delete / set_enabled / run。
 * - 定义 JSON 直接由模型产出，WorkflowJson.parse 校验（动作工具必须在当前工具面里真实存在）
 * - create/update/delete/set_enabled/run 都会触发 WorkflowTriggers.sync 重挂接收器
 * - workflow_run 走引擎完整门控（条件/冷却/每日上限照常）
 */
object WorkflowTools {

    @Volatile private var ctxRef: android.content.Context? = null

    /** Wiring 启动时注入（与 MemoryTools.initContext 同款） */
    @JvmStatic
    fun initContext(context: Context) {
        if (ctxRef == null) ctxRef = context.applicationContext
    }

    @JvmStatic
    fun handles(name: String): Boolean = name.startsWith("workflow_")

    /** 当前可用动作工具名 = MCP 注册表 + 记忆工具（与聊天轮一致的工具面） */
    fun knownToolNames(): Set<String> {
        val out = mutableSetOf<String>()
        runCatching { McpClient.cachedDefs().forEach { out.add(it.safeName) } }
        runCatching { out.addAll(MemoryTools.names()) }
        return out
    }

    /** OpenAI function 格式的工具定义（照 gateway 的注入形状） */
    @JvmStatic
    fun defs(): JSONArray {
        val arr = JSONArray()
        fun def(name: String, desc: String, params: JSONObject) {
            arr.put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", name)
                    .put("description", desc)
                    .put("parameters", params)))
        }
        fun objProp(desc: String) = JSONObject()
            .put("type", "object").put("description", desc)
            .put("properties", JSONObject())
        val createDesc = """
            创建一个事件驱动工作流：trigger 触发 + 全部 conditions 通过时，按顺序执行 actions
            （每个 action 调一个当前已注册的工具）。所有触发器都是持续性的，没有一次性模式。
            可用 trigger.type：time_cron（cron 5字段 或 time_of_day "HH:mm" + days_of_week [1..7] 1=周一）、
            manual（只能 workflow_run 或设置页"立即运行"触发）、power_connected、power_disconnected、
            battery_below（threshold_percent 1..100，跨阈值时触发）、battery_above、screen_on、
            screen_off、boot_completed、headphones_plugged、headphones_unplugged、
            wifi_connected / wifi_disconnected（可选 ssid 参数）、
            notification_received（package_name/title_contains/text_contains/title_matches/text_matches 至少一个）、
            app_launched（package_name）。
            可用 condition.type（AND 组合，每个都支持 invert:true 取反）：time_between（start/end HH:mm，可跨午夜）、
            day_of_week_in（days [1..7]）、wifi_ssid_is / wifi_ssid_in、battery_above / battery_below（percent）、
            is_charging / is_not_charging、screen_is_on / screen_is_off、last_chat_ago（minutes，用户超过这么久没发消息）。
            actions 每项 = { tool: 现有工具名, args: {...}, timeout_seconds?: 1..600 }。
            可选：cooldown_seconds（两次真实触发最小间隔）、max_runs_per_day（1..1000）。
            必填：name、trigger、actions。
        """.trimIndent().replace("\n", " ")
        def("workflow_create", createDesc, objProp("definition 对象：name/trigger/actions 必填；description/enabled/conditions/cooldown_seconds/max_runs_per_day 可选"))
        def("workflow_list", "列出全部工作流（id/name/enabled/触发器类型/上次运行状态）。enabled_only=true 只看启用的。",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("enabled_only", JSONObject().put("type", "boolean").put("description", "只返回启用中的（默认 false）"))))
        def("workflow_get", "取一个工作流的完整定义和最近 10 次运行记录。id 必填。",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("id", JSONObject().put("type", "string"))).put("required", JSONArray().put("id")))
        def("workflow_update", "整体替换一个工作流的定义（id 必须已存在）。schema 同 workflow_create。",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("definition", JSONObject().put("type", "object"))).put("required", JSONArray().put("definition")))
        def("workflow_delete", "删除工作流及其全部运行历史。id 必填。",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("id", JSONObject().put("type", "string"))).put("required", JSONArray().put("id")))
        def("workflow_set_enabled", "启用/停用一个工作流（停用不删定义和历史）。",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("id", JSONObject().put("type", "string"))
                .put("enabled", JSONObject().put("type", "boolean")))
                .put("required", JSONArray().put("id").put("enabled")))
        def("workflow_run", "立即触发一个工作流（条件/冷却/每日上限照常生效）。适合测试或手动型工作流。",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("id", JSONObject().put("type", "string"))).put("required", JSONArray().put("id")))
        return arr
    }

    /** 分发入口（与聊天轮工具循环同一调用约定：返回结果文本） */
    @JvmStatic
    fun dispatch(name: String, argsJson: String): String {
        val app = ctxRef ?: throw IllegalStateException("WorkflowTools 未初始化")
        return dispatchInner(app, name, argsJson)
    }

    private fun dispatchInner(app: Context, name: String, argsJson: String): String {
        val args = try { JSONObject(if (argsJson.isBlank()) "{}" else argsJson) } catch (e: Exception) { JSONObject() }
        return when (name) {
            "workflow_create" -> create(app, args.optString("definition"), null)
            "workflow_update" -> {
                val d = args.optString("definition")
                val obj = runCatching { JSONObject(d) }.getOrNull()
                    ?: return err("bad_json", "definition 不是合法 JSON")
                val id = obj.optString("id")
                if (id.isBlank()) return err("missing_id", "definition.id 必填")
                val existing = WorkflowStore.getById(app, id) ?: return err("not_found", "没有 id=$id 的工作流，用 workflow_create")
                create(app, d, existing.id)
            }
            "workflow_list" -> {
                val rows = WorkflowStore.listAll(app, args.optBoolean("enabled_only", false))
                JSONObject().put("count", rows.size).put("items", JSONArray().apply {
                    for (r in rows) {
                        val def = runCatching { JSONObject(r.definitionJson) }.getOrNull()
                        put(JSONObject()
                            .put("id", r.id)
                            .put("name", r.name)
                            .put("enabled", r.enabled)
                            .put("trigger_type", def?.optJSONObject("trigger")?.optString("type") ?: "unknown")
                            .put("last_run_at_ms", r.lastRunAtMs ?: JSONObject.NULL)
                            .put("last_run_status", r.lastRunStatus ?: JSONObject.NULL))
                    }
                }).toString()
            }
            "workflow_get" -> {
                val id = args.optString("id")
                if (id.isBlank()) return err("missing_id", "id 必填")
                val row = WorkflowStore.getById(app, id) ?: return err("not_found", "没有 id=$id 的工作流")
                JSONObject()
                    .put("ok", true)
                    .put("definition", JSONObject(row.definitionJson))
                    .put("enabled", row.enabled)
                    .put("last_run_at_ms", row.lastRunAtMs ?: JSONObject.NULL)
                    .put("last_run_status", row.lastRunStatus ?: JSONObject.NULL)
                    .put("last_run_error", row.lastRunError ?: JSONObject.NULL)
                    .put("runs_today_count", row.runsTodayCount)
                    .put("history", JSONArray().apply {
                        for (r in WorkflowStore.lastRuns(app, id, 10)) put(JSONObject()
                            .put("fired_at_ms", r.firedAtMs)
                            .put("status", r.status)
                            .put("duration_ms", r.durationMs)
                            .put("error", r.errorMessage ?: JSONObject.NULL))
                    }).toString()
            }
            "workflow_delete" -> {
                val id = args.optString("id")
                if (id.isBlank()) return err("missing_id", "id 必填")
                if (WorkflowStore.delete(app, id)) {
                    WorkflowEngine.forget(id)
                    WorkflowTriggers.sync(app)
                    JSONObject().put("ok", true).put("id", id).toString()
                } else err("not_found", "没有 id=$id 的工作流")
            }
            "workflow_set_enabled" -> {
                val id = args.optString("id")
                val enabled = if (args.has("enabled") && args.get("enabled") is Boolean) args.getBoolean("enabled")
                else args.optString("enabled").toBooleanStrictOrNull()
                    ?: return err("invalid_enabled", "enabled 需要 true/false")
                if (!WorkflowStore.setEnabled(app, id, enabled)) return err("not_found", "没有 id=$id 的工作流")
                WorkflowTriggers.sync(app)
                JSONObject().put("ok", true).put("id", id).put("enabled", enabled).toString()
            }
            "workflow_run" -> {
                val id = args.optString("id")
                if (id.isBlank()) return err("missing_id", "id 必填")
                WorkflowStore.getById(app, id) ?: return err("not_found", "没有 id=$id 的工作流")
                val outcome = WorkflowEngine.fire(app, id)
                JSONObject()
                    .put("ok", outcome.status == "SUCCESS")
                    .put("status", outcome.status)
                    .put("error", outcome.error ?: JSONObject.NULL)
                    .put("output_summary", if (outcome.summary.isBlank()) JSONObject.NULL else outcome.summary)
                    .toString()
            }
            else -> err("unknown_tool", name)
        }
    }

    private fun create(app: Context, definitionJson: String, replaceId: String?): String {
        if (definitionJson.isBlank()) return err("missing_definition", "definition 对象必填")
        val parsed = try {
            WorkflowJson.parse(definitionJson, knownToolNames())
        } catch (e: IllegalArgumentException) {
            return err("validation", e.message ?: "定义不合法")
        } catch (e: Exception) {
            return err("bad_json", "definition 不是合法 JSON: ${e.message?.take(120)}")
        }
        val id = if (replaceId != null) {
            // update：必须落在已有 id 上，防改错对象
            parsed.optString("id", "").takeIf { it == replaceId } ?: replaceId
        } else {
            parsed.optString("id").ifBlank { "wf_" + java.util.UUID.randomUUID().toString().substring(0, 8) }
        }
        parsed.put("id", id)
        val row = WorkflowStore.WorkflowRow(
            id = id,
            name = parsed.optString("name"),
            description = parsed.optString("description").takeIf { it.isNotBlank() },
            enabled = parsed.optBoolean("enabled", true),
            definitionJson = parsed.toString(),
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis(),
            lastRunAtMs = null, lastRunStatus = null, lastRunError = null,
            runsTodayCount = 0, runsTodayDate = "",
        )
        return try {
            WorkflowStore.upsert(app, row)
            WorkflowTriggers.sync(app)
            JSONObject().put("ok", true).put("id", id).put("name", row.name).toString()
        } catch (e: Exception) {
            err("persist_failed", e.message ?: "保存失败")
        }
    }

    private fun err(code: String, detail: String): String =
        JSONObject().put("ok", false).put("error", code).put("detail", detail).toString()
}
