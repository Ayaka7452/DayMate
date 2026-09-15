package com.ayaka7452.daymate.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * SAF 备份工具：把内部主库导出到用户选择的备份文件夹，或从中导入。
 *
 * 全程通过 DocumentFile / ContentResolver 访问用户用 SAF（OpenDocumentTree）选择的目录，
 * 凭借系统授予的持久化 URI 权限读写，不需要申请任何存储权限（含 MANAGE_EXTERNAL_STORAGE）。
 *
 * 注意：SQLite 在数据库打开时会使用 WAL（-wal / -shm 文件）。导出 [exportInternal] 前调用方
 * 应对在线容器执行 wal_checkpoint(TRUNCATE) 让数据落盘到主文件（容器保持打开），因此备份
 * 只含单个 daymate.db；导入 [importExternal] 前应确保主库已关闭（close 容器），再复制文件、
 * 最后 rebuild 容器。导入兼容旧版三文件式备份（-wal / -shm 可选）。
 *
 * 关于重复备份文件：部分 SAF 提供方（SD 卡、第三方文件管理器的 DocumentsProvider）的
 * deleteDocument 会静默失败或延迟生效，此时 createFile 会被系统自动改名成
 * `daymate.db (1)`、`daymate.db (2)` 不断累积。为此本类统一按「命名模式」识别备份文件
 * （见 [BACKUP_NAME_REGEX]），而不是只匹配固定的 `daymate.db`——既保证写入前清理干净，
 * 也保证存在性探测不会因为文件被改名而漏判。
 */
object StorageBackup {
    /** 主备份文件名。云端备份（[com.ayaka7452.daymate.core.cloud.WebDavStore.REMOTE_DB]）与之同名。 */
    const val DB_NAME = "daymate.db"

    private val SUFFIXES = listOf("", "-wal", "-shm")

    /**
     * 备份相关文件的命名模式：
     *  - 本体 `daymate.db`；
     *  - 旧版三文件导出的 `daymate.db-wal` / `daymate.db-shm`；
     *  - SAF 自动改名产生的 `daymate.db (1)` / `daymate.db-wal (2)` 变体。
     */
    private val BACKUP_NAME_REGEX =
        Regex("^" + Regex.escape(DB_NAME) + "(?:-(?:wal|shm))?(?:\\s*\\(\\d+\\))?$")

    /** 导出互斥：自动备份的 flush() 与手动「立即备份」可能并发，交错执行会撞出重复文件。 */
    private val exportLock = Any()

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
        val file = findMainBackup(root) ?: return BackupPreview.None
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

