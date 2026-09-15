package com.ayaka7452.daymate.core

import android.content.Context
import com.ayaka7452.daymate.data.db.DayMateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据库诊断与维护（设置 → 数据维护）。
 *
 * 定位：给长期使用的库做一次「体检 + 整理」，**只做无损维护，绝不删除用户数据**——
 * 不碰事件/文件夹/保险箱的任何一行内容，回收站里的已删除条目也保持原样（仅在报告里列出数量）。
 *
 * 具体动作：
 *  - 诊断：文件占用、空闲页与碎片率、`PRAGMA integrity_check`、各表行数（含回收站条目）、
 *    悬空文件夹引用、异常时间戳、与当前代码不再匹配的僵尸列；
 *  - 修复：悬空引用置空（指向已不存在的文件夹本身已是无效状态）、异常时间戳补正（仅 0/负值这类
 *    不可能值）、`VACUUM` 内部重建文件以回收碎片；
 *  - 收尾：合并 WAL 后调用 [AutoBackupManager.flush]，把修好的库同步到本地文件夹与云端
 *    （自动备份带空数据护栏，而本流程不改行数，因此不会被误拦）。
 *
 * 关于 VACUUM：它是 SQLite 官方的维护操作，效果等价于「重新生成一份紧凑的数据库文件」，
 * 数据一行不动——这也是本工具不采用「导出重建」方案的原因：重建需要自行搬运数据，风险高得多，
 * 而收益（回收空间）VACUUM 已经覆盖。
 */
