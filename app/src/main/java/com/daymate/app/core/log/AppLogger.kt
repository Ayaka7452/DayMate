package com.ayaka7452.daymate.core.log

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用级诊断日志：记录全 app 的关键操作链路——启动/崩溃、小组件、备份与云同步、
 * 检查更新与下载安装、节日数据下载、数据维护（体检/修复）、Vault、事件增删改等，
 * 用于远程排查用户遇到的问题（如 v1.17.7 排查 OriginOS 无法添加小组件）。
 *
 *  - **新用户默认关闭**，设置页（「诊断日志」分区）可开启/查看/导出/清除；
 *  - 旧版（v1.17.7）的小组件日志开关（widget_prefs/widget_log_enabled）一次性迁移，
 *    已开过小组件日志的老用户不会因改名被静默关掉；
 *  - 日志仅写应用私有目录 filesDir/diag_log/diag_log.txt，不联网不上传；
 *  - 超 1MB 自动轮转为 diag_log.old.txt（最多保留一份），不会无限增长；
 *  - 开关存 SharedPreferences：小组件渲染路径要无阻塞读取，不走 DataStore；
 *    写入全程 runCatching，日志任何异常都不影响业务本身。
 *
 * 两种调用形态：
 *  - `AppLogger.log(tag, msg)`：不传 Context，使用 [init] 存下的应用级 Context
 *    （DayMateApp.onCreate 里初始化；未初始化时静默跳过）。仓库/网络等无 Context 层用这个。
 *  - `AppLogger.log(ctx, tag, msg)`：小组件等可能早于任意状态的路径用这个，永不为空。
 */
object AppLogger {
    private const val KEY_ENABLED = "diag_log_enabled"
    private const val LEGACY_PREFS = "widget_prefs"
    private const val LEGACY_KEY = "widget_log_enabled"
    private const val DIR = "diag_log"
    private const val FILE = "diag_log.txt"
    private const val MAX_BYTES = 1024L * 1024

    /** [init] 存下的应用级 Context；log(tag, msg) 无 Context 形态依赖它。 */
    @Volatile
    private var appContext: Context? = null

    /** DayMateApp.onCreate 最先调用：为无 Context 的调用方（仓库/网络层）绑定应用级 Context。 */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("diag_prefs", Context.MODE_PRIVATE)

    /**
     * 开关状态；**新用户默认关闭**。
     * 首次读取时做旧开关迁移：v1.17.7 的「小组件诊断日志」开关若存在，
     * 原样迁过来（开过就是开、没开过就是关），迁移后立刻落盘，旧键不再参与判断。
     */
    fun isEnabled(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (p.contains(KEY_ENABLED)) return p.getBoolean(KEY_ENABLED, false)
        val legacy = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(LEGACY_KEY, false)
        p.edit().putBoolean(KEY_ENABLED, legacy).apply()
        return legacy
    }

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
        log(ctx, "Logger", if (enabled) "诊断日志已开启" else "诊断日志已关闭")
    }

    fun logFile(ctx: Context): File = File(ctx.filesDir, "$DIR/$FILE")

    /** 清空日志：当前文件与轮转文件一并删除，下次写入自动重建。 */
    fun clear(ctx: Context) {
        runCatching {
            val dir = File(ctx.filesDir, DIR)
            File(dir, FILE).delete()
            File(dir, "diag_log.old.txt").delete()
        }
        log(ctx, "Logger", "诊断日志已清除")
    }

    /**
     * 读取日志尾部供 app 内查看：最多 [maxLines] 行（从最新往前截取），
     * [truncated] 表示是否发生了截断（完整内容请导出）。
     */
    fun readTail(ctx: Context, maxLines: Int = 800): Pair<String, Boolean> {
        val f = logFile(ctx)
        if (!f.exists()) return "" to false
        return runCatching {
            val lines = f.readLines()
            if (lines.size <= maxLines) lines.joinToString("\n") to false
            else lines.takeLast(maxLines).joinToString("\n") to true
        }.getOrDefault("" to false)
    }

    /** 追加一行日志（自动带时间戳）。任何异常吞掉，绝不影响调用方。 */
    fun log(ctx: Context, tag: String, message: String) {
        runCatching {
            if (!isEnabled(ctx)) return
            write(ctx.applicationContext, tag, message)
        }
    }

    /** 无 Context 形态：见类注释。未 init 或读取开关失败时静默跳过。 */
    fun log(tag: String, message: String) {
        val ctx = appContext ?: return
        runCatching {
            if (!isEnabled(ctx)) return
            write(ctx, tag, message)
        }
    }

    fun logError(ctx: Context, tag: String, message: String, t: Throwable? = null) {
        val st = t?.let { android.util.Log.getStackTraceString(it) }.orEmpty()
        log(ctx, tag, if (st.isEmpty()) message else "$message\n  $st")
    }

    fun logError(tag: String, message: String, t: Throwable? = null) {
        val st = t?.let { android.util.Log.getStackTraceString(it) }.orEmpty()
        log(tag, if (st.isEmpty()) message else "$message\n  $st")
    }

    private fun write(ctx: Context, tag: String, message: String) {
        val dir = File(ctx.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        val f = logFile(ctx)
        if (f.length() > MAX_BYTES) {
            val old = File(dir, "diag_log.old.txt")
            if (old.exists()) old.delete()
            f.renameTo(old)
        }
        val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        FileWriter(f, true).use { it.write("$ts [$tag] $message\n") }
    }
}
