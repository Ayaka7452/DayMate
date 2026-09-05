package com.ayaka7452.daymate.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.math.abs

/**
 * 2×2 方形小组件「自动」模式的多事件列表数据源：
 * 显示距离今天最近的若干个事件（未到期优先、过去事件按远近混排），点击行直达对应事件详情。
 */
class CountdownWidgetListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ListFactory(applicationContext, intent)

    class ListFactory(
        private val context: Context,
        intent: Intent
    ) : RemoteViewsFactory {

        private data class Row(val title: String, val daysText: String, val isPast: Boolean, val eventId: Long)

        @Suppress("unused")
        private val appWidgetId: Int =
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        private val rows = mutableListOf<Row>()
        private var empty = false

        override fun onCreate() {}

        override fun onDataSetChanged() {
            rows.clear()
            empty = false
            val container = (context.applicationContext as? DayMateApp)?.container ?: run { empty = true; return }
            val events = runCatching {
                runBlocking { container.eventRepository.observeAll().first() }
            }.getOrDefault(emptyList())
            if (events.isEmpty()) {
                empty = true
                return
            }
            val today = LocalDate.now().toEpochDay()
            // 按与今天的距离取最近的最多 4 个（自动模式无固定事件概念，全部事件参与）
            events.sortedBy { abs(it.targetDateEpochDay - today) }
                .take(4)
                .forEach { e ->
                    val diff = (e.targetDateEpochDay - today).toInt()
                    rows.add(
                        Row(
                            title = e.title,
                            daysText = if (diff >= 0) "还有 $diff 天" else "已过 ${-diff} 天",
                            isPast = diff < 0,
                            eventId = e.id
                        )
                    )
                }
        }

        override fun onDestroy() {
            rows.clear()
        }

        override fun getCount(): Int = if (empty) 0 else rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_list_row)
            val dark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val titleColor = if (dark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val accentColor = if (dark) 0xFF5AA9F0.toInt() else 0xFF1E78D0.toInt()

            val row = rows.getOrNull(position) ?: return views
            views.setTextViewText(R.id.row_title, row.title)
            views.setTextViewText(R.id.row_days, row.daysText)
            views.setTextColor(R.id.row_title, titleColor)
            views.setTextColor(R.id.row_days, if (row.isPast) titleColor else accentColor)
            // 配合渲染层的 PendingIntentTemplate：整行点击携带 eventId 打开事件详情
            views.setOnClickFillInIntent(
                R.id.row_root,
                Intent().putExtra("eventId", row.eventId)
            )
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
