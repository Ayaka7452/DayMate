package com.ayaka7452.daymate.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.data.db.DayMateDatabase
import com.ayaka7452.daymate.data.db.VaultDatabase
import com.ayaka7452.daymate.data.repo.EventRepository
import com.ayaka7452.daymate.data.repo.FolderRepository
import com.ayaka7452.daymate.data.repo.SettingsRepository
import com.ayaka7452.daymate.data.repo.VaultFolderRepository
import com.ayaka7452.daymate.data.repo.VaultRepository
import com.ayaka7452.daymate.core.VaultBridge

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 轻量手动依赖注入容器（Alpha 阶段；M1 后迁移 Hilt）。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val mainDb = DayMateDatabase.build(appContext, StorageConfig.mainDbFile(appContext))
    private val vaultDb = VaultDatabase.build(appContext, StorageConfig.vaultDbFile(appContext))

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)
    val eventRepository = EventRepository(mainDb.eventDao())
    val folderRepository = FolderRepository(mainDb.folderDao())
    val vaultRepository = VaultRepository(vaultDb.vaultEventDao())
    val vaultFolderRepository = VaultFolderRepository(vaultDb.vaultFolderDao())
    val vaultBridge = VaultBridge(eventRepository, vaultRepository)

    /** 关闭底层数据库（切换存储位置时先关闭以保证 WAL 落盘，再迁移文件）。 */
    fun close() {
        runCatching { mainDb.close() }
        runCatching { vaultDb.close() }
    }
}
