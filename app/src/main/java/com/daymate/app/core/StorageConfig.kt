package com.ayaka7452.daymate.core

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri

/**
 * 数据库存储位置配置与工具（路线 A：主库始终在应用内部沙盒，
 * 用户通过 SAF 选择的文件夹仅作为「备份/导出」目标，全程不需要任何存储权限）。
 *
 * 设计说明：
 * - 主库 = 应用沙盒（/data/data/.../databases/daymate.db），Room 默认路径，无需权限；
 * - 备份文件夹 = 用户用 SAF（OpenDocumentTree）选择的目录，仅用于把主库导出/导入，
 *   通过系统授予的持久化 URI 权限访问，无需 MANAGE_EXTERNAL_STORAGE 等任何存储权限。
 * - 用 SharedPreferences（同步读取）保存备份文件夹的 tree Uri。
 */
object StorageConfig {
    private const val PREFS = "daymate_storage"
    private const val KEY_BACKUP_URI = "backup_uri"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 已配置的备份文件夹 tree Uri；未配置返回 null。 */
    fun backupUri(ctx: Context): Uri? {
        val s = prefs(ctx).getString(KEY_BACKUP_URI, null) ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    /** 是否已配置备份文件夹。 */
    fun isBackupConfigured(ctx: Context): Boolean = backupUri(ctx) != null

    /** SAF 持久化 URI 权限标志（读写备份目录所需）。 */
    private val BACKUP_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** 向系统申请对所选备份目录的持久化读写权限（SAF 模型核心）。 */
    fun takeBackupPermission(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, BACKUP_FLAGS) }
    }

    /** 释放对目录的持久化权限（用户取消选择时使用，避免遗留无用授权）。 */
    fun releaseBackupPermission(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.releasePersistableUriPermission(uri, BACKUP_FLAGS) }
    }

    /**
     * 保存备份文件夹 Uri，并向系统申请对该目录的持久化读写权限。
     * 这是 SAF 模型的核心：拿到持久化 URI 权限后即可免存储权限访问该目录，
     * 即使应用重启/卸载重装（在同一份系统授权下）仍能访问。
     */
    fun setBackupFolder(ctx: Context, uri: Uri) {
        takeBackupPermission(ctx, uri)
        prefs(ctx).edit().putString(KEY_BACKUP_URI, uri.toString()).apply()
    }

    /** 清除已配置的备份文件夹。 */
    fun clearBackupFolder(ctx: Context) {
        prefs(ctx).edit().remove(KEY_BACKUP_URI).apply()
    }

    /**
     * 把 tree Uri 转成可读路径（仅用于界面展示）。
     * 例如 content://.../tree/primary:DayMate -> /storage/emulated/0/DayMate
     */
    fun displayPath(uri: Uri?): String {
        if (uri == null) return "未设置"
        val seg = uri.lastPathSegment ?: return uri.toString()
        return if (seg.startsWith("primary:")) {
            "/storage/emulated/0/" + seg.substring("primary:".length).replace(':', '/')
        } else {
            seg
        }
    }

    /** 判断文件是否为合法的 SQLite 数据库（读取文件头 "SQLite format 3"）。 */
    fun isReadableSqlite(file: java.io.File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            file.inputStream().use { ins ->
                val header = ByteArray(16)
                if (ins.read(header) != 16) return false
                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            }
        } catch (_: Throwable) { false }
    }

    /**
     * 复制数据库主文件及其 -wal / -shm 附属文件（File -> File，用于内部临时操作）。
     */
    fun copyDatabase(from: java.io.File, to: java.io.File) {
        to.parentFile?.mkdirs()
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = java.io.File(from.path + suffix)
            val dst = java.io.File(to.path + suffix)
            if (src.exists()) src.copyTo(dst, overwrite = true)
        }
    }

    /** 重启到主页面（备份位置切换/恢复后刷新全部界面与容器）。 */
    fun restartToHome(ctx: Context) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        (ctx as? android.app.Activity)?.finishAffinity()
    }
}
