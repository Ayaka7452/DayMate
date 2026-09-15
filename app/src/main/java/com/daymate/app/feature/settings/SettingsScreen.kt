package com.ayaka7452.daymate.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.data.festival.FestivalRepository
import com.ayaka7452.daymate.feature.common.EmojiCatalog
import com.ayaka7452.daymate.feature.common.EmojiPicker
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
    val colorMode by container.settingsRepository.colorMode
        .collectAsState(initial = "white")
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
    val allowScreenshotCycle by container.settingsRepository.allowScreenshotCycle
        .collectAsState(initial = false)
    val allowScreenshotVault by container.settingsRepository.allowScreenshotVault
        .collectAsState(initial = false)
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
    // 备份子页是同 Activity 内的状态切换：返回手势先回设置主页，而不是退出设置
    androidx.activity.compose.BackHandler(enabled = showSetup) { showSetup = false }
    // 备份子页 ⇄ 设置主页淡入淡出
    Crossfade(targetState = showSetup, label = "settings_backup") { setup ->
        if (setup) {
            StorageSetupBody(
                title = "数据备份",
                showBack = true,
                onBack = { showSetup = false }
            )
        } else {

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
    // 配色选项：value / 短标签（横排显示）/ 色板色（system 特殊渲染为四色圆）
    val colorOptions: List<Triple<String, String, Color>> = listOf(
        Triple("white", "默认", Color(0xFF00668C)),
        Triple("system", "自动", Color.Transparent),
        Triple("blue", "蓝", Color(0xFF1565C0)),
        Triple("green", "绿", Color(0xFF2E7D32)),
        Triple("orange", "橙", Color(0xFFE65100)),
        Triple("purple", "紫", Color(0xFF6A1B9A))
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

            // ===== 配色（横排色板，选中带圈） =====
            Text(
                "配色",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                colorOptions.forEach { (value, label, swatch) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                scope.launch { container.settingsRepository.setColorMode(value) }
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ColorSwatchDot(value = value, selected = colorMode == value, accent = swatch)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (colorMode == value) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
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

            // ===== 隐私：截图限制 =====
            Text(
                "隐私",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "周期管家与保险箱默认禁止截屏/录屏，也不会出现在最近任务缩略图里。需要截图时可在下面放开。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("周期管家允许截图", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (allowScreenshotCycle) "已允许截屏/录屏；密码验证页仍会阻止"
                        else "已阻止截屏/录屏与最近任务缩略图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = allowScreenshotCycle,
                    onCheckedChange = {
                        scope.launch { container.settingsRepository.setAllowScreenshotCycle(it) }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("保险箱允许截图", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (allowScreenshotVault) "已允许截屏/录屏；解锁与设密页仍会阻止"
                        else "已阻止截屏/录屏与最近任务缩略图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = allowScreenshotVault,
                    onCheckedChange = {
                        scope.launch { container.settingsRepository.setAllowScreenshotVault(it) }
                    }
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
                        run {
                            val y = java.time.LocalDate.now().year
                            "范围：${y - 1}–${y + 1} 年 · $festivalStatus"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            // ===== 数据维护（体检 + 无损修复 + 回收碎片，完成后同步各备份点） =====
            DataMaintenanceSection(container = container)

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

    // 节日卡片角标 emoji 选择弹窗（与文件夹图标共用统一选择器：常用集 + 「更多」全量）
    if (showBadgeEmojiDialog) {
        AlertDialog(
            onDismissRequest = { showBadgeEmojiDialog = false },
            title = { Text("节日卡片角标") },
            text = {
                EmojiPicker(
                    selected = homeBadgeEmoji,
                    onSelect = { em ->
                        scope.launch {
                            container.settingsRepository.setHomeBadgeEmoji(em)
                        }
                        showBadgeEmojiDialog = false
                    },
                    presets = EmojiCatalog.festivalPresets
                )
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
    }
}

@Composable
private fun ColorSwatchDot(value: String, selected: Boolean, accent: Color) {
    val ring = if (selected) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = ring,
                shape = CircleShape
            )
            .padding(3.dp)
            .clip(CircleShape)
    ) {
        if (value == "system") {
            // 「自动」= 四色拼圆，表达跟随壁纸的动态取色
            Canvas(Modifier.fillMaxSize()) {
                val quad = listOf(
                    Color(0xFF1565C0), Color(0xFF2E7D32),
                    Color(0xFFE65100), Color(0xFF6A1B9A)
                )
                quad.forEachIndexed { i, c ->
                    drawArc(
                        color = c,
                        startAngle = 90f * i - 90f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent)
            )
        }
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

/**
 * 数据维护区块：一键体检 + 无损修复数据库，完成后把结果同步到各备份点。
 * 运行状态封装在本组件内部，避免给设置页主函数再堆一批 remember 变量。
 */
@Composable
private fun DataMaintenanceSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var result by remember {
        mutableStateOf<com.ayaka7452.daymate.core.DbRepair.RepairResult?>(null)
    }

    Text(
        "数据维护",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp)
    )
    Text(
        "为长期使用的数据库做体检与整理：回收被反复增删撑大的空间、修复指向已删除文件夹的无效引用。" +
            "全程无损，不会删除任何事件、文件夹或保险箱内容。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 4.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !running) {
                running = true
                scope.launch {
                    val r = container.dbRepair.repair()
                    result = r
                    running = false
                    Toast.makeText(
                        ctx,
                        if (r.ok) "修复完成，已同步到备份" else "修复失败：${r.failureReason.orEmpty()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Sync, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (running) "正在修复…" else "扫描并修复",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "体检 → 修复无效引用 → 回收空间 → 同步备份",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    result?.let { RepairResultCard(it) }
}

/** 修复结果卡片：展示修复前后的占用对比与各项体检结论。 */
@Composable
private fun RepairResultCard(r: com.ayaka7452.daymate.core.DbRepair.RepairResult) {
    val after = r.after
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        if (!r.ok || after == null) {
            Text(
                "修复未完成：${r.failureReason ?: "未知原因"}（数据未受影响）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            val saved = (r.before.totalBytes - after.totalBytes).coerceAtLeast(0)
            ResultLine("数据库占用", formatBytes(r.before.totalBytes) + " → " + formatBytes(after.totalBytes))
            ResultLine("已回收空间", formatBytes(saved))
            ResultLine("结构完整性", if (after.integrityOk) "正常" else "异常：${after.integrityDetail.orEmpty()}")
            ResultLine("残留无用字段", if (after.zombieColumns.isEmpty()) "无" else after.zombieColumns.joinToString("、"))
            ResultLine("修复无效引用", if (r.fixedDanglingRefs > 0) "${r.fixedDanglingRefs} 处" else "无需修复")
            ResultLine("修补异常时间", if (r.fixedTimestamps > 0) "${r.fixedTimestamps} 条" else "无需修复")

            // ===== 字段利用率：哪些字段在你的实际数据里从未被填过 =====
            Spacer(Modifier.padding(vertical = 4.dp))
            Text(
                "从未填过值的字段",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (after.unusedFields.isEmpty()) {
                Text("无——所有字段都有数据", style = MaterialTheme.typography.bodySmall)
            } else {
                after.unusedFields.forEach {
                    Text(
                        "· ${it.tableLabel}的「${it.fieldLabel}」（共 ${it.total} 条，0 条填过）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "这些字段都有对应功能，只是你的数据里没用过，不属于垃圾列，不会自动删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // ===== 冗余数据：只提示，不自动清理 =====
            Spacer(Modifier.padding(vertical = 4.dp))
            Text(
                "数据冗余检查",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (after.redundancies.isEmpty()) {
                Text("未发现冗余数据", style = MaterialTheme.typography.bodySmall)
            } else {
                after.redundancies.forEach {
                    Text("· ${it.label}：${it.count} 处", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "以上仅为提示，未做任何自动清理（避免误删你要保留的数据）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.padding(vertical = 4.dp))
            Text(
                "现有数据（未做任何删改）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                after.tables.joinToString(" · ") {
                    it.label + " " + (if (it.rows < 0) "?" else it.rows.toString())
                },
                style = MaterialTheme.typography.bodySmall
            )
            val recycled = after.tables.sumOf { if (it.inRecycleBin < 0) 0L else it.inRecycleBin }
            if (recycled > 0) {
                Text(
                    "回收站内另有 $recycled 条已删除条目，按你的要求保持原样（如需清理可在回收站中操作）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (after.badTimestampRows > 0) {
                Text(
                    "注：修补异常时间只针对 1970 年这类不可能的值，正常时间未改动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 结果卡片里的一行「标签 — 值」。 */
@Composable
private fun ResultLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
