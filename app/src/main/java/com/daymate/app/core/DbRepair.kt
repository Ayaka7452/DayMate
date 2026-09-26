package com.ayaka7452.daymate.core

import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import com.ayaka7452.daymate.core.log.AppLogger
import android.content.Context
import com.ayaka7452.daymate.data.db.DayMateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据库诊断与维护（设置 → 数据维护）。
 *
 * 使用流程刻意分成两步——**先扫描、再由用户决定是否修复**：
 *  1. [diagnose] 只读体检，产出 [Report]；报告自带 [Report.hasFixableIssues] 与 [Report.issues]，
 *     供界面在「发现问题」时弹框列出具体问题；
 *  2. [repair] 执行无损维护：先把 WAL 合并回主文件、**在本地备份文件夹留一份 `daymate.db.bak` 快照**，
 *     然后修复无效引用与异常时间戳，最后 `VACUUM` 回收碎片并同步各备份点。
 *
 * 扫描分三层，全部只读：
 *  - 物理层：文件占用、空闲页与碎片率、`PRAGMA integrity_check`；
 *  - 结构层：与代码不再匹配的僵尸列、悬空文件夹引用、异常时间戳；
 *  - 数据层：逐列填充率（找出**从未被填过**的字段）+ 冗余数据检查
 *    （重复条目、空标题、重名文件夹、越界取值、长期滞留回收站等）。
 *
 * **绝不删除用户数据**：不碰任何一行事件/文件夹/保险箱内容，回收站条目保持原样，
 * 冗余数据只报告不清理。
 */
