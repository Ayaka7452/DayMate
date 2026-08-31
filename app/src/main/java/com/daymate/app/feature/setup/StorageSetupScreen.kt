package com.ayaka7452.daymate.feature.setup

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

    val internalDb = remember { app.getDatabasePath("daymate.db") }

    /** 先关闭容器（触发 WAL checkpoint 落盘），执行 block，再重建容器刷新界面。 */
    fun withClosedDb(block: () -> Unit) {
        runCatching { app.container.close() }
        runCatching(block)
        runCatching { app.rebuildContainer() }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        StorageConfig.setBackupFolder(ctx, uri)
        busy = true
        runCatching {
            withClosedDb { StorageBackup.exportInternal(ctx, internalDb) }
        }.onSuccess { status = "已设置备份文件夹，并导出当前数据到该位置。" }
            .onFailure { status = "导出失败：${it.message}" }
        busy = false
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
        }.onSuccess { status = "已从备份恢复。" }
            .onFailure { status = "恢复失败：${it.message}" }
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
                    "你可以选择一个文件夹，把数据导出备份到这里，或随时从备份恢复。",
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
                onClick = { restore() },
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
    }
}
