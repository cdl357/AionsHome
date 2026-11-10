package com.aion.chat.compose.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 工作流存储（照橘瓣 workflow 模块的数据形状，落 AionsHome 自己的 SQLite）：
 * - workflow 表：definition_json 是唯一真源（新增字段不用迁移），其余列是投影加速
 * - workflow_run 表：每次触发的流水（最多留 100 条/工作流，照橘瓣 MAX_RUNS_HISTORY）
 * 投影列（last_run_at 等与 runs_today）每次 fire 由 [recordFire] 维护；upsert 保留投影不覆盖。
 */
object WorkflowStore {

    const val MAX_RUNS_HISTORY = 100
    const val MAX_ERROR_LENGTH = 500

    data class WorkflowRow(
        val id: String,
        val name: String,
        val description: String?,
        val enabled: Boolean,
        val definitionJson: String,
        val createdAtMs: Long,
        val updatedAtMs: Long,
        val lastRunAtMs: Long?,
        val lastRunStatus: String?,
        val lastRunError: String?,
        val runsTodayCount: Int,
        val runsTodayDate: String,
    )

    data class RunRow(
        val rowId: Long,
        val workflowId: String,
        val firedAtMs: Long,
        val status: String,
        val durationMs: Long,
        val errorMessage: String?,
    )

    class Helper(context: Context) : SQLiteOpenHelper(context, "workflow.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS workflow(" +
                        "id TEXT PRIMARY KEY NOT NULL," +
                        "name TEXT NOT NULL," +
                        "description TEXT," +
                        "enabled INTEGER NOT NULL DEFAULT 1," +
                        "definition_json TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "updated_at INTEGER NOT NULL," +
                        "last_run_at INTEGER," +
                        "last_run_status TEXT," +
                        "last_run_error TEXT," +
                        "runs_today_count INTEGER NOT NULL DEFAULT 0," +
                        "runs_today_date TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS workflow_run(" +
                        "row_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "workflow_id TEXT NOT NULL," +
                        "fired_at INTEGER NOT NULL," +
                        "status TEXT NOT NULL," +
                        "duration_ms INTEGER NOT NULL DEFAULT 0," +
                        "error TEXT)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_wf_run ON workflow_run(workflow_id, fired_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }
    }

    @Volatile private var helper: Helper? = null
    internal fun h(context: Context): Helper =
        helper ?: synchronized(this) { helper ?: Helper(context.applicationContext).also { helper = it } }

    // ── 基础 CRUD ──

