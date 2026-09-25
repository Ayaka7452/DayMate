package com.ayaka7452.daymate.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val THEME = stringPreferencesKey("theme_mode")                 // system / light / dark
        private val VAULT_PASSWORD_HASH = stringPreferencesKey("vault_password_hash")
        private val VAULT_SALT = stringPreferencesKey("vault_salt")
        private val VAULT_BIOMETRIC = booleanPreferencesKey("vault_biometric_enabled")
        private val DEFAULT_SORT = stringPreferencesKey("default_sort")        // remaining_asc 等
        private val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")  // 修改后自动备份，默认开启
        private val BACKUP_TARGET = stringPreferencesKey("backup_target")      // 备份位置：local / both / cloud
        private val HOME_TOP_CARD = stringPreferencesKey("home_top_card")       // festival / event / off
        private val HOME_BADGE_EMOJI = stringPreferencesKey("home_badge_emoji") // 节日卡片右侧角标，默认 ☀️
        private val COLOR_MODE = stringPreferencesKey("color_mode")            // white(默认) / system / blue / green / orange / purple
        private val CYCLE_CYCLE_DAYS = intPreferencesKey("cycle_days")          // 周期管家：周期天数（默认 28）
        private val CYCLE_PERIOD_DAYS = intPreferencesKey("cycle_period_days")  // 周期管家：经期持续天数（默认 5）
        private val CYCLE_PASSWORD = booleanPreferencesKey("cycle_password_enabled") // 周期管家密码保护（默认关，验证用 Vault 密码）
        private val CYCLE_EVENT_ENABLED = booleanPreferencesKey("cycle_event_enabled") // 主页快捷事件开关
        private val CYCLE_EVENT_TITLE = stringPreferencesKey("cycle_event_title")   // 快捷事件自定义名（默认「周期管家」）
        private val CYCLE_EVENT_ID = longPreferencesKey("cycle_event_id")           // 快捷事件的事件 id
        private val CYCLE_ENTRY_ENABLED = booleanPreferencesKey("cycle_entry_enabled") // 主页新建按钮上方的常驻入口按钮（默认关）
        private val CYCLE_DEFAULT_CALENDAR = booleanPreferencesKey("cycle_default_calendar") // 周期管家默认视图（false=圆环，true=日历）
        private val CYCLE_CYCLE_AUTO = booleanPreferencesKey("cycle_cycle_auto")     // 周期天数自动按记录均值推算（默认开；手改即固定）
        private val CYCLE_PERIOD_AUTO = booleanPreferencesKey("cycle_period_auto")   // 经期天数自动按记录均值推算（默认开；手改即固定）
        private val ALLOW_SCREENSHOT_CYCLE = booleanPreferencesKey("allow_screenshot_cycle") // 周期管家允许截屏（默认关＝阻止）
        private val ALLOW_SCREENSHOT_VAULT = booleanPreferencesKey("allow_screenshot_vault") // 保险箱允许截屏（默认关＝阻止）
        private val UPDATE_CHECK = booleanPreferencesKey("update_check_enabled")             // 启动时检查新版本（默认开）
        private val MAKEUP_HINT = booleanPreferencesKey("makeup_tomorrow_hint_enabled")      // 明日补班预告（默认开）
        private val HOLIDAY_SPAN_TOTAL = booleanPreferencesKey("holiday_span_show_total")    // 假期天数显示：false=只显示剩余（默认）/ true=总长+剩余
        private val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check")              // 上次检查时间戳（节流用）
        private val UPDATE_SKIPPED = stringPreferencesKey("update_skipped_version")          // 用户点过「稍后」的版本
        private val HOME_VIEW_MODE = stringPreferencesKey("home_view_mode")                  // 主页视图：list(列表) / medium(中图标) / large(大图标)
        private val HOME_GRID_SPACING = intPreferencesKey("home_grid_spacing")               // 图标模式方块间隔 dp（默认 8）

        /** 主页视图模式取值：列表 / 中图标(3列) / 大图标(2列)。 */
        const val VIEW_MODE_LIST = "list"
        const val VIEW_MODE_MEDIUM = "medium"
        const val VIEW_MODE_LARGE = "large"

        /** 图标模式方块间隔的默认值与可设范围（dp）。 */
        const val GRID_SPACING_DEFAULT = 8
        const val GRID_SPACING_MIN = 0
        const val GRID_SPACING_MAX = 24

        /** 备份位置取值：仅本地 SAF 文件夹 / 本地 + WebDAV 云端 / 仅 WebDAV 云端。 */
        const val BACKUP_TARGET_LOCAL = "local"
        const val BACKUP_TARGET_BOTH = "both"
        const val BACKUP_TARGET_CLOUD = "cloud"
    }

    val themeMode: Flow<String> = dataStore.data.map { it[THEME] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[THEME] = mode }
    }

    suspend fun setColorMode(mode: String) {
        dataStore.edit { it[COLOR_MODE] = mode }
    }

    val vaultPasswordSet: Flow<Boolean> =
        dataStore.data.map { !it[VAULT_PASSWORD_HASH].isNullOrEmpty() }

    val vaultPasswordHash: Flow<String?> =
        dataStore.data.map { it[VAULT_PASSWORD_HASH] }

    val vaultSalt: Flow<String?> =
        dataStore.data.map { it[VAULT_SALT] }

    val vaultBiometricEnabled: Flow<Boolean> =
        dataStore.data.map { it[VAULT_BIOMETRIC] ?: false }

    val defaultSort: Flow<String> = dataStore.data.map { it[DEFAULT_SORT] ?: "remaining_asc" }

    /** 是否在每次数据库修改后自动备份到已配置的 SAF 备份文件夹（默认开启）。 */
    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_BACKUP] ?: true }

    /**
     * 备份位置：
     *  - `local`：仅本地 SAF 备份文件夹（默认，保持旧行为）；
     *  - `both`：本地 + WebDAV 云端各留一份；
     *  - `cloud`：仅 WebDAV 云端（本地文件夹可留作手动导入导出）。
     */
    val backupTarget: Flow<String> = dataStore.data.map { it[BACKUP_TARGET] ?: BACKUP_TARGET_LOCAL }

    suspend fun setBackupTarget(target: String) {
        dataStore.edit { it[BACKUP_TARGET] = target }
    }

    /** 主页顶部卡片内容：festival=下一个节假日（默认）/ event=最近的倒数日 / off=关闭。 */
    val homeTopCard: Flow<String> = dataStore.data.map { it[HOME_TOP_CARD] ?: "festival" }

    /** 主页视图模式：list(列表，默认) / medium(中图标，3列) / large(大图标，2列)。 */
    val homeViewMode: Flow<String> = dataStore.data.map { it[HOME_VIEW_MODE] ?: VIEW_MODE_LIST }

    suspend fun setHomeViewMode(mode: String) {
        dataStore.edit { it[HOME_VIEW_MODE] = mode }
    }

    /** 图标模式（中图标 3 列 / 大图标 2 列）网格中方块之间的间隔，单位 dp（默认 8）。 */
    val homeGridSpacing: Flow<Int> =
        dataStore.data.map { it[HOME_GRID_SPACING] ?: GRID_SPACING_DEFAULT }

    suspend fun setHomeGridSpacing(dp: Int) {
        dataStore.edit { it[HOME_GRID_SPACING] = dp.coerceIn(GRID_SPACING_MIN, GRID_SPACING_MAX) }
    }

    /** 节日卡片右侧角标 emoji（默认 ☀️；卡片只显示放假节日，不需要「休/班」）。 */
    val homeBadgeEmoji: Flow<String> = dataStore.data.map { it[HOME_BADGE_EMOJI] ?: "☀️" }

    /**
     * UI 配色方案：white=白底品牌色（默认）/ system=跟随系统壁纸取色（Material You，Android 12+，
     * 低版本回退白色）/ blue / green / orange / purple=固定原生配色组合。
     */
    val colorMode: Flow<String> = dataStore.data.map { it[COLOR_MODE] ?: "white" }

    suspend fun setDefaultSort(sort: String) {
        dataStore.edit { it[DEFAULT_SORT] = sort }
    }

    suspend fun setVaultPassword(hash: String, salt: String) {
        dataStore.edit {
            it[VAULT_PASSWORD_HASH] = hash
            it[VAULT_SALT] = salt
        }
    }

    suspend fun setVaultBiometric(enabled: Boolean) {
        dataStore.edit { it[VAULT_BIOMETRIC] = enabled }
    }

    /** 清除 Vault 密码相关记录（重置密码时调用；Vault 数据本身由仓库清空）。 */
    suspend fun clearVaultPassword() {
        dataStore.edit {
            it.remove(VAULT_PASSWORD_HASH)
            it.remove(VAULT_SALT)
            it.remove(VAULT_BIOMETRIC)
        }
    }

    /** 设置「修改后自动备份」开关。 */
    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_BACKUP] = enabled }
    }

    // ===== 隐私：截图限制 =====

    /**
     * 是否允许对「周期管家」页面截屏/录屏（默认 false＝阻止）。
     * 放开后周期日历与记录可被截屏，也会出现在最近任务缩略图里；密码验证页始终阻止。
     */
    val allowScreenshotCycle: Flow<Boolean> =
        dataStore.data.map { it[ALLOW_SCREENSHOT_CYCLE] ?: false }

    /**
     * 是否允许对「保险箱」内容页截屏/录屏（默认 false＝阻止）。
     * 解锁页与设密页始终阻止，避免密码被截。
     */
    val allowScreenshotVault: Flow<Boolean> =
        dataStore.data.map { it[ALLOW_SCREENSHOT_VAULT] ?: false }

    suspend fun setAllowScreenshotCycle(allow: Boolean) {
        dataStore.edit { it[ALLOW_SCREENSHOT_CYCLE] = allow }
    }

    suspend fun setAllowScreenshotVault(allow: Boolean) {
        dataStore.edit { it[ALLOW_SCREENSHOT_VAULT] = allow }
    }

    /** 设置主页顶部卡片显示内容。 */
    suspend fun setHomeTopCard(mode: String) {
        dataStore.edit { it[HOME_TOP_CARD] = mode }
    }

    /** 设置节日卡片角标 emoji。 */
    suspend fun setHomeBadgeEmoji(emoji: String) {
        dataStore.edit { it[HOME_BADGE_EMOJI] = emoji }
    }

    // ===== 周期管家 =====

    val cycleDays: Flow<Int> = dataStore.data.map { it[CYCLE_CYCLE_DAYS] ?: 28 }
    val cyclePeriodDays: Flow<Int> = dataStore.data.map { it[CYCLE_PERIOD_DAYS] ?: 5 }
    val cyclePasswordEnabled: Flow<Boolean> = dataStore.data.map { it[CYCLE_PASSWORD] ?: false }
    val cycleEventEnabled: Flow<Boolean> = dataStore.data.map { it[CYCLE_EVENT_ENABLED] ?: false }
    /** 主页右下角、新建按钮上方的常驻入口按钮（默认关，需在周期管家设置里开启）。 */
    val cycleEntryEnabled: Flow<Boolean> = dataStore.data.map { it[CYCLE_ENTRY_ENABLED] ?: false }
    val cycleEventTitle: Flow<String> = dataStore.data.map { it[CYCLE_EVENT_TITLE] ?: Tr.s(R.string.cycle_default_event_title) }
    val cycleEventId: Flow<Long> = dataStore.data.map { it[CYCLE_EVENT_ID] ?: -1L }
    val cycleDefaultCalendar: Flow<Boolean> = dataStore.data.map { it[CYCLE_DEFAULT_CALENDAR] ?: false }
    val cycleCycleAuto: Flow<Boolean> = dataStore.data.map { it[CYCLE_CYCLE_AUTO] ?: true }
    val cyclePeriodAuto: Flow<Boolean> = dataStore.data.map { it[CYCLE_PERIOD_AUTO] ?: true }

    suspend fun setCycleDays(days: Int) {
        dataStore.edit { it[CYCLE_CYCLE_DAYS] = days }
    }

    suspend fun setCyclePeriodDays(days: Int) {
        dataStore.edit { it[CYCLE_PERIOD_DAYS] = days }
    }

    suspend fun setCycleCycleAuto(auto: Boolean) {
        dataStore.edit { it[CYCLE_CYCLE_AUTO] = auto }
    }

    suspend fun setCyclePeriodAuto(auto: Boolean) {
        dataStore.edit { it[CYCLE_PERIOD_AUTO] = auto }
    }

    suspend fun setCyclePasswordEnabled(enabled: Boolean) {
        dataStore.edit { it[CYCLE_PASSWORD] = enabled }
    }

    suspend fun setCycleEventEnabled(enabled: Boolean) {
        dataStore.edit { it[CYCLE_EVENT_ENABLED] = enabled }
    }

    suspend fun setCycleEventTitle(title: String) {
        dataStore.edit { it[CYCLE_EVENT_TITLE] = title }
    }

    suspend fun setCycleEventId(id: Long) {
        dataStore.edit { it[CYCLE_EVENT_ID] = id }
    }

    suspend fun setCycleEntryEnabled(enabled: Boolean) {
        dataStore.edit { it[CYCLE_ENTRY_ENABLED] = enabled }
    }

    suspend fun setCycleDefaultCalendar(calendar: Boolean) {
        dataStore.edit { it[CYCLE_DEFAULT_CALENDAR] = calendar }
    }

    // ===== 检查更新 =====

    /**
     * 是否在启动时检查新版本（**默认开启**）。
     * 关掉后不发起任何请求，设置页仍保留「立即检查」手动入口。
     */
    val updateCheckEnabled: Flow<Boolean> = dataStore.data.map { it[UPDATE_CHECK] ?: true }

    suspend fun setUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[UPDATE_CHECK] = enabled }
    }

    /**
     * 明日补班预告（**默认开启**）：补班前一天在主页/文件夹横幅与小组件上预告。
     * 只关「预告」，当天补班横幅（今日XX调休补班）不受影响。
     */
    val makeupHintEnabled: Flow<Boolean> = dataStore.data.map { it[MAKEUP_HINT] ?: true }

    suspend fun setMakeupHintEnabled(enabled: Boolean) {
        dataStore.edit { it[MAKEUP_HINT] = enabled }
    }

    /** 假期天数口径：false=假期中段只显示剩余（默认）/ true=显示「总长 · 还剩 N 天」。 */
    val holidaySpanTotal: Flow<Boolean> = dataStore.data.map { it[HOLIDAY_SPAN_TOTAL] ?: false }

    suspend fun setHolidaySpanTotal(enabled: Boolean) {
        dataStore.edit { it[HOLIDAY_SPAN_TOTAL] = enabled }
    }

    /** 上次检查（含失败）的时间戳，用于「同一天不重复问」的节流。 */
    suspend fun updateLastCheckAt(): Long = dataStore.data.first()[UPDATE_LAST_CHECK] ?: 0L

    suspend fun setUpdateLastCheckAt(at: Long) {
        dataStore.edit { it[UPDATE_LAST_CHECK] = at }
    }

    /**
     * 用户点了「稍后」的版本号。
     *
     * 只对**这一个版本**闭嘴：等下一个版本发布时仍会正常提醒，
     * 否则点过一次「稍后」＝永久静音，功能等于关掉了。
     */
    suspend fun updateSkippedVersion(): String? = dataStore.data.first()[UPDATE_SKIPPED]

    suspend fun setUpdateSkippedVersion(version: String?) {
        dataStore.edit {
            if (version.isNullOrBlank()) it.remove(UPDATE_SKIPPED) else it[UPDATE_SKIPPED] = version
        }
    }
}
