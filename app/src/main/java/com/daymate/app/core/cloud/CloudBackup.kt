package com.ayaka7452.daymate.core.cloud

import android.content.Context
import com.ayaka7452.daymate.core.StorageBackup
import java.io.File

/**
 * 云端备份的编排层：把「内部主库文件」与「WebDAV 上的 daymate.db」对接起来。
 *
 * 备份文件与本地 SAF 备份同名（daymate.db），因此两种渠道可以互相导入：
 * 本地导出后可用同账号在别的机器上从云端恢复，反之亦然。
 *
 * 所有方法均为阻塞调用，请在 IO 线程使用。
 */
object CloudBackup {

    /** 云端备份文件的完整远程路径（相对 baseUrl），例如 `DayMate/daymate.db`。 */
    fun remoteDbPath(cfg: WebDavConfig): String {
        val dir = cfg.directory.trim().trim('/')
        return if (dir.isEmpty()) WebDavStore.REMOTE_DB else "$dir/${WebDavStore.REMOTE_DB}"
    }

    /** 测试连通性与凭据。 */
    fun testConnection(cfg: WebDavConfig) {
        WebDavClient(cfg).testConnection()
    }

    /** 云端是否已有备份文件。 */
    fun exists(cfg: WebDavConfig): Boolean =
        WebDavClient(cfg).statOrNull(remoteDbPath(cfg), isCollection = false) != null

    /** 列出云端目录下的子项（供选择远程目录时浏览）。 */
    fun list(cfg: WebDavConfig, path: String): List<WebDavEntry> =
        WebDavClient(cfg).list(path)

    /** 逐级创建远程目录。 */
    fun ensureDirectory(cfg: WebDavConfig, path: String) {
        WebDavClient(cfg).ensureDirectory(path)
    }

    /** 上传内部主库，覆盖云端备份。 */
    fun upload(internalDb: File, cfg: WebDavConfig) {
        val client = WebDavClient(cfg)
        client.ensureDirectory(cfg.directory)
        client.upload(remoteDbPath(cfg), internalDb)
    }

    /** 下载云端备份到 [dest]（父目录自动创建）。 */
    fun download(cfg: WebDavConfig, dest: File) {
        WebDavClient(cfg).download(remoteDbPath(cfg), dest)
    }

    /**
     * 探测云端备份的数据行数，用于「空数据不得覆盖好备份」护栏。
     * @return 0=云端没有备份或确认为空；>0=有数据；-1=下载/读库失败（**无法判定，必须按有数据保守处理**）。
     */
    fun probeRemoteRows(ctx: Context, cfg: WebDavConfig): Int {
        val tmp = File(ctx.cacheDir, "daymate_remote_probe.db")
        tmp.delete()
        return try {
            download(cfg, tmp)
            StorageBackup.countDataRows(tmp)
        } catch (e: WebDavException) {
            // 404 = 云端确实没有备份，可以安全地视为「空」
            if (e.code == 404) 0 else -1
        } catch (_: Throwable) {
            -1
        } finally {
            tmp.delete()
        }
    }
}
