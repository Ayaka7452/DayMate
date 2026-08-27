package com.daymate.app.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.daymate.app.data.db.DayMateDatabase
import com.daymate.app.data.db.VaultDatabase
import com.daymate.app.data.repo.EventRepository
import com.daymate.app.data.repo.FolderRepository
import com.daymate.app.data.repo.SettingsRepository
import com.daymate.app.data.repo.VaultFolderRepository
import com.daymate.app.data.repo.VaultRepository
import com.daymate.app.core.VaultBridge

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 轻量手动依赖注入容器（Alpha 阶段；M1 后迁移 Hilt）。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val mainDb = DayMateDatabase.build(appContext)
    private val vaultDb = VaultDatabase.build(appContext)

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)
    val eventRepository = EventRepository(mainDb.eventDao())
    val folderRepository = FolderRepository(mainDb.folderDao())
    val vaultRepository = VaultRepository(vaultDb.vaultEventDao())
    val vaultFolderRepository = VaultFolderRepository(vaultDb.vaultFolderDao())
    val vaultBridge = VaultBridge(eventRepository, vaultRepository)
}
