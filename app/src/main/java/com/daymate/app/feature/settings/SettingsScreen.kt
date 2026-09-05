package com.ayaka7452.daymate.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.feature.setup.StorageSetupBody
import com.ayaka7452.daymate.widget.WidgetPrefs
import com.ayaka7452.daymate.widget.WidgetRenderer
import kotlinx.coroutines.launch

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

    val autoBackup by container.settingsRepository.autoBackupEnabled
        .collectAsState(initial = true)
    val backupConfigured = StorageConfig.isBackupConfigured(ctx)

    // 桌面小组件：默认事件 + 卡片透明度（SharedPreferences，与 Widget 进程共享）
    val widgetEvents by container.eventRepository.observeAll().collectAsState(initial = emptyList())
    var showWidgetEventDialog by remember { mutableStateOf(false) }
    var widgetDefaultEventId by remember { mutableStateOf(WidgetPrefs.defaultEventId(ctx)) }
    var widgetOpacity by remember { mutableStateOf(WidgetPrefs.opacity(ctx).toFloat()) }

    // 数据备份：选择文件夹仅作 SAF 导出/导入目标，不需要任何存储权限（全屏覆盖）
    var showSetup by remember { mutableStateOf(false) }
    if (showSetup) {
        StorageSetupBody(
            title = "数据备份",
            showBack = true,
            onBack = { showSetup = false }
        )
        return
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

            // ===== 数据备份（主库在内部，所选文件夹仅作备份目标） =====
            Text(
                "数据备份",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "主数据库保存在应用内部，安全且无需任何存储权限。可选择一个文件夹用于导出/恢复备份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSetup = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("数据备份", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "当前备份位置：${StorageConfig.displayPath(StorageConfig.backupUri(ctx))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )

            // 修改后自动备份：默认开启；未选择备份文件夹时禁用
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("修改后自动备份", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (backupConfigured) "每次修改数据后自动备份到所选文件夹" else "需先选择备份文件夹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = autoBackup,
                    enabled = backupConfigured,
                    onCheckedChange = { scope.launch { container.settingsRepository.setAutoBackupEnabled(it) } }
                )
            }

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            // ===== 桌面小组件（默认事件 + 卡片透明度） =====
            Text(
                "桌面小组件",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "部署到桌面时可单独选择每个小组件的事件；此处设置默认事件与卡片透明度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWidgetEventDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Widgets, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("默认显示事件", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (widgetDefaultEventId == 0L) "当前：自动（最近的倒数日）"
                        else "当前：" + (widgetEvents.firstOrNull { it.id == widgetDefaultEventId }?.title
                            ?: "已删除的事件"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Text(
                "卡片透明度（${widgetOpacity.toInt()}%）",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
            Slider(
                value = widgetOpacity / 100f,
                onValueChange = { widgetOpacity = (it * 100f).coerceIn(5f, 100f) },
                onValueChangeFinished = {
                    WidgetPrefs.setOpacity(ctx, widgetOpacity.toInt())
                    WidgetRenderer.refreshAll(ctx)
                },
                valueRange = 0.05f..1f
            )

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
    }

    // 小组件默认事件选择弹窗
    if (showWidgetEventDialog) {
        AlertDialog(
            onDismissRequest = { showWidgetEventDialog = false },
            title = { Text("小组件默认事件") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    WidgetEventOption(
                        title = "自动（最近的倒数日）",
                        subtitle = "总是显示最近的一个事件",
                        selected = widgetDefaultEventId == 0L
                    ) {
                        widgetDefaultEventId = 0L
                        scope.launch { WidgetPrefs.setDefaultEventId(ctx, 0L) }
                        WidgetRenderer.refreshAll(ctx)
                        showWidgetEventDialog = false
                    }
                    widgetEvents.forEach { ev ->
                        WidgetEventOption(
                            title = ev.title,
                            subtitle = "固定显示该事件",
                            selected = widgetDefaultEventId == ev.id
                        ) {
                            widgetDefaultEventId = ev.id
                            scope.launch { WidgetPrefs.setDefaultEventId(ctx, ev.id) }
                            WidgetRenderer.refreshAll(ctx)
                            showWidgetEventDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWidgetEventDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun WidgetEventOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