class DbRepair(
    private val context: Context,
    private val db: DayMateDatabase,
    private val autoBackup: AutoBackupManager
) {

    /** 单张表的体检数据。 */
    data class TableStat(
        val table: String,
        /** 面向用户的名称。 */
        val label: String,
        val rows: Long,
        /** 回收站中的条目数（仅 events / folders 支持软删除）。 */
        val inRecycleBin: Long,
        /** 指向已不存在文件夹的引用数。 */
        val danglingRefs: Long
    )

    /** 一次体检的完整结果。 */
    data class Report(
        val dbBytes: Long,
        val walBytes: Long,
        val pageSize: Long,
        val pageCount: Long,
        val freePages: Long,
        val integrityOk: Boolean,
        val integrityDetail: String?,
        val tables: List<TableStat>,
        /** 与当前代码不再匹配的列（僵尸列）。 */
        val zombieColumns: List<String>,
        /** 创建/更新时间为 0 或负数的行数。 */
        val badTimestampRows: Long
    ) {
        /** 数据库总占用（含 WAL）。 */
        val totalBytes: Long get() = dbBytes + walBytes

        /** 可通过 VACUUM 回收的字节数（空闲页估算）。 */
        val reclaimableBytes: Long get() = freePages * pageSize

        /** 空闲页占比，0f~1f。 */
        val freeRatio: Float get() = if (pageCount > 0) freePages.toFloat() / pageCount else 0f

        /** 用户数据总行数（不含回收站）。 */
        val liveRows: Long
            get() = tables.sumOf { it.rows - it.inRecycleBin }
    }

    /** 修复结果。 */
    data class RepairResult(
        val ok: Boolean,
        val failureReason: String?,
        val before: Report,
        val after: Report?,
        val fixedDanglingRefs: Int,
        val fixedTimestamps: Int
    )

    /** 各表当前的列清单（与 Entity 定义一致），用于识别僵尸列。 */
    private val expectedColumns = mapOf(
        "events" to setOf(
            "id", "title", "targetDateEpochDay", "note", "color", "folderId", "refDays",
            "displayUnit", "repeatRule", "linkedFestival", "specialType",
            "sortIndex", "isPinned", "isDeleted", "deletedAt", "createdAt", "updatedAt"
        ),
        "folders" to setOf(
            "id", "name", "icon", "color", "sortIndex", "isPinned",
            "isDeleted", "deletedAt", "createdAt"
        ),
        "vault_events" to setOf(
            "id", "title", "targetDateEpochDay", "note", "color", "folderId", "refDays",
            "displayUnit", "repeatRule", "linkedFestival", "sortIndex", "isPinned",
            "createdAt", "updatedAt"
        ),
        "vault_folders" to setOf(
            "id", "name", "icon", "color", "sortIndex", "isPinned", "createdAt"
        ),
        "cycle_logs" to setOf(
            "id", "startDateEpochDay", "periodDays", "note", "createdAt", "updatedAt"
        )
    )

    /** 只体检、不修改任何内容。 */
    suspend fun diagnose(): Report = withContext(Dispatchers.IO) { buildReport() }

    /**
     * 体检 → 无损修复 → 回收碎片 → 同步各备份点。
     * 修复动作全部是「让无效状态变有效」：悬空引用置空、不可能的时间戳补正，
     * 不删除任何一条记录，也不改变回收站内容。
     */
    suspend fun repair(): RepairResult = withContext(Dispatchers.IO) {
        val before = buildReport()
        val d = db.openHelper.writableDatabase

        var dangling = 0
        var timestamps = 0
        try {
            // 1) 悬空文件夹引用置空（指向已删除/不存在文件夹的引用本身无意义）
            dangling += d.compileStatement(
                "UPDATE events SET folderId = NULL WHERE folderId IS NOT NULL " +
                    "AND folderId NOT IN (SELECT id FROM folders)"
            ).executeUpdateDelete().toInt()
            dangling += d.compileStatement(
                "UPDATE vault_events SET folderId = NULL WHERE folderId IS NOT NULL " +
                    "AND folderId NOT IN (SELECT id FROM vault_folders)"
            ).executeUpdateDelete().toInt()

            // 2) 异常时间戳补正：仅处理 0 / 负数这类不可能值（1970 年），不动正常时间
            val now = System.currentTimeMillis()
            for ((table, cols) in listOf(
                "events" to listOf("createdAt", "updatedAt"),
                "folders" to listOf("createdAt"),
                "vault_events" to listOf("createdAt", "updatedAt"),
                "vault_folders" to listOf("createdAt"),
                "cycle_logs" to listOf("createdAt", "updatedAt")
            )) {
                for (col in cols) {
                    timestamps += d.compileStatement(
                        "UPDATE $table SET $col = $now WHERE $col IS NULL OR $col <= 0"
                    ).executeUpdateDelete().toInt()
                }
            }

            // 3) 先把 WAL 合并回主文件，再 VACUUM 内部重建（回收空闲页与碎片）
            checkpoint(d)
            d.execSQL("VACUUM")
            checkpoint(d)
        } catch (t: Throwable) {
            return@withContext RepairResult(
                ok = false,
                failureReason = t.message ?: t::class.java.simpleName,
                before = before,
                after = null,
                fixedDanglingRefs = dangling,
                fixedTimestamps = timestamps
            )
        }

        val after = buildReport()

        // 4) 同步各备份点（本地文件夹 + 云端，按「备份位置」三态分发）
        runCatching { autoBackup.flush() }

        RepairResult(
            ok = true,
            failureReason = null,
            before = before,
            after = after,
            fixedDanglingRefs = dangling,
            fixedTimestamps = timestamps
        )
    }

    // ===== 内部 =====

    private fun buildReport(): Report {
        val d = db.openHelper.writableDatabase
        val main = context.getDatabasePath("daymate.db")
        val wal = File(main.path + "-wal")

        val pageSize = longPragma(d, "PRAGMA page_size")
        val pageCount = longPragma(d, "PRAGMA page_count")
        val freePages = longPragma(d, "PRAGMA freelist_count")

        var integrityOk = true
        var integrityDetail: String? = null
        runCatching {
            d.query("PRAGMA integrity_check").use { c ->
                if (c.moveToFirst()) {
                    val v = c.getString(0)
                    if (v != "ok") {
                        integrityOk = false
                        integrityDetail = v
                    }
                } else {
                    integrityOk = false
                    integrityDetail = "无法读取检查结果"
                }
            }
        }.onFailure {
            integrityOk = false
            integrityDetail = it.message ?: "完整性检查失败"
        }

        val tables = listOf(
            TableStat(
                "events", "倒数日",
                rows = count(d, "events"),
                inRecycleBin = count(d, "events WHERE isDeleted = 1"),
                danglingRefs = count(
                    d,
                    "events WHERE folderId IS NOT NULL AND folderId NOT IN (SELECT id FROM folders)"
                )
            ),
            TableStat(
                "folders", "文件夹",
                rows = count(d, "folders"),
                inRecycleBin = count(d, "folders WHERE isDeleted = 1"),
                danglingRefs = 0
            ),
            TableStat(
                "vault_events", "保险箱条目",
                rows = count(d, "vault_events"),
                inRecycleBin = 0,
                danglingRefs = count(
                    d,
                    "vault_events WHERE folderId IS NOT NULL " +
                        "AND folderId NOT IN (SELECT id FROM vault_folders)"
                )
            ),
            TableStat("vault_folders", "保险箱文件夹", count(d, "vault_folders"), 0, 0),
            TableStat("cycle_logs", "周期记录", count(d, "cycle_logs"), 0, 0)
        )

        // 与代码不再匹配的列
        val zombies = mutableListOf<String>()
        for ((table, expected) in expectedColumns) {
            val actual = columnsOf(d, table)
            if (actual.isEmpty()) continue
            for (col in actual - expected) zombies.add("$table.$col")
        }

        var badTs = 0L
        for ((table, cols) in listOf(
            "events" to listOf("createdAt", "updatedAt"),
            "folders" to listOf("createdAt"),
            "vault_events" to listOf("createdAt", "updatedAt"),
            "vault_folders" to listOf("createdAt"),
            "cycle_logs" to listOf("createdAt", "updatedAt")
        )) {
            for (col in cols) badTs += count(d, "$table WHERE $col IS NULL OR $col <= 0")
        }

        return Report(
            dbBytes = if (main.exists()) main.length() else 0L,
            walBytes = if (wal.exists()) wal.length() else 0L,
            pageSize = pageSize,
            pageCount = pageCount,
            freePages = freePages,
            integrityOk = integrityOk,
            integrityDetail = integrityDetail,
            tables = tables,
            zombieColumns = zombies,
            badTimestampRows = badTs
        )
    }

    private fun checkpoint(d: androidx.sqlite.db.SupportSQLiteDatabase) {
        runCatching { d.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
    }

    private fun columnsOf(d: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Set<String> {
        val cols = mutableSetOf<String>()
        runCatching {
            d.query("PRAGMA table_info($table)").use { c ->
                while (c.moveToNext()) cols.add(c.getString(1))
            }
        }
        return cols
    }

    private fun longPragma(d: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        runCatching {
            d.query(sql).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }.getOrDefault(0L)

    /** 计数：任意异常都返回 -1（与备份护栏同策略——读不出来就不谎报 0）。 */
    private fun count(d: androidx.sqlite.db.SupportSQLiteDatabase, from: String): Long =
        runCatching {
            d.compileStatement("SELECT COUNT(*) FROM $from").simpleQueryForLong()
        }.getOrDefault(-1L)
}
