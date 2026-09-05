package com.ayaka7452.daymate.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.MainActivity
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 桌面小组件：显示最近的倒数日事件（原生 Material 风格卡片）。
 * - 有未到期的取最近一个（还有 N 天）；全部已过则取最近过的（已过 N 天）。
 * - 每 30 分钟系统定时刷新一次（覆盖跨天变化）；应用内任何数据变更也会立即刷新。
 * - 点击卡片打开应用。
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                renderAll(context.applicationContext, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private data class WidgetModel(
            val title: String,
            val subtitle: String,
            val number: String,
            val unit: String
        )

        /** 应用内数据变更时调用：立即刷新所有已添加到桌面的小组件。 */
        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val manager = AppWidgetManager.getInstance(appContext)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(appContext, CountdownWidgetProvider::class.java)
                    )
                    if (ids.isNotEmpty()) renderAll(appContext, manager, ids)
                }
            }
        }

        private suspend fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val container = (context.applicationContext as? DayMateApp)?.container ?: return
            val model = runCatching { buildModel(container) }.getOrNull()
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context, model))
            }
        }

        private suspend fun buildModel(container: AppContainer): WidgetModel? {
            val events = container.eventRepository.observeAll().first()
            if (events.isEmpty()) return null
            val today = LocalDate.now().toEpochDay()
            // 未到期取最近的一个；全部已过则取离今天最近的过去事件
            val upcoming = events
                .filter { it.targetDateEpochDay - today >= 0 }
                .minByOrNull { it.targetDateEpochDay }
            val picked = upcoming ?: events.maxByOrNull { it.targetDateEpochDay } ?: return null
            val diff = (picked.targetDateEpochDay - today).toInt()
            val isFuture = diff >= 0
            val dateStr = LocalDate.ofEpochDay(picked.targetDateEpochDay)
                .format(DateTimeFormatter.ofPattern("yyyy/M/d"))
            return WidgetModel(
                title = picked.title,
                subtitle = "$dateStr · ${if (isFuture) "还有" else "已过"}",
                number = abs(diff).toString(),
                unit = "天"
            )
        }

        private fun buildViews(context: Context, model: WidgetModel?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)
            if (model == null) {
                views.setTextViewText(R.id.widget_title, "DayMate")
                views.setTextViewText(R.id.widget_subtitle, "暂无倒数日")
                views.setTextViewText(R.id.widget_days_number, "")
                views.setTextViewText(R.id.widget_days_unit, "")
            } else {
                views.setTextViewText(R.id.widget_title, model.title)
                views.setTextViewText(R.id.widget_subtitle, model.subtitle)
                views.setTextViewText(R.id.widget_days_number, model.number)
                views.setTextViewText(R.id.widget_days_unit, model.unit)
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
