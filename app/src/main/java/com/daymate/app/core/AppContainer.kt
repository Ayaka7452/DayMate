package com.ayaka7452.daymate.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    /** 界面语言（SharedPreferences 存储，供 attachBaseContext 同步读取）。 */
    val localeStore = com.ayaka7452.daymate.core.i18n.LocaleStore(appContext)

    val autoBackup = AutoBackupManager(appContext, mainDb, settingsRepository)

    /** 数据库诊断与维护（设置 → 数据维护）：体检、无损修复、回收碎片并同步各备份点。 */
    val dbRepair = DbRepair(appContext, mainDb, autoBackup)
    val festivalRepository = com.ayaka7452.daymate.data.festival.FestivalRepository(appContext)

    /**
     * 应用级协程作用域：承载「发起之后不依赖任何界面存活」的任务。
     *
     * 目前只有一个使用者——切换节日数据源。设置页在确认切换后会立刻重启任务栈让新语言生效，
     * 挂在界面上的 rememberCoroutineScope 会随 Activity 一起取消，下载就永远发不出去。
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 数据变更统一通知：触发自动备份计时 + 刷新桌面小组件
    private fun notifyDataChanged() {
        autoBackup.onDataChanged()
        runCatching { com.ayaka7452.daymate.widget.WidgetRenderer.refreshAll(appContext) }
    }

    /**
     * 切换节日数据源并**立刻把新源的数据拉下来**。
     *
     * 只改 URL 不下载，用户看到的就是「源已经换成日本了，列表还是中国的」——还得自己再点一次
     * 「下载数据」、再重启一次 App 才生效。这里一次做完三件事：换源 → 下载 → 下载成功后
     * 重锚节日跟随事件并刷新小组件。
     *
     * 跑在 [appScope] 上：设置页可能在确认后立刻重启任务栈（换语言那一支）。
     */
    fun switchFestivalRegion(region: com.ayaka7452.daymate.data.festival.FestivalRegion) {
        festivalRepository.setRegion(region)
        downloadFestivalData()
    }

    /** 下载当前数据源的节假日数据，成功后重锚跟随事件并刷新小组件。 */
    fun downloadFestivalData() {
        appScope.launch {
            val result = runCatching { festivalRepository.updateFromNetwork() }.getOrNull()
            if (result?.success == true) {
                runCatching { eventRepository.reanchorFestivalEstimates(festivalRepository) }
                runCatching { eventRepository.rollForwardRepeating(festivalRepository) }
            }
            runCatching { com.ayaka7452.daymate.widget.WidgetRenderer.refreshAll(appContext) }
        }
    }

    val eventRepository = EventRepository(mainDb.eventDao(), ::notifyDataChanged)
    val folderRepository = FolderRepository(mainDb.folderDao(), ::notifyDataChanged)
    val vaultRepository = VaultRepository(mainDb.vaultEventDao(), ::notifyDataChanged)
    val vaultFolderRepository = VaultFolderRepository(mainDb.vaultFolderDao(), ::notifyDataChanged)
    val vaultBridge = VaultBridge(eventRepository, vaultRepository)
    val cycleRepository = com.ayaka7452.daymate.data.repo.CycleRepository(mainDb.cycleLogDao(), ::notifyDataChanged)

    /** 周期管家的日常记录（症状/情绪/性生活/自定义）。与经期记录分表，不参与推算。 */
    val cycleNoteRepository =
        com.ayaka7452.daymate.data.repo.CycleNoteRepository(mainDb.cycleNoteDao(), ::notifyDataChanged)

    val cycleEventBridge = CycleEventBridge(eventRepository, cycleRepository, settingsRepository)

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
