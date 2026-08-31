package com.ayaka7452.daymate.feature.setup

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
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
import com.ayaka7452.daymate.core.StorageConfig
import java.io.File

/**
 * 通用「选择外部存储数据库目录」流程。
 *
 * 行为（与用户需求一致）：
 *  - 启动前若缺少「所有文件访问」权限，先引导授权；
 *  - 通过 SAF 选择目录后，解析真实路径；
 *  - 若目录中已有合法的 daymate.db（SQLite 文件头校验通过）则直接读取其中数据；
 *  - 若没有（或文件损坏）则创建一份全新的数据库；
 *  - 完成后持久化位置、重建容器并重启回主页。
 *
 * @param title       顶栏标题
 * @param intro      说明文案
 * @param showBack   是否显示返回按钮（设置页内嵌时显示）
 * @param onBack     返回按钮回调（设置页内嵌时关闭设置页的「更改目录」视图）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSetupBody(
    title: String,
    intro: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {}
) {
    val ctx = LocalContext.current

    var showPerm by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    fun apply(extPath: String, extUri: String) {
        val app = ctx.applicationContext as DayMateApp
        val dbFile = File(extPath, "daymate.db")
        val vaultFile = File(extPath, "vault.db")
        // 先关闭当前容器，确保 WAL 已 checkpoint，避免复制到未提交的临时数据
        app.container.close()
        // 若目录已有「损坏/非 SQLite」的文件，先清掉，避免 Room 打开失败
        if (dbFile.exists() && !StorageConfig.isReadableSqlite(dbFile)) {
            dbFile.delete()
            vaultFile.delete()
        }
        // 首次切换到外部存储：把已存在的内部数据库迁移过去，避免丢失现有倒数日/文件夹数据
        if (!dbFile.exists()) {
            val internalDb = ctx.getDatabasePath("daymate.db")
            if (internalDb.exists()) StorageConfig.copyDatabase(internalDb, dbFile)
        }
        StorageConfig.setExternal(ctx, extUri, extPath)
        app.rebuildContainer()
        StorageConfig.restartToHome(ctx)
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = StorageConfig.treeUriToPath(uri)
        if (path == null) {
            showError = true
            return@rememberLauncherForActivityResult
        }
        apply(path, uri.toString())
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (StorageConfig.hasAllFilesAccess()) showConfirm = true
    }

    fun onPick() {
        if (!StorageConfig.hasAllFilesAccess()) {
            showPerm = true
            return
        }
        showConfirm = true
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
                .padding(16.dp)
        ) {
            Text(intro, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onPick() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择文件夹")
            }
            val cur = StorageConfig.externalPath(ctx)
            if (cur != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "当前数据库位置：$cur",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (showPerm) {
            AlertDialog(
                onDismissRequest = { showPerm = false },
                title = { Text("需要授权") },
                text = {
                    Text(
                        "DayMate 需要「所有文件访问」权限，才能把数据库直接写入你选择的文件夹。" +
                            "请在接下来的系统页面中为 DayMate 开启该权限。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPerm = false
                        val intent = StorageConfig.allFilesAccessIntent(ctx)
                        val resolved = intent.resolveActivity(ctx.packageManager) != null
                        Log.d("DayMateStorage", "去授权: resolveActivity=$resolved")
                        try {
                            if (resolved) {
                                permLauncher.launch(intent)
                            } else {
                                StorageConfig.openAppDetails(ctx)
                            }
                        } catch (_: ActivityNotFoundException) {
                            // 极少数 ROM 即便 resolveActivity 通过也会抛异常，降级到应用详情页
                            StorageConfig.openAppDetails(ctx)
                        }
                    }) { Text("去授权") }
                },
                dismissButton = {
                    TextButton(onClick = { showPerm = false }) { Text("取消") }
                }
            )
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("使用此文件夹？") },
                text = {
                    Text(
                        "将把数据库存放到该文件夹（daymate.db / vault.db）。" +
                            "若该文件夹已有合法的 DayMate 数据库，将直接读取其中数据；" +
                            "否则将创建一份全新的数据库。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirm = false
                        treeLauncher.launch(null)
                    }) { Text("选择此文件夹") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) { Text("取消") }
                }
            )
        }

        if (showError) {
            AlertDialog(
                onDismissRequest = { showError = false },
                title = { Text("无法使用该文件夹") },
                text = {
                    Text(
                        "未能解析你选择的文件夹路径，请换一个位置" +
                            "（例如内部存储根目录下的某个文件夹）重试。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showError = false }) { Text("知道了") }
                }
            )
        }
    }
}
