package com.ayaka7452.daymate.feature.setup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.MainActivity
import com.ayaka7452.daymate.core.StorageBackup
import com.ayaka7452.daymate.core.StorageConfig

/**
 * 「数据备份」配置界面（路线 A：主库在内部沙盒，用户所选文件夹仅作 SAF 导出/导入备份，
 * 全程不申请任何存储权限）。
 *
 * 行为：
 *  - 「选择备份文件夹」：用 SAF 选目录，持久化 URI 权限后自动导出当前数据；
 *  - 「立即备份」：把内部主库复制到所选文件夹；
 *  - 「从备份恢复」：把所选文件夹的 daymate.db 复制回内部主库（重建容器）；
 *  - 「清除备份文件夹」：仅清除配置（不删除外部文件）。
 *
 * 导出/导入会先 close 容器触发 WAL 落盘，再复制文件，最后 rebuild 容器刷新界面。
 *
 * @param title       顶栏标题
 * @param showBack   是否显示返回按钮（设置页内嵌时显示）
 * @param onBack     返回按钮回调
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

    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // 选择文件夹后若检测到已有备份，则弹出冲突确认；此处保存待裁决的目录 Uri
    var conflictUri by remember { mutableStateOf<Uri?>(null) }
    // 「从备份恢复」二次确认
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val internalDb = remember { app.getDatabasePath("daymate.db") }

    /** 先关闭容器（触发 WAL checkpoint 落盘），执行 block，再重建容器刷新界面。 */
    fun withClosedDb(block: () -> Unit) {
        runCatching { app.container.close() }
        runCatching(block)
        runCatching { app.rebuildContainer() }
    }

    /** 保存文件夹，并把当前内部数据库导出为该文件夹的备份（覆盖其中的旧备份）。 */
    fun commitFolder(uri: Uri) {
        busy = true
        StorageConfig.setBackupFolder(ctx, uri)
        runCatching {
            withClosedDb { StorageBackup.exportInternal(ctx, internalDb, uri) }
        }.onSuccess { status = "已设置备份文件夹，并备份当前数据到该位置。" }
            .onFailure { status = "导出失败：${it.message}" }
        busy = false
    }

    /** 以所选文件夹的备份为准：导入到内部库并设为备份目录（不触碰/不删除原备份文件）。 */
    fun restoreFromSelected(uri: Uri) {
        busy = true
        runCatching {
            withClosedDb { StorageBackup.importExternal(ctx, internalDb, uri) }
        }.onSuccess {
            StorageConfig.setBackupFolder(ctx, uri)
            status = "已从所选备份恢复数据，并设为备份文件夹。"
            finishAndRestartToHome()
        }.onFailure { status = "恢复失败：${it.message}" }
        busy = false
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

    fun backupNow() {
        if (!StorageConfig.isBackupConfigured(ctx)) { status = "请先选择备份文件夹。"; return }
        busy = true
        runCatching {
            withClosedDb { StorageBackup.exportInternal(ctx, internalDb) }
        }.onSuccess { status = "已备份到所选文件夹。" }
            .onFailure { status = "备份失败：${it.message}" }
        busy = false
    }

    fun restore() {
        if (!StorageConfig.isBackupConfigured(ctx)) { status = "请先选择备份文件夹。"; return }
        if (!StorageBackup.isBackupReadable(ctx)) {
            status = "所选文件夹中没有可用的 DayMate 数据库（daymate.db）。"
            return
        }
        busy = true
        runCatching {
            withClosedDb { StorageBackup.importExternal(ctx, internalDb) }
        }.onSuccess {
            status = "已从备份恢复。"
            finishAndRestartToHome()
        }.onFailure { status = "恢复失败：${it.message}" }
        busy = false
    }

    fun clear() {
        StorageConfig.clearBackupFolder(ctx)
        status = "已清除备份文件夹设置（外部文件未删除）。"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                "DayMate 的主数据库保存在应用内部（安全、且不需要任何存储权限）。" +
                    "你可以选择一个文件夹，把数据导出备份到这里，或随时从备份恢复。" +
                    "若所选文件夹中已有备份，会先询问你要「以备份为准」还是「覆盖备份」，避免误删已有备份。",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { treeLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择备份文件夹")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { backupNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("立即备份") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!StorageConfig.isBackupConfigured(ctx)) { status = "请先选择备份文件夹。"; return@Button }
                    if (!StorageBackup.isBackupReadable(ctx)) {
                        status = "所选文件夹中没有可用的 DayMate 数据库（daymate.db）。"
                        return@Button
                    }
                    showRestoreConfirm = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("从备份恢复") }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { clear() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("清除备份文件夹") }

            Spacer(Modifier.height(16.dp))
            Text(
                "当前备份位置：${StorageConfig.displayPath(StorageConfig.backupUri(ctx))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (status != null) {
                Spacer(Modifier.height(8.dp))
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
                title = { Text("文件夹中已有备份数据") },
                text = {
                    Text(
                        if (canRestore)
                            "所选文件夹已存在可用的 DayMate 备份（daymate.db）。若不确认就直接写入，会覆盖并丢失该备份。请选择处理方式："
                        else
                            "所选文件夹已存在 daymate.db，但它不是有效的 DayMate 数据库。建议用当前数据覆盖，或取消选择。"
                    )
                },
                confirmButton = {
                    Row {
                        if (canRestore) {
                            TextButton(onClick = {
                                val u = conflictUri ?: return@TextButton
                                conflictUri = null
                                restoreFromSelected(u)
                            }) { Text("以备份为准") }
                        }
                        TextButton(onClick = {
                            val u = conflictUri ?: return@TextButton
                            conflictUri = null
                            commitFolder(u)
                        }) { Text(if (canRestore) "覆盖备份" else "用当前数据覆盖") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        conflictUri?.let { StorageConfig.releaseBackupPermission(ctx, it) }
                        conflictUri = null
                    }) { Text("取消") }
                }
            )
        }

        // ===== 「从备份恢复」二次确认：将用备份替换当前应用数据 =====
        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text("从备份恢复") },
                text = {
                    Text("将用所选文件夹的备份数据替换当前应用内的全部数据（倒数日、Vault 等）。此操作不可撤销，确定继续？")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirm = false
                        restore()
                    }) { Text("继续") }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
                }
            )
        }
    }
}
