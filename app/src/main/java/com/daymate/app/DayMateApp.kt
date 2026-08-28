package com.ayaka7452.daymate

import android.app.Application
import android.util.Log
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.data.db.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class DayMateApp : Application() {
    var container: AppContainer = AppContainer(this)
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        migrateLegacyVault()
        installCrashHandler()
    }

    /**
     * 旧版独立 vault.db 的一次性迁移：读明文数据写入合并后的主库。
     * 迁移时 Vault 尚未解锁（无密钥），故以明文写入；旧数据在新库中仍保持明文，
     * 之后新增/编辑的数据会被用户密码加密。读取时解密失败会以明文兜底，不影响显示。
     * 若旧文件损坏或读取失败，直接删除，避免脏数据。
     */
    private fun migrateLegacyVault() {
        val candidates = mutableListOf<File>()
        runCatching {
            getDatabasePath("daymate.db").parentFile?.let { candidates.add(File(it, "vault.db")) }
        }
        StorageConfig.mainDbFile(this)?.parentFile?.let { candidates.add(File(it, "vault.db")) }

        for (old in candidates.distinct()) {
            if (!old.exists()) continue
            runCatching {
                runBlocking(Dispatchers.IO) {
                    val legacy = VaultDatabase.buildForMigration(this@DayMateApp, old)
                    val events = legacy.vaultEventDao().getAll()
                    val folders = legacy.vaultFolderDao().getAll()
                    legacy.close()
                    if (events.isNotEmpty() || folders.isNotEmpty()) {
                        container.vaultRepository.addAll(events)
                        container.vaultFolderRepository.addAll(folders)
                    }
                }
            }.onFailure { Log.w("DayMateMigrate", "legacy vault migrate failed", it) }
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
