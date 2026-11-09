package com.ayaka7452.daymate.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ayaka7452.daymate.core.log.AppLogger
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
 * 关于重复备份文件：SAF 的 createFile 遇到同名文件不会覆盖，而是由 DocumentsProvider 自动改名，
 * 且改名的**位置因提供方而异**（`daymate.db (1)` 或 `daymate (1).db` / `daymate(1).db`），
 * 于是每备份一次就多一份。早期只识别前一种命名，连「备份是否存在」「空数据护栏」「恢复入口」
 * 都被连带判错。现在按命名族识别（见 [BACKUP_NAME_REGEX]），并**复用已有主备份文件覆盖写**——
 * 不新建、也不删除备份目录里的任何文件，从根上杜绝副本堆积，同时不影响用户自己放的文件。
 */
object StorageBackup {
    /** 主备份文件名。云端备份（[com.ayaka7452.daymate.core.cloud.WebDavStore.REMOTE_DB]）与之同名。 */
    const val DB_NAME = "daymate.db"

    /** 文件名主体与扩展名：从 [DB_NAME] 拆出，避免命名规则与文件名不同步。 */
    private val DB_STEM = DB_NAME.substringBeforeLast('.')
    private val DB_EXT = DB_NAME.substringAfterLast('.')

    /** 自动改名编号：可有可无空格，如 `(1)` / ` (12)`。 */
    private const val COUNTER = "(?:\\s*\\(\\d+\\))?"

    private val SUFFIXES = listOf("", "-wal", "-shm")

    /**
     * 备份相关文件的命名模式（覆盖两种编号位置）：
     *  - 本体 `daymate.db`；
     *  - 编号在末尾 `daymate.db (1)` / `daymate.db(2)`；
     *  - 编号在扩展名前 `daymate (1).db` / `daymate(2).db`；
     *  - 旧版三文件导出的 `daymate.db-wal` / `daymate.db-shm` 及其同类变体。
     *
     * 用于「找到已有备份并复用」以及导入时的主文件探测，不再用于删除。
     * 刻意不含 `.bak`：修复前快照由 [SNAPSHOT_REGEX] 单独管理，不会被误认成主备份参与恢复。
     */
    private val BACKUP_NAME_REGEX = Regex(
        "^" + Regex.escape(DB_STEM) + COUNTER + "\\." + Regex.escape(DB_EXT) +
            COUNTER + "(?:-(?:wal|shm)" + COUNTER + ")?$"
    )

