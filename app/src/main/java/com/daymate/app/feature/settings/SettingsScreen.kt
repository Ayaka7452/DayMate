package com.ayaka7452.daymate.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.data.festival.FestivalRepository
import com.ayaka7452.daymate.feature.setup.StorageSetupBody
import com.ayaka7452.daymate.widget.WidgetRenderer
import kotlinx.coroutines.launch
import android.widget.Toast

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
    val homeTopCard by container.settingsRepository.homeTopCard
        .collectAsState(initial = "festival")
    val homeBadgeEmoji by container.settingsRepository.homeBadgeEmoji
        .collectAsState(initial = "☀️")
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val autoBackup by container.settingsRepository.autoBackupEnabled
        .collectAsState(initial = true)
    val backupConfigured = StorageConfig.isBackupConfigured(ctx)

    // 节假日数据：不内置离线数据，由应用从可配置的数据源下载并缓存
    val festivalRepo = container.festivalRepository
    var festivalSourceLabel by remember { mutableStateOf(festivalRepo.sourceLabel()) }
    var festivalStatus by remember { mutableStateOf(festivalRepo.dataStatusText()) }
    var festivalDownloading by remember { mutableStateOf(false) }
    var showFestivalSourceDialog by remember { mutableStateOf(false) }
    var showFestivalCustomInput by remember { mutableStateOf(false) }
    var festivalCustomUrl by remember { mutableStateOf(festivalRepo.sourceUrl()) }
    var showBadgeEmojiDialog by remember { mutableStateOf(false) }

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
    val homeCardOptions = listOf(
        "festival" to "下一个节假日（默认）",
        "event" to "最近的倒数日",
        "off" to "关闭"
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

            // ===== 主页顶部卡片 =====
            Text(
                "主页顶部卡片",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "控制主页列表顶部的卡片显示内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            homeCardOptions.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { container.settingsRepository.setHomeTopCard(value) }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = homeTopCard == value,
                        onClick = {
                            scope.launch { container.settingsRepository.setHomeTopCard(value) }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            TextButton(onClick = {
                scope.launch { container.settingsRepository.setHomeTopCard("festival") }
                Toast.makeText(ctx, "已恢复默认（下一个节假日）", Toast.LENGTH_SHORT).show()
            }) { Text("恢复默认") }

            // 节日卡片右侧角标 emoji（卡片只显示放假节日，不需要「休/班」标记）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBadgeEmojiDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("节日卡片角标", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "卡片右侧的表情符号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(homeBadgeEmoji, style = MaterialTheme.typography.titleLarge)
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

            // ===== 节假日数据（在线下载 + 本地缓存，无内置离线数据） =====
            Text(
                "节假日数据",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "由应用从数据源下载并缓存到本机；跟随节日、节日角标等功能依赖此数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFestivalSourceDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("数据源", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "当前：$festivalSourceLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !festivalDownloading) {
                        festivalDownloading = true
                        scope.launch {
                            val result = festivalRepo.updateFromNetwork()
                            festivalDownloading = false
                            festivalStatus = festivalRepo.dataStatusText()
                            Toast.makeText(ctx, result.summaryText(), Toast.LENGTH_SHORT).show()
                            if (result.success) {
                                // 下载成功后：先校正快选时按「+1年」预估的节日日期，
                                // 再滚动已过期的跟随事件，最后刷新小组件
                                container.eventRepository.reanchorFestivalEstimates(festivalRepo)
                                container.eventRepository.rollForwardRepeating(festivalRepo)
                                WidgetRenderer.refreshAll(ctx)
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (festivalDownloading) "正在下载…" else "下载数据",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "范围：${java.time.LocalDate.now().year - 1}–${java.time.LocalDate.now().year + 1} 年 · $festivalStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
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
    }

    // 节日卡片角标 emoji 选择弹窗
    if (showBadgeEmojiDialog) {
        val emojiChoices = listOf(
            "☀️", "🌙", "⭐", "✨", "⚡",
            "🎉", "🎊", "🔥", "❤️", "🌸",
            "🍀", "🎯", "🎄", "🏖️", "🎁"
        )
        AlertDialog(
            onDismissRequest = { showBadgeEmojiDialog = false },
            title = { Text("节日卡片角标") },
            text = {
                Column {
                    emojiChoices.chunked(5).forEach { rowEmojis ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowEmojis.forEach { em ->
                                val isSel = em == homeBadgeEmoji
                                Text(
                                    em,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSel) MaterialTheme.colorScheme.primaryContainer
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .clickable {
                                            scope.launch {
                                                container.settingsRepository.setHomeBadgeEmoji(em)
                                            }
                                            showBadgeEmojiDialog = false
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBadgeEmojiDialog = false }) { Text("关闭") }
            }
        )
    }

    // 节假日数据源选择弹窗
    if (showFestivalSourceDialog) {
        val currentUrl = festivalRepo.sourceUrl()
        AlertDialog(
            onDismissRequest = { showFestivalSourceDialog = false },
            title = { Text("节假日数据源") },
            text = {
                Column {
                    WidgetEventOption(
                        title = "holiday-cn（默认）",
                        subtitle = "GitHub 开源数据 · jsDelivr CDN",
                        selected = currentUrl == FestivalRepository.SOURCE_HOLIDAY_CN
                    ) {
                        festivalRepo.setSourceUrl(FestivalRepository.SOURCE_HOLIDAY_CN)
                        festivalSourceLabel = festivalRepo.sourceLabel()
                        showFestivalSourceDialog = false
                    }
                    WidgetEventOption(
                        title = "timor.tech",
                        subtitle = "免费节假日 API",
                        selected = currentUrl == FestivalRepository.SOURCE_TIMOR
                    ) {
                        festivalRepo.setSourceUrl(FestivalRepository.SOURCE_TIMOR)
                        festivalSourceLabel = festivalRepo.sourceLabel()
                        showFestivalSourceDialog = false
                    }
                    WidgetEventOption(
                        title = "自定义 URL…",
                        subtitle = "URL 中用 {year} 占位年份；需返回 holiday-cn 或 timor 格式",
                        selected = currentUrl != FestivalRepository.SOURCE_HOLIDAY_CN &&
                            currentUrl != FestivalRepository.SOURCE_TIMOR
                    ) {
                        festivalCustomUrl = festivalRepo.sourceUrl()
                        showFestivalCustomInput = true
                        showFestivalSourceDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFestivalSourceDialog = false }) { Text("关闭") }
            }
        )
    }

    // 自定义数据源 URL 输入弹窗
    if (showFestivalCustomInput) {
        AlertDialog(
            onDismissRequest = { showFestivalCustomInput = false },
            title = { Text("自定义数据源 URL") },
            text = {
                OutlinedTextField(
                    value = festivalCustomUrl,
                    onValueChange = { festivalCustomUrl = it },
                    label = { Text("URL（{year} 为年份占位符）") },
                    supportingText = { Text("应用会自动识别 holiday-cn 与 timor.tech 两种数据格式") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = festivalCustomUrl.trim()
                    if (u.startsWith("http") && u.contains("{year}")) {
                        festivalRepo.setSourceUrl(u)
                        festivalSourceLabel = festivalRepo.sourceLabel()
                        showFestivalCustomInput = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showFestivalCustomInput = false }) { Text("取消") }
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
