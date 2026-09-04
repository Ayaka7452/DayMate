package com.ayaka7452.daymate.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ayaka7452.daymate.core.StorageConfig
import java.io.File

/**
 * SAF 备份工具：把内部主库导出到用户选择的备份文件夹，或从中导入。
 *
 * 全程通过 DocumentFile / ContentResolver 访问用户用 SAF（OpenDocumentTree）选择的目录，
 * 凭借系统授予的持久化 URI 权限读写，不需要申请任何存储权限（含 MANAGE_EXTERNAL_STORAGE）。
 *
 * 注意：SQLite 在数据库打开时会使用 WAL（-wal / -shm 文件）。导出 [exportInternal] 前调用方
 * 应对在线容器执行 wal_checkpoint(TRUNCATE) 让数据落盘到主文件（容器保持打开）；导入
 * [importExternal] 前应确保主库已关闭（close 容器），再复制文件、最后 rebuild 容器。
 * 本工具只负责文件的复制，不再额外维护 WAL 状态。
 */
object StorageBackup {
    private const val DB_NAME = "daymate.db"
    private val SUFFIXES = listOf("", "-wal", "-shm")

    /** 选中文件夹的备份探测结果。 */
    sealed interface BackupPreview {
        /** 文件夹中不存在 daymate.db。 */
        object None : BackupPreview
        /** 存在且为合法 SQLite 数据库。 */
        object Valid : BackupPreview
        /** 存在但非合法 SQLite（损坏或别的文件）。 */
        object Invalid : BackupPreview
    }

    /** 探测指定 tree Uri 文件夹中是否已有可用的 DayMate 备份。 */
    fun previewBackup(ctx: Context, treeUri: Uri?): BackupPreview {
        val uri = treeUri ?: return BackupPreview.None
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return BackupPreview.None
        val file = root.findFile(DB_NAME) ?: return BackupPreview.None
        return if (isFileReadableSqlite(ctx, file.uri)) BackupPreview.Valid else BackupPreview.Invalid
    }

    private fun isFileReadableSqlite(ctx: Context, fileUri: Uri): Boolean {
        return try {
            ctx.contentResolver.openInputStream(fileUri)?.use { ins ->
                val header = ByteArray(16)
                if (ins.read(header) != 16) return false
                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            } ?: false
        } catch (_: Throwable) { false }
    }

    /** 备份文件夹中是否存在 daymate.db。 */
    fun exists(ctx: Context): Boolean {
        val uri = StorageConfig.backupUri(ctx) ?: return false
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return false
        return root.findFile(DB_NAME) != null
    }

    /** 备份文件是否为合法 SQLite（用于导入前校验）。 */
    fun isBackupReadable(ctx: Context): Boolean {
        val uri = StorageConfig.backupUri(ctx) ?: return false
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return false
        val file = root.findFile(DB_NAME) ?: return false
        return try {
            ctx.contentResolver.openInputStream(file.uri)?.use { ins ->
                val header = ByteArray(16)
                if (ins.read(header) != 16) return false
                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            } ?: false
        } catch (_: Throwable) { false }
    }

