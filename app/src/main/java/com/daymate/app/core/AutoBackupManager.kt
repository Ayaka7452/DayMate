package com.ayaka7452.daymate.core

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ayaka7452.daymate.data.db.DayMateDatabase
import com.ayaka7452.daymate.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * 自动备份管理器：当数据库发生用户修改时（Repository 写方法通知），经防抖后在后台
 * 把内部主库导出到用户用 SAF 选择的备份文件夹（免任何存储权限）。
 *
 * 与「立即备份」不同，本类不关闭数据库容器（关闭会打断正在进行的读写操作），
 * 而是先执行 `PRAGMA wal_checkpoint(TRUNCATE)` 把 WAL 合并回主文件，
 * 再复制 daymate.db。这是尽力而为的实时快照：偶发的极端并发写入可能使本次
 * 副本处于边界不一致，但用户随时可手动「立即备份」得到完全一致副本（close 容器方案）。
 *
 * 触发由 Repository 写方法末尾的 `onChanged()` 回调完成；本管理器只负责
 * 防抖调度与执行，不感知具体是哪次写操作。
 */
class AutoBackupManager(
    private val context: Context,
    private val db: DayMateDatabase,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val debounceMs = 1500L
    private var backupJob: Job? = null

    /** 数据变更通知：非挂起，可直接在 Repository 写方法末尾调用。 */
    fun onDataChanged() {
        backupJob?.cancel()
        backupJob = scope.launch {
            delay(debounceMs)
            runBackup()
        }
    }

    /**
     * 立即（不防抖）执行一次备份。供 Activity onPause / 即将离开应用时调用，
     * 避免待执行的防抖任务因进程被杀死而丢失最近一次写入的备份。
     */
    fun flush() {
        backupJob?.cancel()
        scope.launch { runBackup() }
    }

    private suspend fun runBackup() {
        // 开关关闭或未配置备份文件夹时直接跳过
        if (!settings.autoBackupEnabled.first()) return
        val internalDb = context.getDatabasePath("daymate.db")
        if (!internalDb.exists()) return
        if (StorageConfig.backupUri(context) == null) return

        // 合并 WAL 进主文件，使复制出的 daymate.db 包含全部已提交数据
        runCatching {
            val d: SupportSQLiteDatabase = db.openHelper.writableDatabase
            d.query("PRAGMA wal_checkpoint(TRUNCATE)").use { /* drain cursor */ }
        }

        // 复用导出逻辑（复制 daymate.db 及其 -wal / -shm 附属文件到 SAF 文件夹）
        StorageBackup.exportInternal(context, internalDb)
    }
}
