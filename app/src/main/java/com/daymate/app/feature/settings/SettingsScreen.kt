package com.ayaka7452.daymate.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.StorageConfig
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val themeMode by container.settingsRepository.themeMode
        .collectAsState(initial = "system")
    val defaultSort by container.settingsRepository.defaultSort
        .collectAsState(initial = "remaining_asc")
    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current

    val INTERNAL = StorageConfig.INTERNAL
    val EXTERNAL = StorageConfig.EXTERNAL

    // 当前选中的存储位置（来自持久化配置）
    var selectedStorage by remember { mutableStateOf(StorageConfig.mode(ctx)) }
    var showPermDialog by remember { mutableStateOf(false) }
    var showRiskDialog by remember { mutableStateOf(false) }
    var showPathError by remember { mutableStateOf(false) }
    // 待切换方向："external" / "internal"
    var pendingSwitch by remember { mutableStateOf<String?>(null) }

    /**
     * 把当前（旧位置）数据库迁移到外部目录后切换。
     * 先关闭旧库以落地 WAL，再复制文件，最后持久化新位置并重建容器 + 重启。
     */
    fun doSwitchToExternal(extPath: String, extUri: String) {
        runCatching {
            container.close()
            StorageConfig.copyDatabase(
                ctx.getDatabasePath("daymate.db"),
                File(extPath, "daymate.db")
            )
            StorageConfig.copyDatabase(
                ctx.getDatabasePath("vault.db"),
                File(extPath, "vault.db")
            )
            StorageConfig.setExternal(ctx, extUri, extPath)
            (ctx.applicationContext as DayMateApp).rebuildContainer()
            StorageConfig.restartToHome(ctx)
        }
    }

    /** 把外部数据库迁回内部沙盒后切换。 */
    fun doSwitchToInternal() {
        runCatching {
            val extPath = StorageConfig.externalPath(ctx)
            container.close()
            if (!extPath.isNullOrEmpty()) {
                StorageConfig.copyDatabase(
                    File(extPath, "daymate.db"),
                    ctx.getDatabasePath("daymate.db")
                )
                StorageConfig.copyDatabase(
                    File(extPath, "vault.db"),
                    ctx.getDatabasePath("vault.db")
                )
            }
            StorageConfig.setInternal(ctx)
            (ctx.applicationContext as DayMateApp).rebuildContainer()
            StorageConfig.restartToHome(ctx)
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = StorageConfig.treeUriToPath(uri)
        if (path == null) {
            showPathError = true
            return@rememberLauncherForActivityResult
        }
        doSwitchToExternal(path, uri.toString())
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 从授权页返回后，若已获得权限则继续走风险提示流程
        if (StorageConfig.hasAllFilesAccess()) {
            showRiskDialog = true
        }
    }

    fun onSelectStorage(target: String) {
        if (target == selectedStorage) return
        pendingSwitch = target
        if (target == EXTERNAL) {
            // 外部存储需先确认「所有文件访问」权限
            if (!StorageConfig.hasAllFilesAccess()) {
                showPermDialog = true
                return
            }
            showRiskDialog = true
        } else {
            showRiskDialog = true
        }
    }

    val themeOptions = listOf(
        "system" to "跟随系统",
        "light" to "始终浅色",
        "dark" to "始终深色"
    )
    val sortOptions = listOf(
        "remaining_asc" to "剩余天数升序",
        "remaining_desc" to "剩余天数降序",
        "manual" to "手动排序"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            Text("主题", style = MaterialTheme.typography.titleMedium)
            themeOptions.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { container.settingsRepository.setThemeMode(value) }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == value,
                        onClick = {
                            scope.launch { container.settingsRepository.setThemeMode(value) }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            Text(
                "默认排序",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            sortOptions.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { container.settingsRepository.setDefaultSort(value) }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = defaultSort == value,
                        onClick = {
                            scope.launch { container.settingsRepository.setDefaultSort(value) }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            // ===== 数据库存储位置 =====
            Text(
                "数据库存储位置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "外部存储会直接把数据库写入你选择的目录，便于用电脑或文件管理器备份；但文件若被删除、移动或所在存储被卸载，事件数据将永久丢失。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            listOf(
                INTERNAL to "内部存储（应用沙盒，默认）",
                EXTERNAL to "外部存储（通过 SAF 选择目录）"
            ).forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectStorage(value) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedStorage == value,
                        onClick = { onSelectStorage(value) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (selectedStorage == EXTERNAL) {
                Text(
                    "当前外部路径：${StorageConfig.externalPath(ctx) ?: "未知"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAbout)
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("关于", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // ===== 弹窗 =====

        // 1) 缺少「所有文件访问」权限 → 引导授权
        if (showPermDialog) {
            AlertDialog(
                onDismissRequest = { showPermDialog = false },
                title = { Text("需要授权") },
                text = {
                    Text(
                        "外部存储数据库需要「所有文件访问」权限，才能把数据库文件直接写入你选择的目录。" +
                            "请在接下来的系统页面中为 DayMate 开启该权限。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPermDialog = false
                        permLauncher.launch(StorageConfig.allFilesAccessIntent(ctx))
                    }) { Text("去授权") }
                },
                dismissButton = {
                    TextButton(onClick = { showPermDialog = false; pendingSwitch = null }) {
                        Text("取消")
                    }
                }
            )
        }

        // 2) 切换风险提示
        if (showRiskDialog) {
            val toExternal = pendingSwitch == EXTERNAL
            AlertDialog(
                onDismissRequest = { showRiskDialog = false; pendingSwitch = null },
                title = { Text(if (toExternal) "切换到外部存储？" else "切换到内部存储？") },
                text = {
                    Text(
                        if (toExternal)
                            "即将打开目录选择。所选目录下的 daymate.db / vault.db 即为数据库文件。" +
                                "这些文件被删除、移动或存储被卸载时，数据会永久丢失，且不会随应用卸载自动清理。是否继续？"
                        else
                            "将把当前外部数据库迁移回应用内部沙盒，数据会保留，但之后无法在文件管理器中直接访问该文件。是否继续？"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRiskDialog = false
                        if (toExternal) {
                            treeLauncher.launch(null)
                        } else {
                            doSwitchToInternal()
                        }
                    }) { Text("继续") }
                },
                dismissButton = {
                    TextButton(onClick = { showRiskDialog = false; pendingSwitch = null }) {
                        Text("取消")
                    }
                }
            )
        }

        // 3) 目录路径无法解析
        if (showPathError) {
            AlertDialog(
                onDismissRequest = { showPathError = false },
                title = { Text("无法使用该目录") },
                text = {
                    Text(
                        "未能解析你选择的目录路径。请换一个位置（例如内部存储根目录下的某个文件夹）重试。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showPathError = false }) { Text("知道了") }
                }
            )
        }
    }
}
