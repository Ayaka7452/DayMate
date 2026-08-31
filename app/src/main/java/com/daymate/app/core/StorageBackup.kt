package com.ayaka7452.daymate.core

import android.content.Context
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
 * 注意：SQLite 在数据库打开时会使用 WAL（-wal / -shm 文件）。调用 [exportInternal] /
 * [importExternal] 前应确保主库已关闭（close 容器），以触发 WAL checkpoint、让数据落盘到
 * 主文件 daymate.db；本工具只负责文件的复制，不再额外维护 WAL 状态。
 */
object StorageBackup {
    private const val DB_NAME = "daymate.db"
    private val SUFFIXES = listOf("", "-wal", "-shm")

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
    fun exportInternal(ctx: Context, internalDb: File) {
        val uri = StorageConfig.backupUri(ctx) ?: return
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return
        for (suffix in SUFFIXES) {
            val src = File(internalDb.path + suffix)
            if (!src.exists()) continue
            val name = DB_NAME + suffix
            // 覆盖前先删除旧文件，避免 SAF 自动重命名为 "daymate.db (1)"
            root.findFile(name)?.delete()
            val target = root.createFile("application/octet-stream", name) ?: continue
            ctx.contentResolver.openInputStream(src)?.use { input ->
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
    fun importExternal(ctx: Context, internalDb: File): Boolean {
        val uri = StorageConfig.backupUri(ctx) ?: return false
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
}
