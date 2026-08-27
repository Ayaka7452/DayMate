package com.daymate.app

import android.app.Application
import android.util.Log
import com.daymate.app.core.AppContainer
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class DayMateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashHandler()
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
