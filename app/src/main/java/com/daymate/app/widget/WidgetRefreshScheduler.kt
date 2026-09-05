package com.ayaka7452.daymate.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 小组件跨天精确刷新：在每天本地午夜（+5 秒）安排一次闹钟广播，
 * 触发后重绘所有小组件并续订下一天——保证「还有 N 天」在跨天瞬间即变，
 * 不必等 30 分钟的 updatePeriodMillis 轮询。
 *
 * 精确闹钟在 Android 12+（targetSdk 33+ 默认不授予 SCHEDULE_EXACT_ALARM）会退化为
 * setAndAllowWhileIdle（误差分钟级，Doze 下也保证触发），对跨天显示足够。
 */
object WidgetRefreshScheduler {

    const val ACTION_MIDNIGHT_REFRESH = "com.ayaka7452.daymate.widget.MIDNIGHT_REFRESH"
    private const val REQUEST_CODE = 1001

    /** 安排「下一个午夜」的刷新闹钟；重复调用安全（FLAG_UPDATE_CURRENT 覆盖旧闹钟）。 */
    fun scheduleNextMidnight(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerAt = cal.timeInMillis
        val pi = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            Intent(appContext, CountdownWidgetProvider::class.java).setAction(ACTION_MIDNIGHT_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }
}
