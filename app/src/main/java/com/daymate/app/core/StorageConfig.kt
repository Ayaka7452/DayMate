package com.ayaka7452.daymate.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

/**
 * 数据库存储位置配置与工具。
 *
 * 设计说明（合理性）：
 * - 内部存储 = 应用沙盒（默认，/data/data/.../databases），无需额外权限；
 * - 外部存储 = 用户通过 SAF 选择的目录，db 文件直接落在所选目录。
 *   现代 Android（作用域存储）下，Room 的 createFromFile 走原始文件 IO，
 *   直接访问共享存储路径必须持有 MANAGE_EXTERNAL_STORAGE（所有文件访问）权限，
 *   否则系统会拒绝。因此切换到外部存储前会引导用户授予该权限。
 * - 用 SharedPreferences（同步读取）保存位置，便于 Application.onCreate 时同步决定数据库路径。
 */
object StorageConfig {
    const val INTERNAL = "internal"
    const val EXTERNAL = "external"

    private const val PREFS = "daymate_storage"
    private const val KEY_MODE = "mode"
    private const val KEY_PATH = "external_path"
    private const val KEY_URI = "external_uri"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(ctx: Context): String = prefs(ctx).getString(KEY_MODE, INTERNAL) ?: INTERNAL
    fun externalPath(ctx: Context): String? = prefs(ctx).getString(KEY_PATH, null)
    fun externalUri(ctx: Context): String? = prefs(ctx).getString(KEY_URI, null)

    /** 是否已初始化（首次启动向导已完成文件夹选择）。以「外部路径是否已记录」判定。 */
    fun isConfigured(ctx: Context): Boolean = externalPath(ctx) != null

    fun setExternal(ctx: Context, uri: String, path: String) {
        prefs(ctx).edit()
            .putString(KEY_MODE, EXTERNAL)
            .putString(KEY_PATH, path)
            .putString(KEY_URI, uri)
            .apply()
    }

    fun setInternal(ctx: Context) {
        prefs(ctx).edit()
            .putString(KEY_MODE, INTERNAL)
            .remove(KEY_PATH)
            .remove(KEY_URI)
            .apply()
    }

    /** 返回主库 db 文件；内部模式返回 null（交给 Room 用沙盒默认路径）。 */
    fun mainDbFile(ctx: Context): File? {
        val path = externalPath(ctx) ?: return null
        return File(path, "daymate.db")
    }

    /**
     * 把 SAF tree Uri 解析为真实文件系统目录路径。
     * 主存储（primary:）可可靠解析；其它可识别卷也会尝试 /storage/<volume>。
     * 解析不到返回 null（调用方应提示用户换一个位置）。
     */
    fun treeUriToPath(uri: Uri): String? {
        val id = DocumentsContract.getTreeDocumentId(uri)
        return if (id.startsWith("primary:")) {
            val rel = id.substring("primary:".length).replace(':', '/')
            Environment.getExternalStorageDirectory().absolutePath + "/" + rel
        } else {
            val vol = id.substringBefore(':')
            val rel = id.substringAfter(':').replace(':', '/')
            val f = File("/storage/$vol/$rel")
            if (f.exists() && f.isDirectory) f.absolutePath else null
        }
    }

    /** 复制数据库主文件及其 -wal / -shm 附属文件。 */
    fun copyDatabase(from: File, to: File) {
        to.parentFile?.mkdirs()
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(from.path + suffix)
            val dst = File(to.path + suffix)
            if (src.exists()) src.copyTo(dst, overwrite = true)
        }
    }

    /**
     * 判断文件是否为合法的 SQLite 数据库（读取文件头 "SQLite format 3"）。
     * 仅做轻量校验，真正的打开与校验交给 Room.createFromFile。
     */
    fun isReadableSqlite(file: File): Boolean {
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
     * 是否已具备访问外部存储目录的权限。
     * - Android 11 (R, API 30) 及以上：需要 MANAGE_EXTERNAL_STORAGE（所有文件访问）。
     * - Android 10 及以下：作用域存储前的传统全量访问，挂载即可，无需额外授权。
     */
    fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /** 打开系统「所有文件访问」授权页，直接定位到本应用。 */
    fun allFilesAccessIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }

    /** 重启到主页面（数据位置切换后刷新全部界面与容器）。 */
    fun restartToHome(ctx: Context) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        (ctx as? Activity)?.finishAffinity()
    }
}
