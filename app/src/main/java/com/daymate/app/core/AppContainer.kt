package com.ayaka7452.daymate.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.ayaka7452.daymate.data.db.DayMateDatabase
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

    private val mainDb = DayMateDatabase.build(appContext)

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)
    val autoBackup = AutoBackupManager(appContext, mainDb, settingsRepository)
    val festivalRepository = com.ayaka7452.daymate.data.festival.FestivalRepository(appContext)

    // 数据变更统一通知：触发自动备份计时 + 刷新桌面小组件
    private fun notifyDataChanged() {
        autoBackup.onDataChanged()
        runCatching { com.ayaka7452.daymate.widget.WidgetRenderer.refreshAll(appContext) }
    }

    val eventRepository = EventRepository(mainDb.eventDao(), ::notifyDataChanged)
    val folderRepository = FolderRepository(mainDb.folderDao(), ::notifyDataChanged)
    val vaultRepository = VaultRepository(mainDb.vaultEventDao(), ::notifyDataChanged)
    val vaultFolderRepository = VaultFolderRepository(mainDb.vaultFolderDao(), ::notifyDataChanged)
    val vaultBridge = VaultBridge(eventRepository, vaultRepository)

    /**
     * 对仍处于打开状态的库执行 WAL checkpoint（TRUNCATE），把 -wal 中的已提交数据合并进主文件。
     * 用于导出备份前的落盘——**不关闭连接**：导出不改变应用数据，容器保持在线；
     * 若走 close + rebuild，在屏页面（主页等）remember 住的旧 Flow 会因旧库被关闭而永远
     * 收不到变更通知（表现为新建事件后列表不刷新，直到重启应用）。
     */
    fun checkpointWal() {
        runCatching {
            mainDb.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }
    }

    /** 关闭底层数据库（切换存储位置时先关闭以保证 WAL 落盘，再迁移文件）。 */
    fun close() {
        runCatching { mainDb.close() }
    }
}