    /**
     * 把内部主库导出到已配置的备份文件夹。
     * @param internalDb 内部主库文件（通常 ctx.getDatabasePath("daymate.db")）。
     * 调用前应已 close 容器以保证 WAL 落盘；本函数只负责复制。
     */
    fun exportInternal(ctx: Context, internalDb: File, targetUri: Uri? = StorageConfig.backupUri(ctx)) {
        val uri = targetUri ?: return
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return
        for (suffix in SUFFIXES) {
            val src = File(internalDb.path + suffix)
            if (!src.exists()) continue
            val name = DB_NAME + suffix
            // 覆盖前先删除旧文件，避免 SAF 自动重命名为 "daymate.db (1)"
            root.findFile(name)?.delete()
            val target = root.createFile("application/octet-stream", name) ?: continue
            src.inputStream().use { input ->
                ctx.contentResolver.openOutputStream(target.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * 从已配置的备份文件夹导入到内部主库。
     * @param internalDb 内部主库文件（通常 ctx.getDatabasePath("daymate.db")）。
     * 调用前应已 close 容器；本函数只负责复制。导入成功后调用方需 rebuild 容器。
     * @return 是否成功导入（备份文件夹中找不到 daymate.db 时返回 false）。
     */
    fun importExternal(ctx: Context, internalDb: File, sourceUri: Uri? = StorageConfig.backupUri(ctx)): Boolean {
        val uri = sourceUri ?: return false
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return false
        val src = root.findFile(DB_NAME) ?: return false
        for (suffix in SUFFIXES) {
            val target = File(internalDb.path + suffix)
            target.parentFile?.mkdirs()
            if (suffix.isEmpty()) {
                ctx.contentResolver.openInputStream(src.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return false
            } else {
                // -wal / -shm 为可选：存在则复制，不存在则清掉内部残留，让下次打开重建
                val ext = root.findFile(DB_NAME + suffix)
                if (ext != null) {
                    ctx.contentResolver.openInputStream(ext.uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                } else {
                    target.delete()
                }
            }
        }
        return true
    }

    /**
     * 统计一个 SQLite 数据库文件中的用户数据行数（events + folders + vault_events + vault_folders）。
     * 以只读方式打开，逐表计数（缺表按 0 计），用于判断数据库是否为空（避免用空库覆盖有数据的备份）。
     * 返回 -1 表示「无法判定」（文件打不开 / 任一表读不出）——调用方必须按「有数据」保守处理。
     */
    fun countDataRows(dbFile: File): Int {
        if (!dbFile.exists()) return 0
        return try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                var total = 0
                for (tbl in listOf("events", "folders", "vault_events", "vault_folders")) {
                    // 任一表读不出来都视为「无法判定」：绝不能把读库失败当成 0 行，
                    // 否则「空数据覆盖备份」护栏会被绕过
                    val rows = runCatching {
                        db.compileStatement("SELECT COUNT(*) FROM $tbl").simpleQueryForLong().toInt()
                    }.getOrNull() ?: return -1
                    total += rows
                }
                total
            }
        } catch (_: Throwable) { -1 }
    }

    /**
     * 探测备份文件夹中 daymate.db 的数据行数：先把备份（含 -wal / -shm 附属文件）复制到缓存临时文件
     * 再只读计数（避免直接打开 SAF Content Uri），计数完成后删除临时文件。
     * 返回值语义：0 = 文件夹中没有备份文件（视为空备份）；>0 = 备份有数据；
     * -1 = 备份文件存在但复制/读库失败（无法判定），调用方必须按「有数据」保守处理。
     */
    fun probeBackupDataRows(ctx: Context, treeUri: Uri?): Int {
        val uri = treeUri ?: return 0
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return 0
        val src = root.findFile(DB_NAME) ?: return 0
        val tmp = File(ctx.cacheDir, "daymate_probe.db")
        val tmpWal = File(ctx.cacheDir, "daymate_probe.db-wal")
        val tmpShm = File(ctx.cacheDir, "daymate_probe.db-shm")
        listOf(tmp, tmpWal, tmpShm).forEach { it.delete() }
        return try {
            // 主文件复制失败（拿不到流 / IO 异常）→ 返回 -1，绝不按 0 行放行
            var copied = false
            ctx.contentResolver.openInputStream(src.uri)?.use { ins ->
                tmp.outputStream().use { ins.copyTo(it) }
                copied = true
            }
            if (!copied) return -1
            for (suffix in listOf("-wal", "-shm")) {
                root.findFile(DB_NAME + suffix)?.let { ext ->
                    ctx.contentResolver.openInputStream(ext.uri)?.use { ins ->
                        File(ctx.cacheDir, "daymate_probe.db$suffix").outputStream().use { ins.copyTo(it) }
                    }
                }
            }
            countDataRows(tmp).also {
                tmp.delete(); tmpWal.delete(); tmpShm.delete()
            }
        } catch (_: Throwable) {
            tmp.delete(); tmpWal.delete(); tmpShm.delete()
            -1
        }
    }
}
