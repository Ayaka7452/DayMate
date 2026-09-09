package com.ayaka7452.daymate.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val THEME = stringPreferencesKey("theme_mode")                 // system / light / dark
        private val VAULT_PASSWORD_HASH = stringPreferencesKey("vault_password_hash")
        private val VAULT_SALT = stringPreferencesKey("vault_salt")
        private val VAULT_BIOMETRIC = booleanPreferencesKey("vault_biometric_enabled")
        private val DEFAULT_SORT = stringPreferencesKey("default_sort")        // remaining_asc 等
        private val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")  // 修改后自动备份，默认开启
        private val HOME_TOP_CARD = stringPreferencesKey("home_top_card")       // festival / event / off
        private val HOME_BADGE_EMOJI = stringPreferencesKey("home_badge_emoji") // 节日卡片右侧角标，默认 ☀️
        private val COLOR_MODE = stringPreferencesKey("color_mode")            // white(默认) / system / blue / green / orange / purple
        private val CYCLE_CYCLE_DAYS = intPreferencesKey("cycle_days")          // 周期管家：周期天数（默认 28）
        private val CYCLE_PERIOD_DAYS = intPreferencesKey("cycle_period_days")  // 周期管家：经期持续天数（默认 5）
        private val CYCLE_PASSWORD = booleanPreferencesKey("cycle_password_enabled") // 周期管家密码保护（默认关，验证用 Vault 密码）
        private val CYCLE_EVENT_ENABLED = booleanPreferencesKey("cycle_event_enabled") // 主页快捷事件开关
        private val CYCLE_EVENT_TITLE = stringPreferencesKey("cycle_event_title")   // 快捷事件自定义名（默认「周期管家」）
        private val CYCLE_EVENT_ID = longPreferencesKey("cycle_event_id")           // 快捷事件的事件 id
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

    /** 主页顶部卡片内容：festival=下一个节假日（默认）/ event=最近的倒数日 / off=关闭。 */
    val homeTopCard: Flow<String> = dataStore.data.map { it[HOME_TOP_CARD] ?: "festival" }

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
    val cycleEventTitle: Flow<String> = dataStore.data.map { it[CYCLE_EVENT_TITLE] ?: "周期管家" }
    val cycleEventId: Flow<Long> = dataStore.data.map { it[CYCLE_EVENT_ID] ?: -1L }

    suspend fun setCycleDays(days: Int) {
        dataStore.edit { it[CYCLE_CYCLE_DAYS] = days }
    }

    suspend fun setCyclePeriodDays(days: Int) {
        dataStore.edit { it[CYCLE_PERIOD_DAYS] = days }
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
}