    /** 导出互斥：自动备份的 flush() 与手动「立即备份」可能并发，交错执行会互相覆盖。 */
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
     * 故 -wal / -shm 附属文件无需导出。
     *
     * 写入策略：**复用目录里已有的主备份文件直接覆盖写**，找不到才 createFile。
     * 这样 SAF 提供方没有机会自动改名，也就不会再产生 `daymate (1).db` 之类的副本；
     * 全程不删除备份目录里的任何已有文件。覆盖前把旧内容备份到应用缓存，写入或校验失败时写回。
     */
    fun exportInternal(ctx: Context, internalDb: File, targetUri: Uri? = StorageConfig.backupUri(ctx)) {
        val uri = targetUri ?: return
        val src = File(internalDb.path)
        if (!src.exists()) return
        synchronized(exportLock) {
            val root = DocumentFile.fromTreeUri(ctx, uri) ?: return
            val existing = findMainBackup(root)
            val target = existing ?: root.createFile("application/octet-stream", DB_NAME) ?: return
            val isNewFile = existing == null

            // 覆盖写之前先留一份回滚副本（落在应用缓存，不碰备份目录）。
            // 旧备份读不出来时宁可放弃本次备份，也绝不把用户唯一的备份覆盖坏。
            val rollback = File(ctx.cacheDir, "daymate_local_rollback.db")
            var hasRollback = false
            if (!isNewFile) {
                hasRollback = runCatching {
                    var copied = false
                    ctx.contentResolver.openInputStream(target.uri)?.use { ins ->
                        rollback.outputStream().use { ins.copyTo(it) }
                        copied = true
                    }
                    copied
                }.getOrDefault(false)
                if (!hasRollback) {
                    AppLogger.log(ctx, "Backup", "旧备份不可读，放弃本次覆盖写")
                    rollback.delete()
                    return
                }
            }

            val written = try {
                val out = ctx.contentResolver.openOutputStream(target.uri)
                if (out == null) {
                    false
                } else {
                    src.inputStream().use { input -> out.use { output -> input.copyTo(output) } }
                    isFileReadableSqlite(ctx, target.uri)
                }
            } catch (e: Exception) {
                AppLogger.log(ctx, "Backup", "导出失败：${e.message}")
                false
            }
            if (!written) {
                AppLogger.log(ctx, "Backup", "导出校验未通过，恢复原备份")
                if (hasRollback) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(target.uri)?.use { out ->
                            rollback.inputStream().use { it.copyTo(out) }
                        }
                    }
                } else {
                    // 只删除本次自己刚创建的半截文件，绝不动用户已有的文件
                    runCatching { target.delete() }
                }
                rollback.delete()
                return
            }
            rollback.delete()
            AppLogger.log(ctx, "Backup", "本地备份写入 ${target.name.orEmpty()}（覆盖写，不清理目录）")
        }
    }

    /** 修复前快照的文件名后缀（`daymate.db.bak`）。 */
    private const val SNAPSHOT_SUFFIX = ".bak"

    /** 快照文件的命名模式（同样覆盖两种编号位置，含无空格变体）。 */
    private val SNAPSHOT_REGEX = Regex(
        "^" + Regex.escape(DB_STEM) + COUNTER + "\\." + Regex.escape(DB_EXT) +
            COUNTER + Regex.escape(SNAPSHOT_SUFFIX) + COUNTER + "$"
    )

    /**
     * 在备份文件夹中留一份「修复前快照」`daymate.db.bak`（数据维护 → 修复前自动调用）。
     *
     * 与 [exportInternal] 刻意分开：快照不参与主备份的匹配（[BACKUP_NAME_REGEX] 不含 `.bak`），
     * 因此既不会被覆盖，也不会被误认成主备份参与恢复。
     * 同样复用已有快照覆盖写，不清理目录、不会累积 `.bak (1)` 之类的副本。
     * @return 是否成功写入（未配置备份文件夹或写入失败时为 false，调用方据此提示用户）。
     */
    fun exportSnapshot(ctx: Context, internalDb: File, targetUri: Uri?): Boolean {
        val uri = targetUri ?: return false
        val src = File(internalDb.path)
        if (!src.exists()) return false
        synchronized(exportLock) {
            val root = DocumentFile.fromTreeUri(ctx, uri) ?: return false
            val existing = root.listFiles().firstOrNull { SNAPSHOT_REGEX.matches(it.name.orEmpty()) }
            val target = existing
                ?: root.createFile("application/octet-stream", DB_NAME + SNAPSHOT_SUFFIX)
                ?: return false
            val written = runCatching {
                var ok = false
                src.inputStream().use { input ->
                    ctx.contentResolver.openOutputStream(target.uri)?.use { output ->
                        input.copyTo(output)
                        ok = true
                    }
                }
                ok
            }.getOrDefault(false)
            // 只删本次自己刚创建的半截文件
            if (!written && existing == null) runCatching { target.delete() }
            return written
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
        // 回滚保护：先把当前主库（含 sidecar）快照到 .import_bak——导入中途失败时旧库已被
        // 部分覆盖，没有快照就还原不回来，应用数据会停在「半新半旧」的不可用状态。
        // 快照不完整（IO 异常）直接放弃导入，绝不覆盖现有数据。
        val backupBase = internalDb.path + ".import_bak"
        val backedUp = mutableSetOf<String>()
        val snapOk = runCatching {
            for (suffix in SUFFIXES) {
                val cur = File(internalDb.path + suffix)
                if (cur.exists()) {
                    cur.copyTo(File(backupBase + suffix), overwrite = true)
                    backedUp.add(suffix)
                }
            }
        }.isSuccess
        if (!snapOk) return false
        val copied = runCatching {
            for (suffix in SUFFIXES) {
                val target = File(internalDb.path + suffix)
                target.parentFile?.mkdirs()
                if (suffix.isEmpty()) {
                    val input = ctx.contentResolver.openInputStream(src.uri)
                        ?: error("主库输入流不可用")
                    input.use { ins -> target.outputStream().use { ins.copyTo(it) } }
                } else {
                    // -wal / -shm 为可选：存在则复制，不存在则清掉内部残留，让下次打开重建
                    val ext = findSidecar(root, suffix)
                    if (ext != null) {
                        val input = ctx.contentResolver.openInputStream(ext.uri)
                            ?: error("附属文件输入流不可用")
                        input.use { ins -> target.outputStream().use { ins.copyTo(it) } }
                    } else {
                        target.delete()
                    }
                }
            }
            true
        }.getOrDefault(false)
        if (!copied) {
            // 还原：快照过的用快照覆盖回去；原本不存在的 sidecar 删掉半截残留
            for (suffix in SUFFIXES) {
                val target = File(internalDb.path + suffix)
                val bak = File(backupBase + suffix)
                runCatching {
                    if (suffix in backedUp) bak.copyTo(target, overwrite = true) else target.delete()
                }
            }
            return false
        }
        for (suffix in SUFFIXES) runCatching { File(backupBase + suffix).delete() }
        return true
    }

    /**
     * 参与「空数据护栏」统计的用户数据表。
     *
     * **新增任何用户数据表都必须同步加进来**，否则在该表有数据、其他表为空时，
     * 护栏会把应用/备份误判成空（历史上就漏过周期管家）。
     */
    private val DATA_TABLES = listOf(
        "events", "folders", "vault_events", "vault_folders",
        "cycle_logs", "cycle_notes"
    )

    /**
     * 统计一个 SQLite 数据库文件中的用户数据行数（见 [DATA_TABLES]，含倒数日、文件夹、保险箱、周期管家）。
     * 以只读方式打开，逐表计数，用于判断数据库是否为空（避免用空库覆盖有数据的备份）。
     *
     * 返回 -1 表示「无法判定」（文件打不开 / 表清单读不出 / 现存表计数失败）——调用方必须按「有数据」保守处理。
     * 表清单里但不存在的表按 0 计：旧版备份缺少后来新增的表（如 v1.7 备份没有 cycle_notes）属正常情况，
     * 不能与「读库失败」混为一谈，否则老备份会被永远当成「无法判定」而拦下正常的空库初始化。
     */
    fun countDataRows(dbFile: File): Int {
        if (!dbFile.exists()) return 0
        return try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // 先取现存表清单：读不出表清单 = 无法判定，直接 -1
                val existing = runCatching {
                    val names = mutableSetOf<String>()
                    db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                        while (c.moveToNext()) c.getString(0)?.let { names.add(it) }
                    }
                    names
                }.getOrNull() ?: return -1
                var total = 0
                for (tbl in DATA_TABLES) {
                    if (tbl !in existing) continue   // 旧备份缺表：按 0 计，不算失败
                    // 现存表读不出来才视为「无法判定」：绝不能把读库失败当成 0 行，
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
        // 随机后缀：恢复弹窗与自动备份护栏同时探测时不再互踩同一临时文件
        val tmp = File(ctx.cacheDir, "daymate_probe_" + System.nanoTime() + ".db")
        return try {
            // 主文件复制失败（拿不到流 / IO 异常）→ 返回 -1，绝不按 0 行放行
            var copied = false
            ctx.contentResolver.openInputStream(src.uri)?.use { ins ->
                tmp.outputStream().use { ins.copyTo(it) }
                copied = true
            }
            if (!copied) {
                deleteProbeTemps(tmp)
                return -1
            }
            for (suffix in listOf("-wal", "-shm")) {
                findSidecar(root, suffix)?.let { ext ->
                    ctx.contentResolver.openInputStream(ext.uri)?.use { ins ->
                        File(tmp.path + suffix).outputStream().use { ins.copyTo(it) }
                    }
                }
            }
            countDataRows(tmp).also { deleteProbeTemps(tmp) }
        } catch (_: Throwable) {
            deleteProbeTemps(tmp)
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

    private fun deleteProbeTemps(tmp: File) {
        for (suffix in listOf("", "-wal", "-shm")) {
            File(tmp.path + suffix).delete()
        }
    }
}
