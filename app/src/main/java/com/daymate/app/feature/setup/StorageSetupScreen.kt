package com.ayaka7452.daymate.feature.setup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.MainActivity
import com.ayaka7452.daymate.WebDavActivity
import com.ayaka7452.daymate.core.StorageBackup
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.core.cloud.CloudBackup
import com.ayaka7452.daymate.core.cloud.WebDavConfig
import com.ayaka7452.daymate.core.cloud.WebDavStore
import com.ayaka7452.daymate.data.repo.SettingsRepository
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 「数据备份」配置界面（路线 A：主库在内部沙盒，用户所选文件夹仅作 SAF 导出/导入备份，
 * 全程不申请任何存储权限）。
 *
 * 备份位置三态（对应 [SettingsRepository.backupTarget]）：
 *  - 仅本地：只写 SAF 文件夹（默认，与旧版本行为一致）；
 *  - 本地与云端：同时在两处各备份一份；
 *  - 仅云端：只写 WebDAV（配置见 [WebDavSetupScreen]）。
 *
 * 导出（备份）只做 WAL 落盘、容器保持在线；导入/恢复才会 close + rebuild 容器，并以
 * CLEAR_TASK 重启回主页（确保所有页面用上新容器）。
 *
 * 数据安全：任何会覆盖已有备份的操作都先探测目标数据量，禁止用「空应用」数据覆盖有内容的备份；
 * 从云端恢复时先把备份下载到临时文件并校验为合法 SQLite，**校验通过后才关库替换**，
 * 避免下载失败把主库截断成空文件。
 *
 * @param title       顶栏标题
 * @param showBack    是否显示返回按钮（设置页内嵌时显示）
 * @param onBack      返回按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSetupBody(
    title: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DayMateApp

    val backupTarget by app.container.settingsRepository.backupTarget
        .collectAsState(initial = SettingsRepository.BACKUP_TARGET_LOCAL)

    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // 选择文件夹后若检测到已有备份，则弹出冲突确认；此处保存待裁决的目录 Uri
    var conflictUri by remember { mutableStateOf<Uri?>(null) }
    // 「从备份恢复」二次确认
    var showRestoreConfirm by remember { mutableStateOf(false) }
    // 用当前应用数据覆盖备份前的二次确认（非空表示待确认的目标目录）
    var overwriteTarget by remember { mutableStateOf<Uri?>(null) }
    // 备份有数据而当前应用为空：禁止用空数据覆盖备份，弹出警告后仅允许关闭
    var overwriteBlocked by remember { mutableStateOf(false) }
    // 云端：覆盖确认 / 空数据拦截 / 恢复二次确认
    var cloudOverwriteTarget by remember { mutableStateOf<WebDavConfig?>(null) }
    var cloudBlocked by remember { mutableStateOf(false) }
    var showCloudRestoreConfirm by remember { mutableStateOf(false) }

    /**
     * WebDAV 配置是否完整。它存在 SharedPreferences 里、没有变更通知：从 WebDAV 配置页
     * 返回本页时不会自动重组，若不重读就一直是「尚未配置」，让人误以为没保存成功。
     * 这里用生命周期观察者在每次 ON_RESUME（即从配置页返回）时重读一遍。
     */
    var cloudConfigured by remember { mutableStateOf(WebDavStore.isConfigured(ctx)) }
    DisposableEffect(ctx) {
        val activity = ctx as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cloudConfigured = WebDavStore.isConfigured(ctx)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()
    val internalDb = remember { app.getDatabasePath("daymate.db") }

    /** 先关闭容器（触发 WAL checkpoint 落盘），执行 block，再重建容器刷新界面。
     *  仅用于「导入/恢复」这类真正替换数据库文件的场景，且随后必须重启回主页。
     *  注意：[runCatching] 会吞掉 block 的异常，调用方需要失败信息时请在 block 内自行捕获。 */
    fun withClosedDb(block: () -> Unit) {
        runCatching { app.container.close() }
        runCatching(block)
        runCatching { app.rebuildContainer() }
    }

    /**
     * 导出（备份）前把 WAL 落盘即可，**不关闭、不重建容器**：
     * rebuildContainer() 会替换 Application 的 container，而在屏页面（主页等）remember 住的
     * Flow 仍指向旧容器里的旧库——旧库被关闭后将永远收不到变更通知，表现为「新建事件后列表
     * 不刷新，直到重启应用」。导出不改变应用数据，库保持在线即可（与自动备份同策略）。
     */
    fun withCheckpoint(block: () -> Unit) {
        runCatching { app.container.checkpointWal() }
        runCatching(block)
    }

    /**
     * 恢复数据后，以全新容器重启回主页。由于 rebuildContainer() 会替换 Application 的
     * container 实例，而仍在后台的 Home Activity 仍持有旧容器引用（指向已关闭的库），
     * 直接返回会看到空数据。用 CLEAR_TASK|NEW_TASK 重启主页可确保各在屏页面都使用恢复后的数据库。
     */
    fun finishAndRestartToHome() {
        val act = ctx as? Activity ?: return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        act.finish()
    }

    var closedDbFailure: Throwable? = null

    /** 关库执行替换文件的操作；返回失败原因（成功为 null）。 */
    fun replaceDbFile(block: () -> Unit): Throwable? {
        closedDbFailure = null
        withClosedDb {
            runCatching(block).onFailure { closedDbFailure = it }
        }
        return closedDbFailure
    }

    // ===== 备份位置 =====

    fun setTarget(t: String) {
        scope.launch { app.container.settingsRepository.setBackupTarget(t) }
        status = when (t) {
            SettingsRepository.BACKUP_TARGET_BOTH -> Tr.s(R.string.storage_target_both)
            SettingsRepository.BACKUP_TARGET_CLOUD -> Tr.s(R.string.storage_target_cloud)
            else -> Tr.s(R.string.storage_target_local)
        }
    }

    // ===== 本地备份 =====

    /** 保存文件夹，并把当前内部数据库导出为该文件夹的备份（覆盖其中的旧备份）。 */
    fun commitFolder(uri: Uri) {
        busy = true
        StorageConfig.setBackupFolder(ctx, uri)
        runCatching {
            withCheckpoint { StorageBackup.exportInternal(ctx, internalDb, uri) }
        }.onSuccess { status = Tr.s(R.string.storage_set_folder_done) }
            .onFailure { status = Tr.s(R.string.storage_export_failed, it.message) }
        busy = false
    }

    /** 以所选文件夹的备份为准：导入到内部库并设为备份目录（不触碰/不删除原备份文件）。 */
    fun restoreFromSelected(uri: Uri) {
        busy = true
        val failure = replaceDbFile {
            if (!StorageBackup.importExternal(ctx, internalDb, uri)) {
                error(Tr.s(R.string.storage_no_db_short))
            }
        }
        if (failure != null) {
            status = Tr.s(R.string.storage_restore_failed, failure.message)
            busy = false
            return
        }
        StorageConfig.setBackupFolder(ctx, uri)
        status = Tr.s(R.string.storage_restore_done)
        busy = false
        finishAndRestartToHome()
    }

    /** 把当前内部主库导出（覆盖）到指定备份文件夹；alsoConfigure 为 true 时一并设为备份文件夹。 */
    fun doExport(targetUri: Uri, alsoConfigure: Boolean) {
        busy = true
        if (alsoConfigure) StorageConfig.setBackupFolder(ctx, targetUri)
        runCatching {
            withCheckpoint { StorageBackup.exportInternal(ctx, internalDb, targetUri) }
        }.onSuccess { status = Tr.s(R.string.storage_backup_done) }
            .onFailure { status = Tr.s(R.string.storage_backup_failed, it.message) }
        busy = false
    }

    /**
     * 计算「当前应用数据行数」（倒数日 + 文件夹 + 保险箱事件与文件夹 + 周期管家），
     * 用于在覆盖备份前判断应用是否为空。通过仍在线的 Room 容器读取，结果权威。
     *
     * **必须与 [StorageBackup.countDataRows] 的 DATA_TABLES 保持一致**：
     * 漏掉任何一张用户数据表，都会让「只剩这类数据」（例如只记经期、不留倒数日）的用户
     * 被误判为空库，备份被拦下——历史上周期管家就被漏掉过。
     */
    suspend fun countAppDataRows(): Int =
        app.container.eventRepository.countAll() +
            app.container.folderRepository.countAll() +
            app.container.vaultRepository.countAll() +
            app.container.vaultFolderRepository.countAll() +
            app.container.cycleRepository.countAll() +
            app.container.cycleNoteRepository.countAll()

    /**
     * 判断是否应阻止「用当前应用数据覆盖备份」：
     * 仅当「应用为空 且 目标文件夹中已有备份 且 备份非空（或探测失败无法确定）」时返回 true。
     * 采用保守策略——探测失败时一律视为有数据，避免用空数据静默覆盖好备份。
     */
    suspend fun shouldBlockOverwrite(backupUri: Uri): Boolean {
        if (countAppDataRows() != 0) return false
        // 注意：不能用 StorageBackup.exists(ctx)（读的是「已配置」的备份目录）——
        // 冲突弹窗阶段新选的目录尚未写入配置，exists 会误判为「无备份」而直接放行，
        // 导致空数据静默覆盖有数据的备份（连确认弹窗都跳过）。此处直接探测传入的目录。
        // probeBackupDataRows：0=无备份文件或确认为空；>0=有数据；-1=复制/读库失败（无法判定）。
        val backupRows = StorageBackup.probeBackupDataRows(ctx, backupUri)
        return backupRows != 0
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 先取得对该目录的持久化权限，才能探查其中是否已有备份
        StorageConfig.takeBackupPermission(ctx, uri)
        when (StorageBackup.previewBackup(ctx, uri)) {
            StorageBackup.BackupPreview.None -> commitFolder(uri)   // 无冲突：直接保存并导出当前数据作为初始备份
            else -> conflictUri = uri                               // 已有备份：交给冲突确认框裁决
        }
    }

    fun backupLocalNow() {
        if (!StorageConfig.isBackupConfigured(ctx)) { status = Tr.s(R.string.storage_need_folder); return }
        val backupUri = StorageConfig.backupUri(ctx) ?: return
        // 该文件夹尚无任何备份：直接导出当前数据作为初始备份（无数据可丢失，无需提示）
        if (!StorageBackup.exists(ctx)) {
            doExport(backupUri, alsoConfigure = false)
            return
        }
        // 已有备份：覆盖前先探测。应用为空且备份非空（或不确定）时禁止用空数据覆盖
        scope.launch {
            if (shouldBlockOverwrite(backupUri)) {
                overwriteBlocked = true
                return@launch
            }
            overwriteTarget = backupUri
        }
    }

    fun restoreLocal() {
        if (!StorageConfig.isBackupConfigured(ctx)) { status = Tr.s(R.string.storage_need_folder); return }
        if (!StorageBackup.isBackupReadable(ctx)) {
            status = Tr.s(R.string.storage_no_valid_db)
            return
        }
        busy = true
        val failure = replaceDbFile {
            if (!StorageBackup.importExternal(ctx, internalDb)) {
                error(Tr.s(R.string.storage_no_db_backup))
            }
        }
        if (failure != null) {
            status = Tr.s(R.string.storage_restore_failed, failure.message)
            busy = false
            return
        }
        status = Tr.s(R.string.storage_restored)
        busy = false
        finishAndRestartToHome()
    }

    // ===== 云端备份 =====

    fun doCloudExport(cfg: WebDavConfig) {
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { withCheckpoint { CloudBackup.upload(internalDb, cfg) } }
            }.onSuccess { status = Tr.s(R.string.storage_cloud_backup_done) }
                .onFailure { status = Tr.s(R.string.storage_cloud_backup_failed, it.message) }
            busy = false
        }
    }

    /** 手动「立即备份」到云端：先过空数据护栏，再按需弹出覆盖确认。 */
    fun startCloudBackup(cfg: WebDavConfig) {
        busy = true
        scope.launch {
            val appRows = countAppDataRows()
            val remoteExists = runCatching {
                withContext(Dispatchers.IO) { CloudBackup.exists(cfg) }
            }.getOrDefault(false)
            // 空数据护栏：应用为空而云端已有内容（或探测失败）时禁止覆盖
            if (appRows == 0 && remoteExists) {
                val remoteRows = runCatching {
                    withContext(Dispatchers.IO) { CloudBackup.probeRemoteRows(ctx, cfg) }
                }.getOrDefault(-1)
                if (remoteRows != 0) {
                    cloudBlocked = true
                    busy = false
                    return@launch
                }
            }
            busy = false
            if (remoteExists) cloudOverwriteTarget = cfg else doCloudExport(cfg)
        }
    }

    /** 从云端恢复：先下载到临时文件并校验，**通过后才关库替换**（下载失败不影响现有数据）。 */
    fun doCloudRestore(cfg: WebDavConfig) {
        busy = true
        scope.launch {
            val tmp = File(ctx.cacheDir, "daymate_restore_cloud.db")
            val failure: Throwable? = try {
                tmp.delete()
                withContext(Dispatchers.IO) { CloudBackup.download(cfg, tmp) }
                if (!StorageBackup.isSqliteFile(tmp)) {
                    error(Tr.s(R.string.storage_cloud_invalid_db))
                }
                replaceDbFile {
                    tmp.copyTo(internalDb, overwrite = true)
                    // 清掉内部残留的 WAL/SHM，避免旧日志覆盖刚恢复的库
                    File(internalDb.path + "-wal").delete()
                    File(internalDb.path + "-shm").delete()
                }
            } catch (e: Throwable) {
                e
            }
            tmp.delete()
            busy = false
            if (failure != null) {
                status = Tr.s(R.string.storage_cloud_restore_failed, failure.message)
                return@launch
            }
            status = Tr.s(R.string.storage_cloud_restored)
            finishAndRestartToHome()
        }
    }

    fun backupNow() {
        val cloudCfg = WebDavStore.config(ctx)
        when (backupTarget) {
            SettingsRepository.BACKUP_TARGET_CLOUD -> {
                if (cloudCfg == null) {
                    status = Tr.s(R.string.storage_cloud_only_need_webdav)
                    return
                }
                startCloudBackup(cloudCfg)
            }
            SettingsRepository.BACKUP_TARGET_BOTH -> {
                if (!StorageConfig.isBackupConfigured(ctx) && cloudCfg == null) {
                    status = Tr.s(R.string.storage_need_folder_or_cloud)
                    return
                }
                if (StorageConfig.isBackupConfigured(ctx)) {
                    backupLocalNow()
                } else {
                    status = Tr.s(R.string.storage_local_missing)
                }
                if (cloudCfg != null) startCloudBackup(cloudCfg)
            }
            else -> backupLocalNow()
        }
    }

    fun restoreRequest() {
        when (backupTarget) {
            SettingsRepository.BACKUP_TARGET_CLOUD -> {
                if (WebDavStore.config(ctx) == null) {
                    status = Tr.s(R.string.storage_cloud_only_need_webdav)
                    return
                }
                showCloudRestoreConfirm = true
            }
            else -> {
                if (!StorageConfig.isBackupConfigured(ctx)) { status = Tr.s(R.string.storage_need_folder); return }
                if (!StorageBackup.isBackupReadable(ctx)) {
                    status = Tr.s(R.string.storage_no_valid_db)
                    return
                }
                showRestoreConfirm = true
            }
        }
    }

    fun clear() {
        StorageConfig.clearBackupFolder(ctx)
        status = Tr.s(R.string.storage_cleared)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Tr.s(R.string.common_back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                Tr.s(R.string.storage_intro),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle(Tr.s(R.string.storage_section_target))
            BackupTargetRow(
                label = Tr.s(R.string.storage_local),
                desc = Tr.s(R.string.storage_local_desc),
                selected = backupTarget == SettingsRepository.BACKUP_TARGET_LOCAL,
                enabled = !busy,
                onClick = { setTarget(SettingsRepository.BACKUP_TARGET_LOCAL) }
            )
            BackupTargetRow(
                label = Tr.s(R.string.storage_both),
                desc = Tr.s(R.string.storage_both_desc),
                selected = backupTarget == SettingsRepository.BACKUP_TARGET_BOTH,
                enabled = !busy,
                onClick = { setTarget(SettingsRepository.BACKUP_TARGET_BOTH) }
            )
            BackupTargetRow(
                label = Tr.s(R.string.storage_cloud),
                desc = Tr.s(R.string.storage_cloud_desc),
                selected = backupTarget == SettingsRepository.BACKUP_TARGET_CLOUD,
                enabled = !busy,
                onClick = { setTarget(SettingsRepository.BACKUP_TARGET_CLOUD) }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle(Tr.s(R.string.storage_section_local_folder))
            Text(
                Tr.s(R.string.storage_current_path, StorageConfig.displayPath(StorageConfig.backupUri(ctx))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { treeLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text(Tr.s(R.string.storage_pick_folder)) }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { clear() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text(Tr.s(R.string.storage_clear_folder)) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle(Tr.s(R.string.storage_section_webdav))
            // 已配置但备份位置仍是「仅本地」时要说清楚——否则用户会以为配好就该自动上传
            val cloudLocation = WebDavStore.directory(ctx).ifBlank { Tr.s(R.string.storage_root_dir) }
            val cloudActive =
                cloudConfigured && backupTarget != SettingsRepository.BACKUP_TARGET_LOCAL
            Text(
                when {
                    !cloudConfigured ->
                        Tr.s(R.string.storage_webdav_unconfigured)
                    cloudActive -> Tr.s(
                        R.string.storage_webdav_configured,
                        WebDavStore.url(ctx),
                        "$cloudLocation/${WebDavStore.REMOTE_DB}"
                    )
                    else -> Tr.s(
                        R.string.storage_webdav_configured_inactive,
                        WebDavStore.url(ctx),
                        "$cloudLocation/${WebDavStore.REMOTE_DB}"
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    !cloudConfigured -> MaterialTheme.colorScheme.outline
                    cloudActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(ctx, WebDavActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text(if (cloudConfigured) Tr.s(R.string.storage_webdav_edit) else Tr.s(R.string.storage_webdav_config)) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle(Tr.s(R.string.storage_section_manual))
            Text(
                when (backupTarget) {
                    SettingsRepository.BACKUP_TARGET_CLOUD -> Tr.s(R.string.storage_manual_target_cloud)
                    SettingsRepository.BACKUP_TARGET_BOTH -> Tr.s(R.string.storage_manual_target_both)
                    else -> Tr.s(R.string.storage_manual_target_local)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { backupNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text(Tr.s(R.string.storage_backup_now)) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { restoreRequest() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text(Tr.s(R.string.storage_restore_from)) }

            if (status != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    status!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ===== 选择文件夹时的冲突确认：目标目录已有备份 =====
        if (conflictUri != null) {
            val canRestore = StorageBackup.previewBackup(ctx, conflictUri) == StorageBackup.BackupPreview.Valid
            AlertDialog(
                onDismissRequest = {
                    conflictUri?.let { StorageConfig.releaseBackupPermission(ctx, it) }
                    conflictUri = null
                },
                title = { Text(Tr.s(R.string.storage_folder_has_data)) },
                text = {
                    Text(
                        if (canRestore)
                            Tr.s(R.string.storage_overwrite_warn)
                        else
                            Tr.s(R.string.storage_invalid_db_warn)
                    )
                },
                confirmButton = {
                    Row {
                        if (canRestore) {
                            TextButton(onClick = {
                                val u = conflictUri ?: return@TextButton
                                conflictUri = null
                                restoreFromSelected(u)
                            }) { Text(Tr.s(R.string.storage_use_backup)) }
                        }
                        TextButton(onClick = {
                            val u = conflictUri ?: return@TextButton
                            conflictUri = null
                            // 覆盖备份前先探测：备份有数据（或不确定）而当前应用为空时禁止用空数据覆盖
                            scope.launch {
                                if (shouldBlockOverwrite(u)) {
                                    overwriteBlocked = true
                                    return@launch
                                }
                                doExport(u, alsoConfigure = true)
                            }
                        }) { Text(if (canRestore) Tr.s(R.string.storage_overwrite_backup) else Tr.s(R.string.storage_overwrite_with_current)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        conflictUri?.let { StorageConfig.releaseBackupPermission(ctx, it) }
                        conflictUri = null
                    }) { Text(Tr.s(R.string.common_cancel)) }
                }
            )
        }

        // ===== 「从备份恢复」二次确认：将用备份替换当前应用数据 =====
        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text(Tr.s(R.string.storage_restore_title)) },
                text = {
                    Text(Tr.s(R.string.storage_restore_desc))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirm = false
                        restoreLocal()
                    }) { Text(Tr.s(R.string.common_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) { Text(Tr.s(R.string.common_cancel)) }
                }
            )
        }

        // ===== 用应用数据覆盖备份前的确认 =====
        if (overwriteTarget != null) {
            AlertDialog(
                onDismissRequest = { overwriteTarget = null },
                title = { Text(Tr.s(R.string.storage_overwrite_title)) },
                text = {
                    Text(Tr.s(R.string.storage_overwrite_desc))
                },
                confirmButton = {
                    TextButton(onClick = {
                        val u = overwriteTarget ?: return@TextButton
                        overwriteTarget = null
                        doExport(u, alsoConfigure = false)
                    }) { Text(Tr.s(R.string.common_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { overwriteTarget = null }) { Text(Tr.s(R.string.common_cancel)) }
                }
            )
        }

        // ===== 备份有数据而当前应用为空：禁止用空数据覆盖，弹出警告 =====
        if (overwriteBlocked) {
            AlertDialog(
                onDismissRequest = { overwriteBlocked = false },
                title = { Text(Tr.s(R.string.storage_blocked)) },
                text = {
                    Text(
                        Tr.s(R.string.storage_blocked_local_desc)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { overwriteBlocked = false }) { Text(Tr.s(R.string.common_ok)) }
                }
            )
        }

        // ===== 云端：覆盖确认 =====
        val cloudTarget = cloudOverwriteTarget
        if (cloudTarget != null) {
            AlertDialog(
                onDismissRequest = { cloudOverwriteTarget = null },
                title = { Text(Tr.s(R.string.storage_cloud_overwrite_title)) },
                text = {
                    Text(Tr.s(R.string.storage_cloud_overwrite_desc, cloudTarget.directory.ifBlank { Tr.s(R.string.storage_root_dir_plain) }))
                },
                confirmButton = {
                    TextButton(onClick = {
                        cloudOverwriteTarget = null
                        doCloudExport(cloudTarget)
                    }) { Text(Tr.s(R.string.common_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { cloudOverwriteTarget = null }) { Text(Tr.s(R.string.common_cancel)) }
                }
            )
        }

        // ===== 云端：空数据拦截 =====
        if (cloudBlocked) {
            AlertDialog(
                onDismissRequest = { cloudBlocked = false },
                title = { Text(Tr.s(R.string.storage_blocked)) },
                text = {
                    Text(
                        Tr.s(R.string.storage_blocked_cloud_desc)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { cloudBlocked = false }) { Text(Tr.s(R.string.common_ok)) }
                }
            )
        }

        // ===== 云端：恢复二次确认 =====
        if (showCloudRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showCloudRestoreConfirm = false },
                title = { Text(Tr.s(R.string.storage_cloud_restore_title)) },
                text = {
                    Text(Tr.s(R.string.storage_cloud_restore_desc))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCloudRestoreConfirm = false
                        WebDavStore.config(ctx)?.let { doCloudRestore(it) }
                    }) { Text(Tr.s(R.string.common_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCloudRestoreConfirm = false }) { Text(Tr.s(R.string.common_cancel)) }
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
}

/** 备份位置的单选项。 */
@Composable
private fun BackupTargetRow(
    label: String,
    desc: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
