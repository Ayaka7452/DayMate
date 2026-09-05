package com.ayaka7452.daymate.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.ayaka7452.daymate.DayMateApp
import com.ayaka7452.daymate.MainActivity
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.data.db.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 小组件偏好（SharedPreferences，供 Widget 进程同步读取）。
 * 一切配置按小组件实例隔离：每个桌面小组件各自绑定事件与透明度，
 * 通过配置页（添加时弹出，或长按小组件重新打开）设置，互不影响。
 */
object WidgetPrefs {
    private const val FILE = "widget_prefs"
    private const val KEY_OPACITY = "card_opacity" // 旧全局键，仅迁移用
    private const val KEY_DEFAULT_EVENT = "default_event_id" // 旧全局键，仅迁移用
    private const val PREFIX_WIDGET_EVENT = "event_for_widget_"
    private const val PREFIX_WIDGET_OPACITY = "opacity_for_widget_"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * 单个小组件的不透明度 5–100（默认 100）。
     * 配置页可改，仅对该组件生效。
     */
    fun opacityFor(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt(PREFIX_WIDGET_OPACITY + appWidgetId, 100).coerceIn(5, 100)

    fun setOpacityFor(ctx: Context, appWidgetId: Int, value: Int) {
        prefs(ctx).edit().putInt(PREFIX_WIDGET_OPACITY + appWidgetId, value.coerceIn(5, 100)).apply()
    }

    /** 单个小组件绑定的事件 id；0 = 自动（最近倒数日，2×2 显示多事件列表）。 */
    fun eventForWidget(ctx: Context, appWidgetId: Int): Long =
        prefs(ctx).getLong(PREFIX_WIDGET_EVENT + appWidgetId, 0L)

    fun setEventForWidget(ctx: Context, appWidgetId: Int, eventId: Long) {
        prefs(ctx).edit().putLong(PREFIX_WIDGET_EVENT + appWidgetId, eventId).apply()
    }

    /** 小组件被移除时清理其全部配置。 */
    fun clearWidget(ctx: Context, appWidgetId: Int) {
        prefs(ctx).edit()
            .remove(PREFIX_WIDGET_EVENT + appWidgetId)
            .remove(PREFIX_WIDGET_OPACITY + appWidgetId)
            .apply()
    }

    /**
     * 一次性迁移：把旧版的「全局透明度 / 全局默认事件」写入每个现有小组件的独立配置，
     * 然后删除旧全局键。应用启动时调用；迁移后 prefs 里没有旧键，重复调用为空操作。
     */
    fun migrateGlobalPrefs(context: Context) {
        val p = prefs(context)
        val legacyEvent = p.getLong(KEY_DEFAULT_EVENT, 0L)
        val legacyOpacity = p.getInt(KEY_OPACITY, -1)
        if (legacyEvent == 0L && legacyOpacity == -1) return
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            for (cls in listOf(
                CountdownWidgetProvider::class.java,
                CountdownWidgetSmallProvider::class.java,
                CountdownWidgetSquareProvider::class.java
            )) {
                for (id in manager.getAppWidgetIds(ComponentName(context, cls))) {
                    if (legacyEvent != 0L && !p.contains(PREFIX_WIDGET_EVENT + id)) {
                        p.edit().putLong(PREFIX_WIDGET_EVENT + id, legacyEvent).apply()
                    }
                    if (legacyOpacity != -1 && !p.contains(PREFIX_WIDGET_OPACITY + id)) {
                        p.edit().putInt(PREFIX_WIDGET_OPACITY + id, legacyOpacity).apply()
                    }
                }
            }
        }
        p.edit().remove(KEY_DEFAULT_EVENT).remove(KEY_OPACITY).apply()
    }
}

/**
 * 小组件统一渲染器：三种尺寸（宽版 3×1 / 迷你 2×1 / 方形 2×2）共用同一套
 * 事件选取（小组件绑定 > 全局默认 > 自动最近）、深浅色适配与透明度逻辑。
 *  - 深浅色跟随系统（launcher 亮色亮卡片、深色深卡片），系统切换后立即重绘；
 *  - 卡片背景用 View.setAlpha 控制不透明度（文字保持在独立层不受影响）；
 *  - 方形小组件在「自动」模式（未绑定事件且未设全局默认）下显示多事件列表
 *    （RemoteViews ListView，最多 4 个最近事件）；绑定/默认事件后回到单事件大卡片。
 */
object WidgetRenderer {

