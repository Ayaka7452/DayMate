package com.ayaka7452.daymate.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }

    val themeMode: Flow<String> = dataStore.data.map { it[THEME] ?: "system" }

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

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[THEME] = mode }
    }

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
}