class DbRepair(
    private val context: Context,
    private val db: DayMateDatabase,
    private val autoBackup: AutoBackupManager
) {

    /** 单张表的体检数据。 */
    data class TableStat(
        val table: String,
        /** 面向用户的名称（资源 id；静态 val 里不能取字符串，否则语言会被冻结）。 */
        val labelRes: Int,
        val rows: Long,
        /** 回收站中的条目数（仅 events / folders 支持软删除）。 */
        val inRecycleBin: Long,
        /** 指向已不存在文件夹的引用数。 */
        val danglingRefs: Long
    )

    /** 某个字段的填充情况——用于回答「哪些字段实际没被用起来」。 */
    data class FieldUsage(
        val table: String,
        val tableRes: Int,
        val column: String,
        val fieldRes: Int,
        /** 该列非空的行数（0 表示从未填过）。 */
        val filled: Long,
        /** 该表总行数。 */
        val total: Long
    )

    /** 一项冗余数据检查的结果。 */
    data class Redundancy(
        val labelRes: Int,
        val count: Long
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
        /** 从未被填过任何值的字段。 */
        val unusedFields: List<FieldUsage>,
        /** 发现的冗余数据（只报告，不自动清理）。 */
        val redundancies: List<Redundancy>,
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
            get() = tables.sumOf { if (it.rows < 0) 0L else it.rows - it.inRecycleBin }

        /** 悬空文件夹引用总数（读不出来时按 0 计）。 */
        val danglingRefs: Long
            get() = tables.sumOf { if (it.danglingRefs < 0) 0L else it.danglingRefs }

        /** 是否存在「可通过修复动作处理」的问题。 */
        val hasFixableIssues: Boolean
            get() = !integrityOk || zombieColumns.isNotEmpty() || danglingRefs > 0 ||
                badTimestampRows > 0 || reclaimableBytes > RECLAIM_THRESHOLD_BYTES

        /** 可修复问题的中文清单（供弹框逐条展示）。 */
        val issues: List<String>
            get() = buildList {
                if (!integrityOk) {
                    add(
                        Tr.s(
                            R.string.db_integrity_bad,
                            integrityDetail ?: Tr.s(R.string.db_integrity_unknown)
                        )
                    )
                }
                if (zombieColumns.isNotEmpty()) {
                    add(
                        Tr.s(
                            R.string.db_zombie_columns,
                            zombieColumns.joinToString(Tr.s(R.string.festival_list_sep))
                        )
                    )
                }
                if (danglingRefs > 0) add(Tr.s(R.string.db_dangling_refs, danglingRefs))
                if (badTimestampRows > 0) add(Tr.s(R.string.db_bad_timestamps, badTimestampRows))
                if (reclaimableBytes > RECLAIM_THRESHOLD_BYTES) {
                    add(Tr.s(R.string.db_fragmented, formatBytes(reclaimableBytes)))
                }
            }

        /** 仅作提示、不会自动处理的情况。 */
        val notices: List<String>
            get() = buildList {
                redundancies.forEach { add(Tr.s(R.string.db_redundancy_item, Tr.s(it.labelRes), it.count)) }
                unusedFields.forEach {
                    add(Tr.s(R.string.db_unused_field_item, Tr.s(it.tableRes), Tr.s(it.fieldRes)))
                }
            }

        /**
         * 是否值得打断用户（弹框列明细）。口径：**可修复问题**，或检出了**冗余/异常数据**。
         *
         * 「从未填过值的字段」只作参考信息，单独不触发弹框 —— 它几乎在任何库上都能命中
         * （总有某个可选字段没被填过），拿它当门槛等于没有过滤。
         */
        val hasProblems: Boolean get() = hasFixableIssues || redundancies.isNotEmpty()
    }

    /** 修复结果。 */
    data class RepairResult(
        val ok: Boolean,
        val failureReason: String?,
        val before: Report,
        val after: Report?,
        val fixedDanglingRefs: Int,
        val fixedTimestamps: Int,
        /** 是否成功在本地备份文件夹留下了修复前快照。 */
        val snapshotCreated: Boolean
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
        ),
        "cycle_notes" to setOf(
            "id", "dateEpochDay", "category", "presetKey", "label", "note", "createdAt"
        )
    )

    /** 需要做填充率统计的可空字段（NOT NULL 列必然有值，没有统计意义）。 */
    private data class FieldSpec(
        val table: String,
        val tableRes: Int,
        val column: String,
        val fieldRes: Int
    )

    private val trackedFields = listOf(
        FieldSpec("events", R.string.db_t_events, "note", R.string.db_f_note),
        FieldSpec("events", R.string.db_t_events, "refDays", R.string.db_f_refdays),
        FieldSpec("events", R.string.db_t_events, "displayUnit", R.string.db_f_displayunit),
        FieldSpec("events", R.string.db_t_events, "repeatRule", R.string.db_f_repeatrule),
        FieldSpec("events", R.string.db_t_events, "linkedFestival", R.string.db_f_linkedfestival),
        FieldSpec("events", R.string.db_t_events, "specialType", R.string.db_f_specialtype),
        FieldSpec("events", R.string.db_t_events, "color", R.string.db_f_color),
        FieldSpec("events", R.string.db_t_events, "folderId", R.string.db_f_folderid),
        FieldSpec("folders", R.string.db_t_folders, "icon", R.string.db_f_icon),
        FieldSpec("folders", R.string.db_t_folders, "color", R.string.db_f_color),
        FieldSpec("vault_events", R.string.db_t_vault_events, "note", R.string.db_f_note),
        FieldSpec("vault_events", R.string.db_t_vault_events, "refDays", R.string.db_f_refdays),
        FieldSpec("vault_events", R.string.db_t_vault_events, "displayUnit", R.string.db_f_displayunit),
        FieldSpec("vault_events", R.string.db_t_vault_events, "repeatRule", R.string.db_f_repeatrule),
        FieldSpec("vault_events", R.string.db_t_vault_events, "linkedFestival", R.string.db_f_linkedfestival),
        FieldSpec("vault_folders", R.string.db_t_vault_folders, "icon", R.string.db_f_icon),
        FieldSpec("vault_folders", R.string.db_t_vault_folders, "color", R.string.db_f_color),
        FieldSpec("cycle_logs", R.string.db_t_cycle_logs, "note", R.string.db_f_cycle_note_flag),
        FieldSpec("cycle_notes", R.string.db_t_cycle_notes, "presetKey", R.string.db_f_presetkey),
        FieldSpec("cycle_notes", R.string.db_t_cycle_notes, "note", R.string.db_f_note_extra)
    )

    /** 只体检、不修改任何内容。 */
    suspend fun diagnose(): Report = withContext(Dispatchers.IO) {
        AppLogger.log("Maintain", "数据库体检开始")
        val report = buildReport()
        AppLogger.log("Maintain", "数据库体检完成 可修复=${report.hasFixableIssues}")
        report
    }

    /**
     * 执行无损修复：留快照 → 修复无效引用与异常时间戳 → VACUUM 回收 → 同步各备份点。
     *
     * @param createSnapshot 是否在修复前把主库复制一份 `daymate.db.bak` 到本地备份文件夹
     *   （仅在已配置备份文件夹时生效；写入失败不会中断修复，但结果里
     *   [RepairResult.snapshotCreated] 为 false）。
     */
    suspend fun repair(createSnapshot: Boolean = true): RepairResult = withContext(Dispatchers.IO) {
        AppLogger.log("Maintain", "数据库修复开始 snapshot=$createSnapshot")
        val before = buildReport()
        val d = db.openHelper.writableDatabase
        val mainDb = context.getDatabasePath("daymate.db")

        // 0) 修复前留底：写入本地备份文件夹的 daymate.db.bak（未配置备份文件夹时跳过）
        var snapshotCreated = false
        if (createSnapshot) {
            snapshotCreated = runCatching {
                checkpoint(d)
                val target = StorageConfig.backupUri(context) ?: return@runCatching false
                StorageBackup.exportSnapshot(context, mainDb, target)
            }.getOrDefault(false)
        }

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
            for ((table, cols) in timestampColumns()) {
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
                fixedTimestamps = timestamps,
                snapshotCreated = snapshotCreated
            )
        }

        val after = buildReport()

        // 4) 同步各备份点（本地文件夹 + 云端，按「备份位置」三态分发）
        runCatching { autoBackup.flush() }

        AppLogger.log(
            "Maintain",
            "数据库修复完成 ok=true snapshot=$snapshotCreated 悬空引用=$dangling 时间戳=$timestamps"
        )
        RepairResult(
            ok = true,
            failureReason = null,
            before = before,
            after = after,
            fixedDanglingRefs = dangling,
            fixedTimestamps = timestamps,
            snapshotCreated = snapshotCreated
        )
    }

    // ===== 报告构建 =====

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
                    integrityDetail = Tr.s(R.string.db_integrity_unreadable)
                }
            }
        }.onFailure {
            integrityOk = false
            integrityDetail = it.message ?: Tr.s(R.string.db_integrity_failed)
        }

        val tables = listOf(
            TableStat(
                "events", R.string.db_t_events,
                rows = count(d, "events"),
                inRecycleBin = count(d, "events WHERE isDeleted = 1"),
                danglingRefs = count(
                    d,
                    "events WHERE folderId IS NOT NULL AND folderId NOT IN (SELECT id FROM folders)"
                )
            ),
            TableStat(
                "folders", R.string.db_t_folders,
                rows = count(d, "folders"),
                inRecycleBin = count(d, "folders WHERE isDeleted = 1"),
                danglingRefs = 0
            ),
            TableStat(
                "vault_events", R.string.db_t_vault_events,
                rows = count(d, "vault_events"),
                inRecycleBin = 0,
                danglingRefs = count(
                    d,
                    "vault_events WHERE folderId IS NOT NULL " +
                        "AND folderId NOT IN (SELECT id FROM vault_folders)"
                )
            ),
            TableStat("vault_folders", R.string.db_t_vault_folders, count(d, "vault_folders"), 0, 0),
            TableStat("cycle_logs", R.string.db_t_cycle_logs, count(d, "cycle_logs"), 0, 0),
            TableStat("cycle_notes", R.string.db_t_cycle_notes, count(d, "cycle_notes"), 0, 0)
        )

        var badTs = 0L
        for ((table, cols) in timestampColumns()) {
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
            zombieColumns = scanZombieColumns(d),
            unusedFields = scanUnusedFields(d),
            redundancies = scanRedundancies(d),
            badTimestampRows = badTs
        )
    }

    /** 与当前代码不再匹配的列。 */
    private fun scanZombieColumns(d: androidx.sqlite.db.SupportSQLiteDatabase): List<String> {
        val zombies = mutableListOf<String>()
        for ((table, expected) in expectedColumns) {
            val actual = columnsOf(d, table)
            if (actual.isEmpty()) continue
            for (col in actual - expected) zombies.add("$table.$col")
        }
        return zombies
    }

    /** 可空字段里从未被填过值的那些（回答「哪些字段实际没被用起来」）。 */
    private fun scanUnusedFields(d: androidx.sqlite.db.SupportSQLiteDatabase): List<FieldUsage> {
        val out = mutableListOf<FieldUsage>()
        val totalCache = mutableMapOf<String, Long>()
        for (spec in trackedFields) {
            if (spec.column !in columnsOf(d, spec.table)) continue
            val total = totalCache.getOrPut(spec.table) { count(d, spec.table) }
            if (total <= 0) continue
            val filled = runCatching {
                d.compileStatement("SELECT COUNT(${spec.column}) FROM ${spec.table}")
                    .simpleQueryForLong()
            }.getOrDefault(-1L)
            if (filled == 0L) {
                out.add(
                    FieldUsage(
                        table = spec.table,
                        tableRes = spec.tableRes,
                        column = spec.column,
                        fieldRes = spec.fieldRes,
                        filled = filled,
                        total = total
                    )
                )
            }
        }
        return out
    }

    /** 冗余数据检查——**只统计不修改**，是否清理由用户自行决定。 */
    private fun scanRedundancies(d: androidx.sqlite.db.SupportSQLiteDatabase): List<Redundancy> {
        val out = mutableListOf<Redundancy>()
        val staleCutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000

        fun add(labelRes: Int, sql: String) {
            val n = runCatching { d.compileStatement(sql).simpleQueryForLong() }.getOrDefault(0L)
            if (n > 0) out.add(Redundancy(labelRes, n))
        }

        add(
            R.string.db_red_dup_events,
            "SELECT COUNT(*) FROM (SELECT 1 FROM events WHERE isDeleted = 0 " +
                "GROUP BY title, targetDateEpochDay, IFNULL(folderId, -1) HAVING COUNT(*) > 1)"
        )
        add(
            R.string.db_red_empty_title,
            "SELECT COUNT(*) FROM events WHERE isDeleted = 0 AND TRIM(IFNULL(title, '')) = ''"
        )
        add(
            R.string.db_red_dup_folders,
            "SELECT COUNT(*) FROM (SELECT 1 FROM folders WHERE isDeleted = 0 " +
                "GROUP BY name HAVING COUNT(*) > 1)"
        )
        add(
            R.string.db_red_ref_days,
            "SELECT COUNT(*) FROM events WHERE refDays IS NOT NULL " +
                "AND (refDays <= 0 OR refDays > 36500)"
        )
        add(
            R.string.db_red_date_range,
            "SELECT COUNT(*) FROM events WHERE targetDateEpochDay < -200000 " +
                "OR targetDateEpochDay > 100000"
        )
        add(
            R.string.db_red_period_days,
            "SELECT COUNT(*) FROM cycle_logs WHERE periodDays < 1 OR periodDays > 15"
        )
        add(
            R.string.db_red_dup_start,
            "SELECT COUNT(*) FROM (SELECT 1 FROM cycle_logs " +
                "GROUP BY startDateEpochDay HAVING COUNT(*) > 1)"
        )
        // 日常记录只报告不清理：同一天重复勾同一个小项多半是误操作，但删除与否该由用户决定
        add(
            R.string.db_red_empty_note,
            "SELECT COUNT(*) FROM cycle_notes WHERE TRIM(IFNULL(label, '')) = ''"
        )
        add(
            R.string.db_red_note_range,
            "SELECT COUNT(*) FROM cycle_notes WHERE dateEpochDay < -200000 OR dateEpochDay > 100000"
        )
        add(
            R.string.db_red_bin_stale,
            "SELECT (SELECT COUNT(*) FROM events WHERE isDeleted = 1 AND deletedAt > 0 " +
                "AND deletedAt < $staleCutoff) + (SELECT COUNT(*) FROM folders " +
                "WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < $staleCutoff)"
        )
        return out
    }

    // ===== 内部工具 =====

    private fun timestampColumns(): List<Pair<String, List<String>>> = listOf(
        "events" to listOf("createdAt", "updatedAt"),
        "folders" to listOf("createdAt"),
        "vault_events" to listOf("createdAt", "updatedAt"),
        "vault_folders" to listOf("createdAt"),
        "cycle_logs" to listOf("createdAt", "updatedAt"),
        "cycle_notes" to listOf("createdAt")
    )

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

    /** 计数：读不出来返回 -1（与备份护栏同策略——绝不谎报 0）。 */
    private fun count(d: androidx.sqlite.db.SupportSQLiteDatabase, from: String): Long =
        runCatching {
            d.compileStatement("SELECT COUNT(*) FROM $from").simpleQueryForLong()
        }.getOrDefault(-1L)

    companion object {
        /** 空闲页超过该体积才提示「碎片较多」，避免正常波动也报问题。 */
        private const val RECLAIM_THRESHOLD_BYTES = 64L * 1024
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