    /** 本地文件是否为合法 SQLite 数据库（云端下载后、覆盖主库前校验）。 */
    fun isSqliteFile(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            file.inputStream().use { ins ->
                val header = ByteArray(16)
                if (ins.read(header) != 16) return false
                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            }
        } catch (_: Throwable) { false }
    }

    /** 备份文件夹中是否存在 main 备份文件。 */
    fun exists(ctx: Context): Boolean {
        val uri = StorageConfig.backupUri(ctx) ?: return false
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return false
        return findMainBackup(root) != null
    }

    /** 备份文件是否为合法 SQLite（用于导入前校验）。 */
    fun isBackupReadable(ctx: Context): Boolean {
        val uri = StorageConfig.backupUri(ctx) ?: return false
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return false
        val file = findMainBackup(root) ?: return false
        return isFileReadableSqlite(ctx, file.uri)
    }

    /**
     * 把内部主库导出到已配置的备份文件夹（只写单个 daymate.db 文件）。
     * @param internalDb 内部主库文件（通常 ctx.getDatabasePath("daymate.db")）。
     * 调用前调用方应已对在线容器执行 wal_checkpoint(TRUNCATE)——数据已全部合并进主文件，
     * 故 -wal / -shm 附属文件无需导出；目标文件夹中旧版三文件式导出残留的附属文件会一并清理。
     */
    fun exportInternal(ctx: Context, internalDb: File, targetUri: Uri? = StorageConfig.backupUri(ctx)) {
        val uri = targetUri ?: return
        val src = File(internalDb.path)
        if (!src.exists()) return
        synchronized(exportLock) {
            val root = DocumentFile.fromTreeUri(ctx, uri) ?: return
            // 覆盖前清理：按名字模式删除所有旧备份（含被 SAF 改名的 "(1)/(2)" 变体与旧版 -wal/-shm），
            // 否则 createFile 会被系统自动改名，越积越多。
            purgeBackupFiles(root)
            val target = root.createFile("application/octet-stream", DB_NAME)
            if (target == null) {
                purgeBackupFiles(root)
                return
            }
            src.inputStream().use { input ->
                ctx.contentResolver.openOutputStream(target.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            // 写入后再清一次：若本次 createFile 仍被改名（说明旧文件确实删不掉），
            // 就保留刚写入的这份、把其它同名残留清掉，尽量只留一份备份。
            purgeBackupFiles(root, keep = target.uri)
        }
    }

    /** 修复前快照的文件名后缀（`daymate.db.bak`）。 */
    private const val SNAPSHOT_SUFFIX = ".bak"

    /** 快照文件的命名模式（含 SAF 改名产生的 `(1)` 变体）。 */
    private val SNAPSHOT_REGEX =
        Regex("^" + Regex.escape(DB_NAME + SNAPSHOT_SUFFIX) + "(?:\\s*\\(\\d+\\))?$")

    /**
     * 在备份文件夹中留一份「修复前快照」`daymate.db.bak`（数据维护 → 修复前自动调用）。
     *
     * 与 [exportInternal] 刻意分开：快照不参与主备份的匹配（[BACKUP_NAME_REGEX] 不含 `.bak`），
     * 因此既不会被下一次备份顺手清掉，也不会被误认成主备份参与恢复。
     * @return 是否成功写入（未配置备份文件夹或写入失败时为 false，调用方据此提示用户）。
     */
    fun exportSnapshot(ctx: Context, internalDb: File, targetUri: Uri?): Boolean {
        val uri = targetUri ?: return false
        val src = File(internalDb.path)
        if (!src.exists()) return false
        synchronized(exportLock) {
            val root = DocumentFile.fromTreeUri(ctx, uri) ?: return false
            // 先清掉旧快照，避免 createFile 被系统改名成 `daymate.db.bak (1)`
            purgeSnapshots(root)
            val target = root.createFile("application/octet-stream", DB_NAME + SNAPSHOT_SUFFIX)
                ?: run {
                    purgeSnapshots(root)
                    return false
                }
            return runCatching {
                var written = false
                src.inputStream().use { input ->
                    ctx.contentResolver.openOutputStream(target.uri)?.use { output ->
                        input.copyTo(output)
                        written = true
                    }
                }
                written
            }.getOrDefault(false)
        }
    }

    /** 删除目录中所有快照文件（`daymate.db.bak` 及其改名变体）。 */
    private fun purgeSnapshots(root: DocumentFile) {
        for (f in root.listFiles().filter { SNAPSHOT_REGEX.matches(it.name.orEmpty()) }) {
            runCatching { f.delete() }
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
        val src = findMainBackup(root) ?: return false
        for (suffix in SUFFIXES) {
            val target = File(internalDb.path + suffix)
            target.parentFile?.mkdirs()
            if (suffix.isEmpty()) {
                ctx.contentResolver.openInputStream(src.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return false
            } else {
                // -wal / -shm 为可选：存在则复制，不存在则清掉内部残留，让下次打开重建
                val ext = findSidecar(root, suffix)
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
        val src = findMainBackup(root) ?: return 0
        val tmp = File(ctx.cacheDir, "daymate_probe.db")
        return try {
            // 主文件复制失败（拿不到流 / IO 异常）→ 返回 -1，绝不按 0 行放行
            var copied = false
            ctx.contentResolver.openInputStream(src.uri)?.use { ins ->
                tmp.outputStream().use { ins.copyTo(it) }
                copied = true
            }
            if (!copied) {
                deleteProbeTemps(ctx)
                return -1
            }
            for (suffix in listOf("-wal", "-shm")) {
                findSidecar(root, suffix)?.let { ext ->
                    ctx.contentResolver.openInputStream(ext.uri)?.use { ins ->
                        File(ctx.cacheDir, "daymate_probe.db$suffix").outputStream().use { ins.copyTo(it) }
                    }
                }
            }
            countDataRows(tmp).also { deleteProbeTemps(ctx) }
        } catch (_: Throwable) {
            deleteProbeTemps(ctx)
            -1
        }
    }

    // ===== 内部工具 =====

    /** 主备份文件：优先精确的 `daymate.db`，否则取改名变体中最近修改的一个。 */
    private fun findMainBackup(root: DocumentFile): DocumentFile? {
        val mains = matchingFiles(root).filterNot { isSidecar(it.name.orEmpty()) }
        return mains.firstOrNull { it.name == DB_NAME } ?: mains.maxByOrNull { it.lastModified() }
    }

    /** 查找 `daymate.db-wal` / `daymate.db-shm`（含改名变体）。 */
    private fun findSidecar(root: DocumentFile, suffix: String): DocumentFile? =
        matchingFiles(root).firstOrNull { it.name.orEmpty().startsWith(DB_NAME + suffix) }

    /** 目录中所有符合备份命名模式的条目。 */
    private fun matchingFiles(root: DocumentFile): List<DocumentFile> =
        root.listFiles().filter { BACKUP_NAME_REGEX.matches(it.name.orEmpty()) }

    private fun isSidecar(name: String): Boolean =
        name.contains("-wal") || name.contains("-shm")

    /** 删除目录中所有备份相关文件，[keep] 指定的 Uri 除外。 */
    private fun purgeBackupFiles(root: DocumentFile, keep: Uri? = null) {
        for (f in matchingFiles(root)) {
            if (keep != null && f.uri == keep) continue
            runCatching { f.delete() }
        }
    }

    private fun deleteProbeTemps(ctx: Context) {
        for (name in listOf("daymate_probe.db", "daymate_probe.db-wal", "daymate_probe.db-shm")) {
            File(ctx.cacheDir, name).delete()
        }
    }
}
