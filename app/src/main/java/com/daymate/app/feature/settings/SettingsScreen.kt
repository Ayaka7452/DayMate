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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
    // 可自选缓存年份（当前年 ±3），默认去年/今年/明年
    var showFestivalYearsDialog by remember { mutableStateOf(false) }
    var festivalYears by remember { mutableStateOf(festivalRepo.selectedYears()) }
    var festivalYearDraft by remember { mutableStateOf<Set<Int>>(emptySet()) }
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
                "主页列表顶部卡片显示的内容。",
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
                Toast.makeText(ctx, "已恢复默认设置", Toast.LENGTH_SHORT).show()
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
                        "卡片右侧显示的表情符号",
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
                "主数据库保存在应用内部，无需存储权限。所选文件夹用于导出与恢复备份。",
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
                "备份位置：${StorageConfig.displayPath(StorageConfig.backupUri(ctx))}",
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
                        if (backupConfigured) "数据修改后自动备份到所选文件夹" else "需先选择备份文件夹",
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
                "周期管家与保险箱默认禁止截屏与录屏，也不会出现在最近任务缩略图中。",
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
                        if (allowScreenshotCycle) "已允许截屏与录屏，密码验证页仍受限制"
                        else "已阻止截屏、录屏与最近任务缩略图",
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
                        if (allowScreenshotVault) "已允许截屏与录屏，解锁与设密页仍受限制"
                        else "已阻止截屏、录屏与最近任务缩略图",
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
                "由应用从所选数据源下载并缓存到本机。跟随节日与节日角标等功能需要该数据。",
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
                    .clickable {
                        festivalYearDraft = festivalYears.toSet()
                        showFestivalYearsDialog = true
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("缓存年份", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "已选 ${FestivalRepository.yearsText(festivalYears)}",
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
                            val result = festivalRepo.updateFromNetwork(festivalYears)
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
                        "${FestivalRepository.yearsText(festivalYears)} · $festivalStatus",
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
                        subtitle = "跟随国务院通知发布 · GitHub 开源数据",
                        selected = currentUrl == FestivalRepository.SOURCE_HOLIDAY_CN
                    ) {
                        festivalRepo.setSourceUrl(FestivalRepository.SOURCE_HOLIDAY_CN)
                        festivalSourceLabel = festivalRepo.sourceLabel()
                        showFestivalSourceDialog = false
                    }
                    WidgetEventOption(
                        title = "自定义 URL…",
                        subtitle = "按数据结构自动识别，兼容主流节假日数据源",
                        selected = currentUrl != FestivalRepository.SOURCE_HOLIDAY_CN
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
                Column {
                    OutlinedTextField(
                        value = festivalCustomUrl,
                        onValueChange = { festivalCustomUrl = it },
                        label = { Text("URL") },
                        supportingText = { Text("含 {year} 占位符则按年下载；不含则下载整份文件后按年缓存") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "按结构自动识别，已兼容 holiday-cn、日期为键、节假日/工作日 Map、" +
                            "data.list 数组等主流格式；节日名会统一成规范写法。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = festivalCustomUrl.trim()
                    if (u.startsWith("http")) {
                        festivalRepo.setSourceUrl(u)
                        festivalSourceLabel = festivalRepo.sourceLabel()
                        showFestivalCustomInput = false
                    } else {
                        Toast.makeText(ctx, "请填写以 http 开头的地址", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showFestivalCustomInput = false }) { Text("取消") }
            }
        )
    }

    // 缓存年份选择弹窗（当前年 ±3，勾选后「下载数据」按所选年份逐一拉取）
    if (showFestivalYearsDialog) {
        val years = festivalRepo.selectableYears()
        AlertDialog(
            onDismissRequest = { showFestivalYearsDialog = false },
            title = { Text("缓存年份") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "勾选需要缓存的年份。次年放假安排一般在当年 11 月前后公布，" +
                            "未公布前下载会提示「尚未发布」，不会覆盖已有缓存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    val cached = remember(showFestivalYearsDialog) { festivalRepo.cachedYears().toSet() }
                    years.forEach { y ->
                        val checked = y in festivalYearDraft
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    festivalYearDraft = if (checked) {
                                        festivalYearDraft - y
                                    } else {
                                        festivalYearDraft + y
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text("$y 年", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (y in cached) "已缓存" else "未缓存",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val picked = festivalYearDraft.sorted()
                    if (picked.isNotEmpty()) {
                        festivalRepo.setSelectedYears(picked)
                        festivalYears = picked
                    } else {
                        Toast.makeText(ctx, "至少选择一个年份", Toast.LENGTH_SHORT).show()
                    }
                    showFestivalYearsDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showFestivalYearsDialog = false }) { Text("取消") }
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
 * 数据维护区块：**先扫描 → 发现问题才弹框 → 用户确认后修复**。
 *
 * 修复前的安全网：已配置本地备份文件夹时，会自动留一份 `daymate.db.bak` 快照；
 * 未配置则先弹出警告，让用户决定是先去设置备份还是继续。
 * 运行状态封装在本组件内部，避免给设置页主函数再堆一批 remember 变量。
 */
@Composable
private fun DataMaintenanceSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var repairing by remember { mutableStateOf(false) }
    var report by remember {
        mutableStateOf<com.ayaka7452.daymate.core.DbRepair.Report?>(null)
    }
    var result by remember {
        mutableStateOf<com.ayaka7452.daymate.core.DbRepair.RepairResult?>(null)
    }
    var showIssues by remember { mutableStateOf(false) }
    var showNoBackupWarning by remember { mutableStateOf(false) }

    val busy = scanning || repairing

    fun runRepair() {
        repairing = true
        scope.launch {
            val r = container.dbRepair.repair()
            result = r
            report = r.after ?: r.before
            repairing = false
            Toast.makeText(
                ctx,
                if (r.ok) {
                    if (r.snapshotCreated) "修复完成：已留快照 daymate.db.bak，并同步到备份"
                    else "修复完成，已同步到备份"
                } else {
                    "修复失败：${r.failureReason.orEmpty()}"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Text(
        "数据维护",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp)
    )
    Text(
        "检查数据库完整性与冗余数据。仅报告，不修改现有内容。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 4.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) {
                scanning = true
                result = null
                scope.launch {
                    val rep = container.dbRepair.diagnose()
                    report = rep
                    scanning = false
                    if (rep.hasProblems) {
                        showIssues = true
                    } else {
                        Toast.makeText(ctx, "检查完成，未发现问题", Toast.LENGTH_SHORT).show()
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
                when {
                    scanning -> "正在检查…"
                    repairing -> "正在修复…"
                    else -> "检查数据库"
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "检查完整性、碎片、字段使用与冗余数据，不修改任何内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    // 报告明细只在「确认后真的执行过修复」时展示（修复回执）。
    // 单纯体检没问题时只用一句 Toast 带过，不铺开报告——避免每次扫描都甩一大段文字。
    result?.let { MaintenanceReportCard(report = it.after ?: it.before, lastRepair = it) }

    // 扫描到问题才弹框列明细。仅有可修复问题时才给「立即修复」；
    // 只有提示型发现（冗余/异常数据，修复不会处理）则只给「知道了」。
    val scanned = report
    if (showIssues && scanned != null) {
        val fixable = scanned.hasFixableIssues
        AlertDialog(
            onDismissRequest = { showIssues = false },
            title = { Text(if (fixable) "发现可修复的问题" else "发现异常数据") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    scanned.issues.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (scanned.notices.isNotEmpty()) {
                        if (scanned.issues.isNotEmpty()) Spacer(Modifier.padding(vertical = 6.dp))
                        Text(
                            if (fixable) "以下项目不会被修复：" else "以下项目不会自动清理：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        scanned.notices.forEach {
                            Text(
                                "· $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    if (fixable) {
                        Spacer(Modifier.padding(vertical = 6.dp))
                        Text(
                            "修复仅执行无损维护：修正无效引用并回收碎片空间，不会删改任何数据。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                if (fixable) {
                    TextButton(onClick = {
                        showIssues = false
                        if (StorageConfig.backupUri(ctx) == null) showNoBackupWarning = true else runRepair()
                    }) { Text("立即修复") }
                } else {
                    TextButton(onClick = { showIssues = false }) { Text("好") }
                }
            },
            dismissButton = {
                if (fixable) {
                    TextButton(onClick = { showIssues = false }) { Text("取消") }
                }
            }
        )
    }

    // 未指定本地备份文件夹 → 先警告，用户可先去设置或坚持修复
    if (showNoBackupWarning) {
        AlertDialog(
            onDismissRequest = { showNoBackupWarning = false },
            title = { Text("未设置本地备份文件夹") },
            text = {
                Text(
                    "修复前将不会创建 daymate.db.bak 快照。\n\n" +
                        "如需保留快照，请先在「数据备份」中选择备份文件夹。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNoBackupWarning = false
                    runRepair()
                }) { Text("继续修复") }
            },
            dismissButton = {
                TextButton(onClick = { showNoBackupWarning = false }) { Text("取消") }
            }
        )
    }
}

/** 体检报告卡片：展示扫描结论；若刚执行过修复，则附上修复前后对比。 */
@Composable
private fun MaintenanceReportCard(
    report: com.ayaka7452.daymate.core.DbRepair.Report,
    lastRepair: com.ayaka7452.daymate.core.DbRepair.RepairResult?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        val after = lastRepair?.takeIf { it.ok }?.after
        if (lastRepair != null && !lastRepair.ok) {
            Text(
                "修复未完成：${lastRepair.failureReason ?: "未知原因"}。数据未受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (lastRepair != null && after != null) {
            ResultLine(
                "数据库大小",
                formatBytes(lastRepair.before.totalBytes) + " → " + formatBytes(after.totalBytes)
            )
            ResultLine(
                "已回收空间",
                formatBytes((lastRepair.before.totalBytes - after.totalBytes).coerceAtLeast(0))
            )
            ResultLine(
                "已修复无效引用",
                if (lastRepair.fixedDanglingRefs > 0) "${lastRepair.fixedDanglingRefs} 处" else "无需修复"
            )
            ResultLine(
                "已修复异常时间",
                if (lastRepair.fixedTimestamps > 0) "${lastRepair.fixedTimestamps} 条" else "无需修复"
            )
            ResultLine(
                "修复前快照",
                if (lastRepair.snapshotCreated) "已保存 daymate.db.bak" else "未创建（无本地备份）"
            )
        } else {
            ResultLine("数据库大小", formatBytes(report.totalBytes))
            ResultLine(
                "可回收空间",
                formatBytes(report.reclaimableBytes) + "（碎片 ${(report.freeRatio * 100).toInt()}%）"
            )
        }

        ResultLine(
            "结构完整性",
            if (report.integrityOk) "正常" else "异常：${report.integrityDetail.orEmpty()}"
        )
        ResultLine(
            "残留无用字段",
            if (report.zombieColumns.isEmpty()) "无" else report.zombieColumns.joinToString("、")
        )
        ResultLine("无效文件夹引用", if (report.danglingRefs > 0) "${report.danglingRefs} 处" else "无")

        // ===== 字段利用率：哪些字段在实际数据里从未被填过 =====
        Spacer(Modifier.padding(vertical = 4.dp))
        Text(
            "未使用的字段",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (report.unusedFields.isEmpty()) {
            Text("无，所有字段均有数据", style = MaterialTheme.typography.bodySmall)
        } else {
            report.unusedFields.forEach {
                Text(
                    "· ${it.tableLabel} · ${it.fieldLabel}（${it.total} 条记录均未填写）",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "以上字段均有对应功能，当前数据中未使用，不会被删除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // ===== 冗余数据：只提示，不自动清理 =====
        Spacer(Modifier.padding(vertical = 4.dp))
        Text(
            "冗余数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (report.redundancies.isEmpty()) {
            Text("无", style = MaterialTheme.typography.bodySmall)
        } else {
            report.redundancies.forEach {
                Text("· ${it.label}：${it.count} 处", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "以上项目仅作提示，不会自动清理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // ===== 现有数据行数 =====
        Spacer(Modifier.padding(vertical = 4.dp))
        Text(
            "现有数据（未修改）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            report.tables.joinToString(" · ") {
                it.label + " " + (if (it.rows < 0) "?" else it.rows.toString())
            },
            style = MaterialTheme.typography.bodySmall
        )
        val recycled = report.tables.sumOf { if (it.inRecycleBin < 0) 0L else it.inRecycleBin }
        if (recycled > 0) {
            Text(
                "回收站内另有 $recycled 条已删除条目，不做清理。可在「回收站」中处理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
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
