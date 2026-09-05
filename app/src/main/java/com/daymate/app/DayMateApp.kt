package com.ayaka7452.daymate

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.ayaka7452.daymate.core.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class DayMateApp : Application() {
    // 必须在 onCreate 里赋值：字段初始化器阶段 base context 尚未 attach，
    // 此时 AppContainer(this) 访问 applicationContext 会 NPE。
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        migrateLegacyVault()
        installCrashHandler()
        // 小组件跨天精确刷新：应用起来后续订下一个午夜的刷新闹钟
        runCatching {
            com.ayaka7452.daymate.widget.WidgetRefreshScheduler.scheduleNextMidnight(this)
        }
    }

    /**
     * 旧版独立 vault.db 的一次性迁移：读明文数据写入合并后的主库。
     * 迁移时 Vault 尚未解锁（无密钥），故以明文写入；旧数据在新库中仍保持明文，
     * 之后新增/编辑的数据会被用户密码加密。读取时解密失败会以明文兜底，不影响显示。
     * 若旧文件损坏或读取失败，直接删除，避免脏数据。
     */
    private fun migrateLegacyVault() {
        // 旧版独立 vault.db 只可能残留在内部沙盒（主库所在目录）；路线 A 下主库恒为内部，
        // 不再有外部 vault.db 概念，故仅检查内部路径。
        val candidates = mutableListOf<File>()
        runCatching {
            getDatabasePath("daymate.db").parentFile?.let { candidates.add(File(it, "vault.db")) }
        }

        for (old in candidates.distinct()) {
            if (!old.exists()) continue
            // 旧库 schema 与当前 Room 实体不保证一致（实体可能新增列），用裸 SQL 只读读取，
            // 避免 Room schema 校验失败；先读文件夹再读事件（保持外键引用有效）。
            val migrated: Pair<List<com.ayaka7452.daymate.data.db.VaultFolderEntity>, List<com.ayaka7452.daymate.data.db.VaultEventEntity>> =
                runCatching {
                SQLiteDatabase.openDatabase(old.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val folders = mutableListOf<com.ayaka7452.daymate.data.db.VaultFolderEntity>()
                    db.rawQuery(
                        "SELECT id, name, icon, color, sortIndex, isPinned, createdAt FROM vault_folders",
                        null
                    ).use { c ->
                        while (c.moveToNext()) {
                            folders.add(
                                com.ayaka7452.daymate.data.db.VaultFolderEntity(
                                    id = c.getLong(0),
                                    name = c.getString(1),
                                    icon = c.getString(2),
                                    color = if (c.isNull(3)) null else c.getInt(3),
                                    sortIndex = c.getInt(4),
                                    isPinned = c.getInt(5) != 0,
                                    createdAt = c.getLong(6)
                                )
                            )
                        }
                    }
                    val folderIds = folders.map { it.id }.toSet()
                    val events = mutableListOf<com.ayaka7452.daymate.data.db.VaultEventEntity>()
                    db.rawQuery(
                        "SELECT id, title, targetDateEpochDay, repeatYearly, note, color, folderId, " +
                            "sortIndex, isPinned, createdAt, updatedAt FROM vault_events",
                        null
                    ).use { c ->
                        while (c.moveToNext()) {
                            val fid = c.getLong(6)
                            events.add(
                                com.ayaka7452.daymate.data.db.VaultEventEntity(
                                    id = c.getLong(0),
                                    title = c.getString(1),
                                    targetDateEpochDay = c.getLong(2),
                                    repeatYearly = c.getInt(3) != 0,
                                    note = c.getString(4),
                                    color = if (c.isNull(5)) null else c.getInt(5),
                                    folderId = if (c.isNull(6) || fid !in folderIds) null else fid,
                                    sortIndex = c.getInt(7),
                                    isPinned = c.getInt(8) != 0,
                                    createdAt = c.getLong(9),
                                    updatedAt = c.getLong(10)
                                )
                            )
                        }
                    }
                    folders to events
                }
            }.onFailure { Log.w("DayMateMigrate", "legacy vault read failed", it) }
                .getOrDefault(
                    emptyList<com.ayaka7452.daymate.data.db.VaultFolderEntity>() to
                        emptyList<com.ayaka7452.daymate.data.db.VaultEventEntity>()
                )

            val (folders, events) = migrated
            if (folders.isNotEmpty() || events.isNotEmpty()) {
                runCatching {
                    runBlocking(Dispatchers.IO) {
                        container.vaultFolderRepository.addAll(folders)
                        container.vaultRepository.addAll(events)
                    }
                }.onFailure { Log.w("DayMateMigrate", "legacy vault import failed", it) }
            }
            runCatching { old.delete() }
        }
    }

    /** 切换数据库存储位置后，关闭旧库并以新位置重建容器。 */
    fun rebuildContainer() {
        runCatching { container.close() }
        container = AppContainer(this)
    }

    /** 兜底：任何未捕获异常都写入外部文件 + Logcat，方便定位闪退（无 keystore 也能用）。 */
    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = sw.toString()
                Log.e("DayMateCrash", trace)
                getExternalFilesDir(null)?.let { dir ->
                    runCatching {
                        File(dir, "daymate_crash.txt").writeText(
                            "time=${System.currentTimeMillis()}\nthread=${thread.name}\n$trace"
                        )
                    }
                }
            } catch (_: Throwable) {
                // 忽略记录失败，不影响原始异常处理
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
