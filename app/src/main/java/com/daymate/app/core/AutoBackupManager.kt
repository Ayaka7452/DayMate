package com.ayaka7452.daymate.core

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ayaka7452.daymate.core.cloud.CloudBackup
import com.ayaka7452.daymate.core.cloud.WebDavConfig
import com.ayaka7452.daymate.core.cloud.WebDavStore
import com.ayaka7452.daymate.data.db.DayMateDatabase
import com.ayaka7452.daymate.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 自动备份管理器：当数据库发生用户修改时（Repository 写方法通知），经防抖后在后台把内部主库
 * 导出到「备份位置」（设置里的三态）：
 *  - `local`：仅 SAF 备份文件夹；
 *  - `both`：SAF 文件夹与 WebDAV 云端各留一份；
 *  - `cloud`：仅 WebDAV 云端。
 *
 * 与「立即备份」不同，本类不关闭数据库容器（关闭会打断正在进行的读写操作），
 * 而是先执行 `PRAGMA wal_checkpoint(TRUNCATE)` 把 WAL 合并回主文件，再复制/上传 daymate.db。
 * 这是尽力而为的实时快照：偶发的极端并发写入可能使本次副本处于边界不一致，
 * 但用户随时可手动「立即备份」得到完全一致副本。
 *
 * 触发由 Repository 写方法末尾的 `onChanged()` 回调完成；本管理器只负责防抖调度与执行。
 * 所有执行路径都包在 try/catch 内——备份失败绝不能以未捕获异常的形式把应用打崩
 * （scope 用的是 SupervisorJob，未捕获异常会直达默认处理器）。
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
        runCatching { doBackup() }
    }

    private suspend fun doBackup() {
        // 开关关闭时直接跳过
        if (!settings.autoBackupEnabled.first()) return
        val internalDb = context.getDatabasePath("daymate.db")
        if (!internalDb.exists()) return

        val target = settings.backupTarget.first()
        val localUri: Uri? =
            if (target == SettingsRepository.BACKUP_TARGET_CLOUD) null
            else StorageConfig.backupUri(context)
        val cloudCfg: WebDavConfig? =
            if (target == SettingsRepository.BACKUP_TARGET_LOCAL) null
            else runCatching { WebDavStore.config(context) }.getOrNull()

        // 两个目标都没配置 → 无事可做
        if (localUri == null && cloudCfg == null) return

        // 合并 WAL 进主文件，使复制/上传出的 daymate.db 包含全部已提交数据（两种目标共用）
        runCatching {
            val d: SupportSQLiteDatabase = db.openHelper.writableDatabase
            d.query("PRAGMA wal_checkpoint(TRUNCATE)").use { /* drain cursor */ }
        }

        if (localUri != null && localBackupAllowed(localUri)) {
            runCatching { StorageBackup.exportInternal(context, internalDb, localUri) }
        }
        if (cloudCfg != null && cloudBackupAllowed(cloudCfg)) {
            runCatching { CloudBackup.upload(internalDb, cloudCfg) }
        }
    }

    /**
     * 本地空数据护栏：当前应用为空、而备份已有数据时，禁止用空数据覆盖备份。
     * 采用保守策略——探测失败（无法确定）也视为有数据并跳过，杜绝空数据静默覆盖好备份。
     */
    private suspend fun localBackupAllowed(uri: Uri): Boolean {
        val appRows = appDataRows() ?: return false   // 读不出当前数据量时不冒险覆盖
        if (appRows != 0) return true
        if (!StorageBackup.exists(context)) return true
        val backupRows =
            runCatching { StorageBackup.probeBackupDataRows(context, uri) }.getOrDefault(-1)
        return backupRows == 0
    }

    /** 云端空数据护栏：同样保守——探测失败（-1）一律按有数据拦截。 */
    private suspend fun cloudBackupAllowed(cfg: WebDavConfig): Boolean {
        val appRows = appDataRows() ?: return false
        if (appRows != 0) return true
        val remoteRows =
            runCatching { CloudBackup.probeRemoteRows(context, cfg) }.getOrDefault(-1)
        return remoteRows == 0
    }

    /** 当前应用的数据行数（倒数日 + 文件夹 + Vault 事件）；查询失败返回 null。 */
    private suspend fun appDataRows(): Int? = runCatching {
        db.eventDao().countAll() + db.folderDao().countAll() + db.vaultEventDao().countAll()
    }.getOrNull()
}
