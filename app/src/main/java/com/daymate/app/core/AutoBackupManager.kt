package com.ayaka7452.daymate.core

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ayaka7452.daymate.core.cloud.CloudBackup
import com.ayaka7452.daymate.core.cloud.WebDavConfig
import com.ayaka7452.daymate.core.cloud.WebDavStore
import com.ayaka7452.daymate.data.db.DayMateDatabase
import com.ayaka7452.daymate.data.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 云备份（WebDAV）的执行状态，供主页顶栏的云备份指示器展示。
 *
 * 注意语义：本应用只做**单向上传**（内部主库 → 云端覆盖），没有下载与合并，
 * 因此这是「云备份」而非多设备双向「同步」。
 */
sealed interface CloudBackupState {
    /** 常态：从未备份过，或上次成功后已回到常态。 */
    data object Idle : CloudBackupState

    /** 正在上传。 */
    data object Syncing : CloudBackupState

    /** 最近一次上传成功，[at] 为完成时刻（epoch millis）。 */
    data class Success(val at: Long) : CloudBackupState

    /** 最近一次上传失败，[reason] 为失败原因，[at] 为失败时刻（epoch millis）。 */
    data class Failure(val reason: String, val at: Long) : CloudBackupState
}

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

    /** 成功态在 UI 上停留的时长；到点自动回到 [CloudBackupState.Idle]。 */
    private val successLingerMs = 2000L
    private var backupJob: Job? = null

    // ===== 云备份状态（主页顶栏指示器用） =====

    private val _cloudState = MutableStateFlow<CloudBackupState>(CloudBackupState.Idle)

    /** 最近一次云备份的执行状态；未启用云备份时恒为 [CloudBackupState.Idle]。 */
    val cloudState: StateFlow<CloudBackupState> = _cloudState.asStateFlow()

    /**
     * WebDAV 配置是否完整。初始为 false——[WebDavStore.isConfigured] 会经 AndroidKeyStore
     * 解密密码，不能在构造时于主线程同步执行，改由 [refreshCloudConfig] 在 IO 线程填充。
     * 配置存于 SharedPreferences，**没有变更通知**，因此设置页改完配置后需要重新调用一次。
     */
    private val webDavConfigured = MutableStateFlow(false)

    /** 备份位置是否包含云端（both / cloud）。 */
    private val cloudTarget = MutableStateFlow(false)

    /**
     * 云备份是否已启用：备份位置含云端 **且** WebDAV 配置完整。
     * 主页据此决定云备份图标是否常驻显示。
     */
    val cloudEnabled: StateFlow<Boolean> =
        combine(cloudTarget, webDavConfigured) { target, configured -> target && configured }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // 首帧前先把 WebDAV 配置读出来（IO 线程，避开主线程的 KeyStore 解密）
        scope.launch { refreshCloudConfig() }
        // 跟随「备份位置」设置：切到仅本地时立即收起云备份指示器
        scope.launch {
            settings.backupTarget.collect { target ->
                val enabled = target != SettingsRepository.BACKUP_TARGET_LOCAL
                cloudTarget.value = enabled
                if (!enabled) _cloudState.value = CloudBackupState.Idle
            }
        }
    }

    /** WebDAV 配置可能在设置页被改动（SharedPreferences 无变更通知），回到前台时调用以重新评估。 */
    fun refreshCloudConfig() {
        runCatching {
            val configured = WebDavStore.isConfigured(context)
            webDavConfigured.value = configured
            // 取消配置后不应残留上次的失败角标
            if (!configured) _cloudState.value = CloudBackupState.Idle
        }
    }

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
            _cloudState.value = CloudBackupState.Syncing
            try {
                CloudBackup.upload(internalDb, cloudCfg)
                _cloudState.value = CloudBackupState.Success(System.currentTimeMillis())
                // 勾停留片刻后回到常态；期间若又开始新一轮备份则不打扰（届时已是 Syncing）
                scope.launch {
                    delay(successLingerMs)
                    if (_cloudState.value is CloudBackupState.Success) {
                        _cloudState.value = CloudBackupState.Idle
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // 失败必须留痕：主页常驻红叉角标，直到下次成功或用户手动重试
                _cloudState.value = CloudBackupState.Failure(
                    reason = t.message ?: t::class.java.simpleName,
                    at = System.currentTimeMillis()
                )
            }
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

    /**
     * 当前应用的数据行数（倒数日 + 文件夹 + 保险箱事件与文件夹 + 周期管家）；
     * 查询失败返回 null，调用方按「有数据」保守处理。
     *
     * **表清单必须与 [StorageBackup.countDataRows] 的 DATA_TABLES 一致**：
     * 漏掉任何一张用户数据表，都会让「只剩这类数据」（例如只记经期、不留倒数日）的用户
     * 被误判为空库，自动备份被静默跳过——历史上周期管家就被漏掉过。
     */
    private suspend fun appDataRows(): Int? = runCatching {
        db.eventDao().countAll() + db.folderDao().countAll() +
            db.vaultEventDao().countAll() + db.vaultFolderDao().countAll() +
            db.cycleLogDao().countAll() + db.cycleNoteDao().countAll()
    }.getOrNull()
}