    private data class WidgetModel(
        val title: String,
        val subtitle: String,
        val number: String,
        val unit: String
    )

    enum class Style { WIDE, SMALL, SQUARE }

    /** 三种小组件 provider 及对应布局样式。 */
    private val providers: List<Pair<Class<out AppWidgetProvider>, Style>> = listOf(
        CountdownWidgetProvider::class.java to Style.WIDE,
        CountdownWidgetSmallProvider::class.java to Style.SMALL,
        CountdownWidgetSquareProvider::class.java to Style.SQUARE
    )

    /** 应用内数据变更 / 设置变更时调用：立即刷新所有已添加到桌面的小组件。 */
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val manager = AppWidgetManager.getInstance(appContext)
                // 先滚动循环/节日跟随事件（跨天午夜/开机路径依赖此调用把已过的日期锚定到下一次）
                val container = (appContext as? DayMateApp)?.container
                container?.eventRepository?.rollForwardRepeating(container.festivalRepository)
                for ((cls, style) in providers) {
                    val ids = manager.getAppWidgetIds(ComponentName(appContext, cls))
                    if (ids.isNotEmpty()) renderAll(appContext, manager, ids, style)
                }
            }
        }
    }

    /** 系统深浅色切换后由 CONFIGURATION_CHANGED 广播触发。 */
    fun onSystemConfigurationChanged(context: Context) {
        refreshAll(context)
    }

    suspend fun renderOne(context: Context, manager: AppWidgetManager, appWidgetId: Int, style: Style) {
        val container = (context.applicationContext as? DayMateApp)?.container ?: return
        val pair = buildViewsForWidget(context, container, appWidgetId, style)
        manager.updateAppWidget(appWidgetId, pair.first)
        if (pair.second) manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    suspend fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray, style: Style) {
        val container = (context.applicationContext as? DayMateApp)?.container ?: return
        for (id in ids) {
            val pair = buildViewsForWidget(context, container, id, style)
            manager.updateAppWidget(id, pair.first)
            if (pair.second) manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }

    /**
     * 构建单个小组件的 RemoteViews。
     * 返回 (views, isList)；isList=true 时调用方需 notifyAppWidgetViewDataChanged 刷新列表数据。
     */
    private suspend fun buildViewsForWidget(
        context: Context,
        container: AppContainer,
        appWidgetId: Int,
        style: Style
    ): Pair<RemoteViews, Boolean> {
        val events = runCatching { container.eventRepository.observeAll().first() }.getOrDefault(emptyList())
        // 今日节假日信息（来自已下载的节假日缓存；未下载时为 null，角标隐藏）
        val festival = runCatching {
            container.festivalRepository.todayInfo(LocalDate.now())
        }.getOrNull()
        // 是否处于「固定事件」模式：该组件在配置页绑定了具体事件
        val bound = WidgetPrefs.eventForWidget(context, appWidgetId) != 0L
        if (style == Style.SQUARE && !bound) {
            return buildListViews(context, appWidgetId, festival) to true
        }
        val picked = pickEvent(events, context, appWidgetId)
        val model = picked?.let { buildModel(it) }
        return buildViews(context, appWidgetId, model, style, picked?.id, festival) to false
    }

    /** 事件选取优先级：该小组件绑定的事件 > 自动（最近未到期，否则最近已过）。 */
    private fun pickEvent(events: List<EventEntity>, context: Context, appWidgetId: Int): EventEntity? {
        if (events.isEmpty()) return null
        WidgetPrefs.eventForWidget(context, appWidgetId)
            .takeIf { it != 0L }
            ?.let { id -> events.firstOrNull { it.id == id } }
            ?.let { return it }
        val today = LocalDate.now().toEpochDay()
        return events.filter { it.targetDateEpochDay - today >= 0 }
            .minByOrNull { it.targetDateEpochDay }
            ?: events.maxByOrNull { it.targetDateEpochDay }
    }

    private fun buildModel(picked: EventEntity): WidgetModel {
        val today = LocalDate.now()
        val diff = (picked.targetDateEpochDay - today.toEpochDay()).toInt()
        val isFuture = diff >= 0
        val dateStr = LocalDate.ofEpochDay(picked.targetDateEpochDay)
            .format(DateTimeFormatter.ofPattern("yyyy/M/d"))

        // 数字与单位：跟随事件的显示单位（月/年不足 1 时自动退回更小单位）
        var number = abs(diff).toString()
        var unit = "天"
        if (!isFuture || diff > 0) {
            val period = if (isFuture) Period.between(today, LocalDate.ofEpochDay(picked.targetDateEpochDay))
            else Period.between(LocalDate.ofEpochDay(picked.targetDateEpochDay), today)
            val totalMonths = period.years * 12L + period.months
            when (picked.displayUnit) {
                "MONTH" -> if (totalMonths > 0) {
                    number = totalMonths.toString(); unit = "个月"
                }
                "YEAR" -> when {
                    period.years > 0 -> { number = period.years.toString(); unit = "年" }
                    totalMonths > 0 -> { number = totalMonths.toString(); unit = "个月" }
                }
            }
        }
        return WidgetModel(
            title = picked.title,
            subtitle = "$dateStr · ${if (isFuture) "还有" else "已过"}",
            number = number,
            unit = unit
        )
    }

    private fun isDarkTheme(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * 点击小组件打开对应事件详情：
     *  - eventId > 0 时携带 extra（MainActivity 深链转跳 event_form 详情页）；
     *  - 无事件（空状态）时仅打开主页。
     * requestCode 用 eventId 区分，避免不同事件的 PendingIntent 相互覆盖。
     */
    private fun openEventPendingIntent(context: Context, eventId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (eventId != null && eventId > 0) putExtra("eventId", eventId)
        }
        return PendingIntent.getActivity(
            context, (eventId ?: 0L).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 2×2「自动」模式的多事件列表视图（RemoteViews ListView）。 */
    private fun buildListViews(
        context: Context,
        appWidgetId: Int,
        festival: com.ayaka7452.daymate.data.festival.FestivalDay?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_countdown_list)
        val dark = isDarkTheme(context)
        views.setInt(
            R.id.widget_card, "setBackgroundResource",
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg
        )
        views.setFloat(R.id.widget_card, "setAlpha", WidgetPrefs.opacityFor(context, appWidgetId) / 100f)
        views.setTextColor(R.id.widget_empty, if (dark) 0xFF9B9498.toInt() else 0xFF7A7570.toInt())
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        // 行点击：模板 PendingIntent + 工厂里的 FillInIntent（各行携带自己的 eventId）
        views.setPendingIntentTemplate(R.id.widget_list, openEventPendingIntent(context, null))
        applyFestivalBadge(views, festival)
        return views
    }

    /** 节日角标：今天恰逢法定节假日显示绿色「休」，调休上班日显示橙色「班」；无数据隐藏。 */
    private fun applyFestivalBadge(views: RemoteViews, festival: com.ayaka7452.daymate.data.festival.FestivalDay?) {
        if (festival == null) {
            views.setViewVisibility(R.id.widget_festival_badge, android.view.View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_festival_badge, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_festival_badge, if (festival.isOffDay) "休" else "班")
        views.setInt(
            R.id.widget_festival_badge, "setBackgroundResource",
            if (festival.isOffDay) R.drawable.widget_badge_off else R.drawable.widget_badge_work
        )
    }

    private fun buildViews(
        context: Context,
        appWidgetId: Int,
        model: WidgetModel?,
        style: Style,
        eventId: Long? = null,
        festival: com.ayaka7452.daymate.data.festival.FestivalDay? = null
    ): RemoteViews {
        val layout = when (style) {
            Style.WIDE -> R.layout.widget_countdown
            Style.SMALL -> R.layout.widget_countdown_small
            Style.SQUARE -> R.layout.widget_countdown_square
        }
        val views = RemoteViews(context.packageName, layout)

        // 深浅色跟随系统；卡片背景与文字颜色整套切换
        val dark = isDarkTheme(context)
        val titleColor = if (dark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        val subColor = if (dark) 0xFF9B9498.toInt() else 0xFF7A7570.toInt()
        val accentColor = if (dark) 0xFF5AA9F0.toInt() else 0xFF1E78D0.toInt()
        views.setInt(
            R.id.widget_card, "setBackgroundResource",
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg
        )
        views.setFloat(R.id.widget_card, "setAlpha", WidgetPrefs.opacityFor(context, appWidgetId) / 100f)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextColor(R.id.widget_subtitle, subColor)
        views.setTextColor(R.id.widget_days_number, accentColor)
        views.setTextColor(R.id.widget_days_unit, subColor)

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

        // 整卡点击直达当前显示事件的详情页
        views.setOnClickPendingIntent(R.id.widget_root, openEventPendingIntent(context, eventId))
        applyFestivalBadge(views, festival)
        return views
    }
}