    fun upsert(context: Context, row: WorkflowRow) {
        val existing = getById(context, row.id)
        val cv = ContentValues().apply {
            put("id", row.id)
            put("name", row.name)
            put("description", row.description)
            put("enabled", if (row.enabled) 1 else 0)
            put("definition_json", row.definitionJson)
            put("created_at", existing?.createdAtMs ?: row.createdAtMs)
            put("updated_at", System.currentTimeMillis())
            put("last_run_at", existing?.lastRunAtMs)
            put("last_run_status", existing?.lastRunStatus)
            put("last_run_error", existing?.lastRunError)
            put("runs_today_count", existing?.runsTodayCount ?: 0)
            put("runs_today_date", existing?.runsTodayDate ?: "")
        }
        h(context).writableDatabase.insertWithOnConflict(
            "workflow", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getById(context: Context, id: String): WorkflowRow? {
        h(context).readableDatabase.rawQuery("SELECT * FROM workflow WHERE id=?", arrayOf(id)).use { c ->
            return if (c.moveToFirst()) rowOf(c) else null
        }
    }

    fun listAll(context: Context, enabledOnly: Boolean = false): List<WorkflowRow> {
        val out = mutableListOf<WorkflowRow>()
        val sql = if (enabledOnly) "SELECT * FROM workflow WHERE enabled=1 ORDER BY created_at DESC"
        else "SELECT * FROM workflow ORDER BY created_at DESC"
        h(context).readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) out.add(rowOf(c))
        }
        return out
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean {
        val cv = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        return h(context).writableDatabase.update("workflow", cv, "id=?", arrayOf(id)) > 0
    }

    fun delete(context: Context, id: String): Boolean {
        h(context).writableDatabase.delete("workflow_run", "workflow_id=?", arrayOf(id))
        return h(context).writableDatabase.delete("workflow", "id=?", arrayOf(id)) > 0
    }

    // ── 触发流水 ──

    fun recordFire(
        context: Context, id: String, firedAtMs: Long,
        status: String, durationMs: Long, errorMessage: String?,
    ) {
        val db = h(context).writableDatabase
        val err = errorMessage?.take(MAX_ERROR_LENGTH)
        db.execSQL("INSERT INTO workflow_run(workflow_id, fired_at, status, duration_ms, error) VALUES(?,?,?,?,?)",
            arrayOf(id, firedAtMs, status, durationMs, err))
        // 每日计数：只有真实触发（SUCCESS/FAILED）才算，SKIP_* 不占当日额度（照橘瓣）
        val counts = status == "SUCCESS" || status == "FAILED"
        val today = java.time.LocalDate.now().toString()
        val current = getById(context, id)
        val newCount = when {
            current == null -> if (counts) 1 else 0
            current.runsTodayDate != today -> if (counts) 1 else 0
            else -> current.runsTodayCount + (if (counts) 1 else 0)
        }
        val cv = ContentValues().apply {
            put("last_run_at", firedAtMs)
            put("last_run_status", status)
            put("last_run_error", err)
            put("runs_today_count", newCount)
            put("runs_today_date", today)
        }
        db.update("workflow", cv, "id=?", arrayOf(id))
        db.execSQL("DELETE FROM workflow_run WHERE workflow_id=? AND row_id NOT IN " +
                "(SELECT row_id FROM workflow_run WHERE workflow_id=? ORDER BY fired_at DESC LIMIT ?)",
            arrayOf(id, id, MAX_RUNS_HISTORY))
    }

    /** 最近一次真实触发（SUCCESS/FAILED）——冷却门的锚点；SKIP 不算（照橘瓣 CooldownGate） */
    fun lastActualFireAtMs(context: Context, id: String): Long? {
        h(context).readableDatabase.rawQuery(
            "SELECT MAX(fired_at) FROM workflow_run WHERE workflow_id=? AND status IN ('SUCCESS','FAILED')",
            arrayOf(id)).use { c ->
            return if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }

    fun lastRuns(context: Context, id: String, limit: Int = 20): List<RunRow> {
        val out = mutableListOf<RunRow>()
        h(context).readableDatabase.rawQuery(
            "SELECT row_id, workflow_id, fired_at, status, duration_ms, error FROM workflow_run " +
                    "WHERE workflow_id=? ORDER BY fired_at DESC LIMIT ?", arrayOf(id, limit.toString())).use { c ->
            while (c.moveToNext()) {
                out.add(RunRow(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getLong(4),
                    if (c.isNull(5)) null else c.getString(5)))
            }
        }
        return out
    }

    private fun rowOf(c: android.database.Cursor) = WorkflowRow(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        description = c.getString(c.getColumnIndexOrThrow("description"))?.takeIf { it.isNotBlank() },
        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
        definitionJson = c.getString(c.getColumnIndexOrThrow("definition_json")),
        createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        lastRunAtMs = if (c.isNull(c.getColumnIndexOrThrow("last_run_at"))) null else c.getLong(c.getColumnIndexOrThrow("last_run_at")),
        lastRunStatus = c.getString(c.getColumnIndexOrThrow("last_run_status")),
        lastRunError = c.getString(c.getColumnIndexOrThrow("last_run_error")),
        runsTodayCount = c.getInt(c.getColumnIndexOrThrow("runs_today_count")),
        runsTodayDate = c.getString(c.getColumnIndexOrThrow("runs_today_date")),
    )
}
