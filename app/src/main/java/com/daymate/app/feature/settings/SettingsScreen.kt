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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.core.i18n.AppLanguage
import com.ayaka7452.daymate.core.i18n.LocaleWrap
import com.ayaka7452.daymate.core.i18n.Tr
import com.ayaka7452.daymate.core.update.UpdateCheckResult
import com.ayaka7452.daymate.core.update.UpdateChecker
import com.ayaka7452.daymate.core.update.UpdateInfo
import com.ayaka7452.daymate.core.update.UpdatePrompt
import com.ayaka7452.daymate.data.festival.FestivalRegion
import com.ayaka7452.daymate.data.festival.FestivalRepository
import com.ayaka7452.daymate.feature.common.EmojiCatalog
import com.ayaka7452.daymate.feature.common.EmojiPicker
import com.ayaka7452.daymate.feature.common.UpdateAvailableDialog
import com.ayaka7452.daymate.feature.common.rememberUpdateStarter
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
    // 明日补班预告开关（默认开）：只关「预告」，当天补班横幅不受影响
    val makeupHintEnabled by container.settingsRepository.makeupHintEnabled
        .collectAsState(initial = true)

    // 节假日数据：不内置离线数据，由应用从可配置的数据源下载并缓存
    val festivalRepo = container.festivalRepository
    var festivalSourceLabel by remember { mutableStateOf(festivalRepo.sourceLabel()) }
    var festivalStatus by remember { mutableStateOf(festivalRepo.dataStatusText()) }
    var festivalDownloading by remember { mutableStateOf(false) }
    var showFestivalSourceDialog by remember { mutableStateOf(false) }
    var showFestivalCustomInput by remember { mutableStateOf(false) }
    var festivalCustomUrl by remember { mutableStateOf(festivalRepo.sourceUrl()) }
    // 可自选缓存年份（去年 ～ 三年后），默认去年/今年；存的是相对今年的偏移，随年份自动滑动
    var showFestivalYearsDialog by remember { mutableStateOf(false) }
    var festivalYears by remember { mutableStateOf(festivalRepo.selectedYears()) }
    var festivalOffsetDraft by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var autoUpdateCurrent by remember { mutableStateOf(festivalRepo.autoUpdateCurrent()) }
    var showBadgeEmojiDialog by remember { mutableStateOf(false) }

    // ===== 检查更新 =====
    // 默认开启（启动时按 24h 节流查一次，有新版本弹窗）；关掉后不发起任何请求，
    // 但下面的「立即检查」仍可手动触发——用户关掉的通常是「自动提醒」，不是「这个功能」。
    val updateCheckEnabled by container.settingsRepository.updateCheckEnabled
        .collectAsState(initial = true)
    val updateCurrentVersion = remember { UpdateChecker.currentVersion(ctx) }
    var updateChecking by remember { mutableStateOf(false) }
    /** 「立即检查」的结果文案；null = 还没查过（显示当前版本号）。 */
    var updateStatus by remember { mutableStateOf<String?>(null) }
    /** 手动检查发现的新版本 → 复用主页那套更新弹窗。 */
    var updateFound by remember { mutableStateOf<UpdateInfo?>(null) }
    val startUpdate = rememberUpdateStarter()

    // 节日数据变更信号（切源 / 下载完成 / 启动时自动补下都会 +1）：在这台页面上重新读一次
    // 源名与缓存状态。没有它，切完区域自动下载完成后这里仍显示「未下载」，直到退出重进。
    val festivalVersion by festivalRepo.version.collectAsState()
    LaunchedEffect(festivalVersion) {
        festivalSourceLabel = festivalRepo.sourceLabel()
        festivalStatus = festivalRepo.dataStatusText()
    }

    // ===== 界面语言 =====
    // 存储在 SharedPreferences（不是 DataStore）：attachBaseContext 是同步调用，
    // 必须在任何 IO 之前就知道该用哪个 Locale，否则首帧会先按旧语言渲染再重建、肉眼可见闪烁。
    val appLanguage by container.localeStore.language
        .collectAsState(initial = container.localeStore.current())
    var showLanguageDialog by remember { mutableStateOf(false) }
    /** 待确认的节日源切换：语言与数据源区域不符时先问一句，两个入口（切语言 / 点提示）共用。 */
    var pendingRegionSwitch by remember { mutableStateOf<FestivalRegion?>(null) }
    /** 上面那次切换是否由「换语言」引发——决定弹窗说「同时切换」还是「切换」。 */
    var pendingFromLanguage by remember { mutableStateOf(false) }

    // 数据备份：选择文件夹仅作 SAF 导出/导入目标，不需要任何存储权限（全屏覆盖）
    var showSetup by remember { mutableStateOf(false) }
    // 备份子页是同 Activity 内的状态切换：返回手势先回设置主页，而不是退出设置
    androidx.activity.compose.BackHandler(enabled = showSetup) { showSetup = false }
    // 备份子页 ⇄ 设置主页淡入淡出
    Crossfade(targetState = showSetup, label = "settings_backup") { setup ->
        if (setup) {
            StorageSetupBody(
                title = stringResource(R.string.settings_data_backup),
                showBack = true,
                onBack = { showSetup = false }
            )
        } else {

    val themeOptions = listOf(
        "system" to stringResource(R.string.settings_theme_system),
        "light" to stringResource(R.string.settings_theme_light),
        "dark" to stringResource(R.string.settings_theme_dark)
    )
    val sortOptions = listOf(
        "remaining_asc" to stringResource(R.string.settings_sort_remaining_asc),
        "remaining_desc" to stringResource(R.string.settings_sort_remaining_desc),
        "manual" to stringResource(R.string.settings_sort_manual)
    )
    val homeCardOptions = listOf(
        "festival" to stringResource(R.string.settings_home_card_festival),
        "event" to stringResource(R.string.settings_home_card_event),
        "off" to stringResource(R.string.common_close)
    )
    // 配色选项：value / 短标签（横排显示）/ 色板色（system 特殊渲染为四色圆）
    val colorOptions: List<Triple<String, String, Color>> = listOf(
        Triple("white", stringResource(R.string.settings_color_default), Color(0xFF00668C)),
        Triple("system", stringResource(R.string.settings_color_auto), Color.Transparent),
        Triple("blue", stringResource(R.string.settings_color_blue), Color(0xFF1565C0)),
        Triple("green", stringResource(R.string.settings_color_green), Color(0xFF2E7D32)),
        Triple("orange", stringResource(R.string.settings_color_orange), Color(0xFFE65100)),
        Triple("purple", stringResource(R.string.settings_color_purple), Color(0xFF6A1B9A))
    )

    /**
     * 目标语言建议的节日源区域。
     *
     * 「自动检测」必须用**未包装的 Application context** 取系统语言：Activity 的 base 已被
     * attachBaseContext 按旧设置包过，从它读只会读回旧语言。显式选的语言则直接走
     * [LocaleWrap.resolveLocale] 拿 Locale.code —— 不能拿 `AppLanguage.tag` 去比，
     * `"zh-Hans"` 这类带子标签的串在 [FestivalRegion.forLanguage] 里匹配不到 `"zh"`，
     * 会一路落到 else 分支误判成美国。
     */
    fun regionForLanguage(lang: AppLanguage): FestivalRegion {
        val locale = if (lang == AppLanguage.AUTO) {
            LocaleWrap.effective(ctx.applicationContext)
        } else {
            LocaleWrap.resolveLocale(lang, java.util.Locale.getDefault())
        }
        return FestivalRegion.forLanguage(locale.language)
    }

    /** 语言是 per-Activity 的（attachBaseContext 只在创建时跑），必须重建整个任务栈才全局生效。 */
    fun restartForLanguage() {
        val intent = android.content.Intent(ctx, com.ayaka7452.daymate.MainActivity::class.java).apply {
            addFlags(
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        ctx.startActivity(intent)
        (ctx as? android.app.Activity)?.finish()
    }

    /**
     * 切换节日数据源。
     *
     * 换源和下载**必须成对发生**：只把 URL 换掉，列表里还是上一个源的数据，
     * 用户得自己再点一次「下载数据」、再重启 App 才看到新国家的节日。
     * 下载挂在容器的应用级作用域上，因此设置页在确认后立刻重启任务栈（换语言那一支）
     * 也打断不了它。
     */
    fun switchRegionNow(region: FestivalRegion) {
        container.switchFestivalRegion(region)
        festivalSourceLabel = festivalRepo.sourceLabel()
    }

    fun chooseLanguage(lang: AppLanguage) {
        container.localeStore.set(lang)
        val target = regionForLanguage(lang)
        val current = festivalRepo.regionOfCurrentSource()
        // 只在「当前用的是内置区域源」且与目标区域不符时询问。
        // 用户手填的 URL 是他自己选的，不擅自替换。
        if (current != null && current != target) {
            showLanguageDialog = false
            pendingFromLanguage = true
            pendingRegionSwitch = target
        } else {
            restartForLanguage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            // ===== 语言（放最前：切换后会重建整个任务栈，是最「重」的一项设置） =====
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_lang_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Language, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    // 每种语言用它自己的写法（中文/粵語/日本語/한국어）：用户即使看不懂当前界面语言，
                    // 也一定能认出母语那一项——与各系统设置的通行做法一致。
                    if (appLanguage == AppLanguage.AUTO) stringResource(R.string.settings_lang_auto)
                    else appLanguage.nativeName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            Text(stringResource(R.string.settings_theme_section), style = MaterialTheme.typography.titleMedium)
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
                stringResource(R.string.settings_color_section),
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
                stringResource(R.string.settings_default_sort_section),
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
                stringResource(R.string.settings_home_top_card_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                stringResource(R.string.settings_home_top_card_desc),
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
                Toast.makeText(ctx, ctx.getString(R.string.settings_restored_default), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.settings_restore_default)) }

            // 节日卡片右侧角标 emoji（卡片只显示放假节日，不需要「休/班」标记）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBadgeEmojiDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_badge_emoji), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_badge_emoji_desc),
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
                stringResource(R.string.settings_data_backup),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                stringResource(R.string.settings_backup_desc),
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
                Text(stringResource(R.string.settings_data_backup), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.settings_backup_location, StorageConfig.displayPath(StorageConfig.backupUri(ctx))),
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
                    Text(stringResource(R.string.settings_auto_backup_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (backupConfigured) stringResource(R.string.settings_auto_backup_desc_on) else stringResource(R.string.settings_auto_backup_desc_off),
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
                stringResource(R.string.settings_privacy_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                stringResource(R.string.settings_privacy_desc),
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
                    Text(stringResource(R.string.settings_cycle_screenshot_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (allowScreenshotCycle) stringResource(R.string.settings_cycle_screenshot_on) else stringResource(R.string.settings_cycle_screenshot_off),
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
                    Text(stringResource(R.string.settings_vault_screenshot_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (allowScreenshotVault) stringResource(R.string.settings_vault_screenshot_on) else stringResource(R.string.settings_cycle_screenshot_off),
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
                stringResource(R.string.settings_festival_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                stringResource(R.string.settings_festival_desc),
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
                    Text(stringResource(R.string.settings_source_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_source_current, festivalSourceLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            // 语言与当前数据源区域不符时给条小字（点它切换，仍要过确认）。
            // 只在「当前用的确实是内置区域源」时才提示——用户手填的 URL 是他自己选的，
            // 与 chooseLanguage 里「不擅自替换自定义源」保持同一口径，否则自定义源用户会被一直絮叨。
            // 以 festivalSourceLabel 为 remember 的 key：换源时该 state 会变，提示随之重新判定。
            val suggestedRegion = regionForLanguage(appLanguage)
            val currentRegion = remember(festivalSourceLabel) { festivalRepo.regionOfCurrentSource() }
            if (currentRegion != null && currentRegion != suggestedRegion) {
                Text(
                    stringResource(R.string.settings_source_suggest, Tr.s(suggestedRegion.labelRes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            pendingFromLanguage = false
                            pendingRegionSwitch = suggestedRegion
                        }
                        .padding(vertical = 6.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        festivalOffsetDraft = festivalRepo.selectedOffsets().toSet()
                        showFestivalYearsDialog = true
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.settings_cache_years_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_years_selected, FestivalRepository.yearsText(festivalYears)),
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
                        if (festivalDownloading) stringResource(R.string.settings_downloading) else stringResource(R.string.settings_download_data),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_years_status, FestivalRepository.yearsText(festivalYears), festivalStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_auto_update_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (autoUpdateCurrent) stringResource(R.string.settings_auto_update_on) else stringResource(R.string.settings_auto_update_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = autoUpdateCurrent,
                    onCheckedChange = {
                        autoUpdateCurrent = it
                        festivalRepo.setAutoUpdateCurrent(it)
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_makeup_hint), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_makeup_hint_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = makeupHintEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setMakeupHintEnabled(enabled) }
                        // 立即刷新小组件：关掉时角标当天就消失，不用等下次重建
                        WidgetRenderer.refreshAll(ctx)
                    }
                )
            }

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            // ===== 数据维护（体检 + 无损修复 + 回收碎片，完成后同步各备份点） =====
            DataMaintenanceSection(container = container)

            Spacer(Modifier.padding(vertical = 8.dp))
            HorizontalDivider()

            // ===== 检查更新（默认开；关掉后启动时不再发请求，此处仍可手动检查） =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.update_check_section),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (updateCheckEnabled) stringResource(R.string.update_check_on)
                        else stringResource(R.string.update_check_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = updateCheckEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { container.settingsRepository.setUpdateCheckEnabled(enabled) }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !updateChecking) {
                        updateChecking = true
                        updateStatus = null
                        scope.launch {
                            when (val result = UpdatePrompt.checkNow(ctx)) {
                                is UpdateCheckResult.Available -> {
                                    updateFound = result.info
                                    updateStatus = Tr.s(R.string.update_status_found, result.info.version)
                                }

                                UpdateCheckResult.Latest ->
                                    updateStatus = Tr.s(R.string.update_status_latest)

                                UpdateCheckResult.Failed ->
                                    updateStatus = Tr.s(R.string.update_status_failed)
                            }
                            updateChecking = false
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.update_check_now),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (updateChecking) stringResource(R.string.update_status_checking)
                        else updateStatus ?: stringResource(
                            R.string.update_status_current, updateCurrentVersion
                        ),
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
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    // 手动检查到新版本 → 复用主页那套更新弹窗（「稍后」在这里只是关掉弹窗，
    // 不写「已忽略版本」——用户是主动来查的，下次进设置再点一次仍应看到提醒）
    updateFound?.let { info ->
        UpdateAvailableDialog(
            info = info,
            currentVersion = updateCurrentVersion,
            onUpdate = {
                updateFound = null
                startUpdate(info)
            },
            onLater = { updateFound = null }
        )
    }

    // 语言选择弹窗
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_lang_dialog_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chooseLanguage(lang) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLanguage == lang,
                                onClick = { chooseLanguage(lang) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (lang == AppLanguage.AUTO) stringResource(R.string.settings_lang_auto)
                                else lang.nativeName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            // M3 的 AlertDialog：带 confirmButton 的那个重载里它是必填参数，
            // 只给 title/text/dismissButton 会两个重载都不匹配（编译器报 candidates is applicable）。
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.common_done))
                }
            }
        )
    }

    // 节日源切换确认（切语言时自动问 / 点节日区小字时问，共用同一套文案）
    val regionToSwitch = pendingRegionSwitch
    if (regionToSwitch != null) {
        val regionName = Tr.s(regionToSwitch.labelRes)
        /**
         * 关掉这个确认框。
         *
         * 语言在 [chooseLanguage] 里**已经写进存储**了，只是还没重建界面——所以只要这次
         * 弹框是由换语言触发的，无论用户是确认还是取消，都必须重启任务栈把语言生效。
         * 早先「点框外重启、点取消不重启」两种出口行为不一致，取消后界面会一直是旧语言，
         * 直到下次冷启动才悄悄变过去。
         */
        fun closeRegionDialog() {
            val needRestart = pendingFromLanguage
            pendingRegionSwitch = null
            pendingFromLanguage = false
            if (needRestart) restartForLanguage()
        }
        /**
         * 这里**刻意不用 `stringResource` 而用 `Tr.s`**。
         *
         * 由「换语言」触发的这一支里，语言在 [chooseLanguage] 就写进存储了，只是 Activity 还没重建——
         * 而 `stringResource` 读的是当前 Activity 的 base context（attachBaseContext 早就包好了旧语言），
         * 于是会出现「界面刚切成 English，却弹出一个中文的节日源确认框」这种自相矛盾。
         * `Tr.s` 每次都按存储里的语言重新取词，拿到的正是用户刚选的那门语言。
         *
         * 顺带把 [regionName] 也统一走 `Tr.s`（它本来就是），避免出现「标题英文、地区名中文」的混排。
         */
        AlertDialog(
            onDismissRequest = { closeRegionDialog() },
            title = {
                Text(
                    Tr.s(
                        if (pendingFromLanguage) R.string.settings_lang_festival_title
                        else R.string.settings_source_switch_title
                    )
                )
            },
            text = { Text(Tr.s(R.string.settings_source_switch_msg, regionName)) },
            confirmButton = {
                TextButton(onClick = {
                    switchRegionNow(regionToSwitch)
                    pendingRegionSwitch = null
                    pendingFromLanguage = false
                    // 由换语言触发的这一支必须重启，否则语言不生效（两个出口行为要一致）
                    restartForLanguage()
                }) { Text(Tr.s(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { closeRegionDialog() }) {
                    Text(Tr.s(R.string.common_cancel))
                }
            }
        )
    }

    // 节日卡片角标 emoji 选择弹窗（与文件夹图标共用统一选择器：常用集 + 「更多」全量）
    if (showBadgeEmojiDialog) {
        AlertDialog(
            onDismissRequest = { showBadgeEmojiDialog = false },
            title = { Text(stringResource(R.string.settings_badge_emoji)) },
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
                TextButton(onClick = { showBadgeEmojiDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    // 节假日数据源选择弹窗：四个内置区域 + 自定义 URL。
    // 副标题显示「这个源已经缓存了哪些年份」——切换区域前就能看出哪些源是现成可用的，
    // 而这正是分源缓存存在的意义（切回去秒开，不用重下）。
    if (showFestivalSourceDialog) {
        val currentRegion = festivalRepo.regionOfCurrentSource()
        AlertDialog(
            onDismissRequest = { showFestivalSourceDialog = false },
            title = { Text(stringResource(R.string.settings_source_dialog_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FestivalRegion.entries.forEach { region ->
                        val cached = remember(showFestivalSourceDialog) {
                            festivalRepo.cachedYearsOf(region.name)
                        }
                        WidgetEventOption(
                            title = Tr.s(region.labelRes),
                            subtitle = if (cached.isEmpty()) {
                                stringResource(R.string.festival_status_none)
                            } else {
                                stringResource(
                                    R.string.festival_status_cached,
                                    FestivalRepository.yearsText(cached)
                                )
                            },
                            selected = currentRegion == region
                        ) {
                            // 选中即切换 + 自动下载新源数据，不用再手动点一次「下载数据」
                            switchRegionNow(region)
                            showFestivalSourceDialog = false
                        }
                    }
                    WidgetEventOption(
                        title = stringResource(R.string.settings_custom_url),
                        subtitle = stringResource(R.string.settings_source_custom_subtitle),
                        selected = currentRegion == null
                    ) {
                        festivalCustomUrl = festivalRepo.sourceUrl()
                        showFestivalCustomInput = true
                        showFestivalSourceDialog = false
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_region_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFestivalSourceDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    // 自定义数据源 URL 输入弹窗
    if (showFestivalCustomInput) {
        AlertDialog(
            onDismissRequest = { showFestivalCustomInput = false },
            title = { Text(stringResource(R.string.settings_custom_source_url)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = festivalCustomUrl,
                        onValueChange = { festivalCustomUrl = it },
                        label = { Text("URL") },
                        supportingText = { Text(stringResource(R.string.settings_custom_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_custom_url_formats),
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
                        // 与内置区域同一套：换源即拉数据，别让用户自己再点一次「下载数据」
                        container.downloadFestivalData()
                        showFestivalCustomInput = false
                    } else {
                        Toast.makeText(ctx, ctx.getString(R.string.settings_url_http_hint), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFestivalCustomInput = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 缓存年份选择弹窗（去年 ～ 三年后）。勾选存的是「相对今年的偏移」，所以年份会自动滑动：
    // 今年选的「去年 + 今年」，到明年就变成「今年 + 明年」，永远不会出现「当年没数据」。
    if (showFestivalYearsDialog) {
        val thisYear = java.time.LocalDate.now().year
        val years = festivalRepo.selectableYears()
        AlertDialog(
            onDismissRequest = { showFestivalYearsDialog = false },
            title = { Text(stringResource(R.string.settings_cache_years_section)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.settings_years_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    val cached = remember(showFestivalYearsDialog) { festivalRepo.cachedYears().toSet() }
                    years.forEach { y ->
                        val offset = y - thisYear
                        val required = offset == 0
                        val checked = required || offset in festivalOffsetDraft
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !required) {
                                    festivalOffsetDraft = if (checked) {
                                        festivalOffsetDraft - offset
                                    } else {
                                        festivalOffsetDraft + offset
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !required)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_year_n, y), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                (if (y in cached) stringResource(R.string.settings_cached) else stringResource(R.string.settings_not_cached)) +
                                    (if (required) stringResource(R.string.settings_required_tag) else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 写入的是偏移；今年（偏移 0）由 setSelectedOffsets 强制保留
                    festivalRepo.setSelectedOffsets(festivalOffsetDraft)
                    festivalYears = festivalRepo.selectedYears()
                    showFestivalYearsDialog = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFestivalYearsDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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
                    if (r.snapshotCreated) ctx.getString(R.string.settings_repair_done_snapshot)
                    else ctx.getString(R.string.settings_repair_done)
                } else {
                    ctx.getString(R.string.settings_repair_failed, r.failureReason.orEmpty())
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Text(
        stringResource(R.string.settings_maintenance_section),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp)
    )
    Text(
        stringResource(R.string.settings_maintenance_desc),
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
                        Toast.makeText(ctx, ctx.getString(R.string.settings_check_ok), Toast.LENGTH_SHORT).show()
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
                    scanning -> stringResource(R.string.settings_scanning)
                    repairing -> stringResource(R.string.settings_repairing)
                    else -> stringResource(R.string.settings_check_db)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(R.string.settings_check_desc),
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
            title = { Text(if (fixable) stringResource(R.string.settings_issues_fixable) else stringResource(R.string.settings_issues_anomaly)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    scanned.issues.forEach {
                        Text(stringResource(R.string.settings_bullet, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (scanned.notices.isNotEmpty()) {
                        if (scanned.issues.isNotEmpty()) Spacer(Modifier.padding(vertical = 6.dp))
                        Text(
                            if (fixable) stringResource(R.string.settings_not_fixed) else stringResource(R.string.settings_not_auto_cleaned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        scanned.notices.forEach {
                            Text(
                                stringResource(R.string.settings_bullet, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    if (fixable) {
                        Spacer(Modifier.padding(vertical = 6.dp))
                        Text(
                            stringResource(R.string.settings_repair_lossless),
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
                    }) { Text(stringResource(R.string.settings_repair_now)) }
                } else {
                    TextButton(onClick = { showIssues = false }) { Text(stringResource(R.string.common_ok)) }
                }
            },
            dismissButton = {
                if (fixable) {
                    TextButton(onClick = { showIssues = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        )
    }

    // 未指定本地备份文件夹 → 先警告，用户可先去设置或坚持修复
    if (showNoBackupWarning) {
        AlertDialog(
            onDismissRequest = { showNoBackupWarning = false },
            title = { Text(stringResource(R.string.settings_no_backup_folder)) },
            text = {
                Text(
                    stringResource(R.string.settings_no_backup_hint)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNoBackupWarning = false
                    runRepair()
                }) { Text(stringResource(R.string.settings_continue_repair)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoBackupWarning = false }) { Text(stringResource(R.string.common_cancel)) }
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
                stringResource(
                    R.string.settings_repair_incomplete,
                    lastRepair.failureReason ?: stringResource(R.string.settings_unknown_reason)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (lastRepair != null && after != null) {
            ResultLine(
                stringResource(R.string.settings_db_size),
                formatBytes(lastRepair.before.totalBytes) + " → " + formatBytes(after.totalBytes)
            )
            ResultLine(
                stringResource(R.string.settings_reclaimed_space),
                formatBytes((lastRepair.before.totalBytes - after.totalBytes).coerceAtLeast(0))
            )
            ResultLine(
                stringResource(R.string.settings_fixed_dangling),
                if (lastRepair.fixedDanglingRefs > 0) stringResource(R.string.settings_unit_places, lastRepair.fixedDanglingRefs) else stringResource(R.string.settings_no_repair_needed)
            )
            ResultLine(
                stringResource(R.string.settings_fixed_timestamps),
                if (lastRepair.fixedTimestamps > 0) stringResource(R.string.settings_unit_items, lastRepair.fixedTimestamps) else stringResource(R.string.settings_no_repair_needed)
            )
            ResultLine(
                stringResource(R.string.settings_pre_repair_snapshot),
                if (lastRepair.snapshotCreated) stringResource(R.string.settings_snapshot_saved) else stringResource(R.string.settings_snapshot_not_created)
            )
        } else {
            ResultLine(stringResource(R.string.settings_db_size), formatBytes(report.totalBytes))
            ResultLine(
                stringResource(R.string.settings_reclaimable_space),
                formatBytes(report.reclaimableBytes) + stringResource(R.string.settings_fragment_ratio, (report.freeRatio * 100).toInt())
            )
        }

        ResultLine(
            stringResource(R.string.settings_integrity),
            if (report.integrityOk) stringResource(R.string.settings_normal) else stringResource(R.string.settings_abnormal, report.integrityDetail.orEmpty())
        )
        ResultLine(
            stringResource(R.string.settings_zombie_fields),
            if (report.zombieColumns.isEmpty()) stringResource(R.string.common_none) else report.zombieColumns.joinToString("、")
        )
        ResultLine(stringResource(R.string.settings_dangling_refs), if (report.danglingRefs > 0) stringResource(R.string.settings_unit_places, report.danglingRefs) else stringResource(R.string.common_none))

        // ===== 字段利用率：哪些字段在实际数据里从未被填过 =====
        Spacer(Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(R.string.settings_unused_fields),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (report.unusedFields.isEmpty()) {
            Text(stringResource(R.string.settings_all_fields_used), style = MaterialTheme.typography.bodySmall)
        } else {
            report.unusedFields.forEach {
                Text(
                    stringResource(R.string.settings_unused_field_item, Tr.s(it.tableRes), Tr.s(it.fieldRes), it.total),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                stringResource(R.string.settings_fields_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // ===== 冗余数据：只提示，不自动清理 =====
        Spacer(Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(R.string.settings_redundancy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (report.redundancies.isEmpty()) {
            Text(stringResource(R.string.common_none), style = MaterialTheme.typography.bodySmall)
        } else {
            report.redundancies.forEach {
                Text(stringResource(R.string.settings_redundancy_item, Tr.s(it.labelRes), it.count), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.settings_redundancy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // ===== 现有数据行数 =====
        Spacer(Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(R.string.settings_existing_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            report.tables.joinToString(" · ") {
                Tr.s(it.labelRes) + " " + (if (it.rows < 0) "?" else it.rows.toString())
            },
            style = MaterialTheme.typography.bodySmall
        )
        val recycled = report.tables.sumOf { if (it.inRecycleBin < 0) 0L else it.inRecycleBin }
        if (recycled > 0) {
            Text(
                stringResource(R.string.settings_recycle_note, recycled),
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
