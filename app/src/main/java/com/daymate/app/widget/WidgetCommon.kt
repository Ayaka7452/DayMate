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
import com.ayaka7452.daymate.core.log.AppLogger
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.core.i18n.Tr
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
    private const val PREFIX_WIDGET_MODE = "mode_for_widget_"
    private const val PREFIX_WIDGET_SELECTED = "selected_for_widget_"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 小组件显示模式：多事件列表（默认）或固定单个事件。 */
    enum class WidgetMode { MULTI, SINGLE }

    /**
     * 单个小组件的不透明度 5–100（默认 100）。
     * 配置页可改，仅对该组件生效。
     */
    fun opacityFor(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt(PREFIX_WIDGET_OPACITY + appWidgetId, 100).coerceIn(5, 100)

    fun setOpacityFor(ctx: Context, appWidgetId: Int, value: Int) {
        prefs(ctx).edit().putInt(PREFIX_WIDGET_OPACITY + appWidgetId, value.coerceIn(5, 100)).apply()
    }

    /** 单个小组件绑定的事件 id；0 = 未绑定（单事件模式下由渲染层自动挑选）。 */
    fun eventForWidget(ctx: Context, appWidgetId: Int): Long =
        prefs(ctx).getLong(PREFIX_WIDGET_EVENT + appWidgetId, 0L)

    fun setEventForWidget(ctx: Context, appWidgetId: Int, eventId: Long) {
        prefs(ctx).edit().putLong(PREFIX_WIDGET_EVENT + appWidgetId, eventId).apply()
    }

    /**
     * 显示模式。旧版没有该键：曾绑定事件（event != 0）的老组件迁移为单事件模式，
     * 未绑定（自动）的老组件迁移为多事件模式——升级后行为与旧版一致。
     */
    fun modeFor(ctx: Context, appWidgetId: Int): WidgetMode {
        val p = prefs(ctx)
        val key = PREFIX_WIDGET_MODE + appWidgetId
        if (p.contains(key)) {
            return if (p.getInt(key, 0) == 1) WidgetMode.SINGLE else WidgetMode.MULTI
        }
        val legacyEvent = p.getLong(PREFIX_WIDGET_EVENT + appWidgetId, 0L)
        val migrated = if (legacyEvent != 0L) WidgetMode.SINGLE else WidgetMode.MULTI
        p.edit().putInt(key, if (migrated == WidgetMode.SINGLE) 1 else 0).apply()
        return migrated
    }

    fun setModeFor(ctx: Context, appWidgetId: Int, mode: WidgetMode) {
        prefs(ctx).edit().putInt(PREFIX_WIDGET_MODE + appWidgetId, if (mode == WidgetMode.SINGLE) 1 else 0).apply()
    }

    /** 多事件模式下用户勾选的事件 id 集合；空 = 自动挑选最近的若干个。 */
    fun selectedEventsFor(ctx: Context, appWidgetId: Int): Set<Long> =
        prefs(ctx).getStringSet(PREFIX_WIDGET_SELECTED + appWidgetId, emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    fun setSelectedEventsFor(ctx: Context, appWidgetId: Int, ids: Set<Long>) {
        prefs(ctx).edit()
            .putStringSet(PREFIX_WIDGET_SELECTED + appWidgetId, ids.map { it.toString() }.toSet())
            .apply()
    }

    /** 小组件被移除时清理其全部配置。 */
    fun clearWidget(ctx: Context, appWidgetId: Int) {
        prefs(ctx).edit()
            .remove(PREFIX_WIDGET_EVENT + appWidgetId)
            .remove(PREFIX_WIDGET_OPACITY + appWidgetId)
            .remove(PREFIX_WIDGET_MODE + appWidgetId)
            .remove(PREFIX_WIDGET_SELECTED + appWidgetId)
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
 * 事件选取（小组件绑定 > 自动最近）、深浅色适配与透明度逻辑。
 *  - 深浅色跟随系统（launcher 亮色亮卡片、深色深卡片），系统切换后立即重绘；
 *  - 卡片背景用 View.setAlpha 控制不透明度（文字保持在独立层不受影响）；
 *  - 多事件模式（默认，配置页可切单事件）：三种尺寸都显示多事件列表，
 *    行数由 widget 实际高度决定（每行至少 30dp，槽位上限 4），weight=1 平均分割，
 *    行间浅色分割线；RemoteViews 白名单不含裸 View，槽位与分割线均用 FrameLayout。
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
            }.onFailure {
                AppLogger.logError(appContext, "Renderer", "refreshAll 全量刷新异常", it)
            }
        }
    }

    /** 系统深浅色切换后由 CONFIGURATION_CHANGED 广播触发。 */
    fun onSystemConfigurationChanged(context: Context) {
        refreshAll(context)
    }

    suspend fun renderOne(context: Context, manager: AppWidgetManager, appWidgetId: Int, style: Style) {
        AppLogger.log(context, "Renderer", "renderOne id=$appWidgetId style=$style")
        val container = (context.applicationContext as? DayMateApp)?.container ?: run {
            AppLogger.log(context, "Renderer", "renderOne id=$appWidgetId 容器未就绪，跳过")
            return
        }
        val views = buildViewsForWidget(context, container, appWidgetId, style)
        manager.updateAppWidget(appWidgetId, views)
    }

    suspend fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray, style: Style) {
        AppLogger.log(context, "Renderer", "renderAll style=$style ids=${ids.joinToString()}")
        val container = (context.applicationContext as? DayMateApp)?.container ?: run {
            AppLogger.log(context, "Renderer", "renderAll 容器未就绪，跳过")
            return
        }
        for (id in ids) {
            try {
                val views = buildViewsForWidget(context, container, id, style)
                manager.updateAppWidget(id, views)
            } catch (t: Throwable) {
                // 单个小组件渲染失败不影响其余实例——记录后继续（系统在特定桌面可能对
                // RemoteViews 的某些操作抛异常，这正是诊断日志要抓的东西）
                AppLogger.logError(context, "Renderer", "render id=$id style=$style 异常", t)
            }
        }
        AppLogger.log(context, "Renderer", "renderAll 完成 style=$style 共 ${ids.size} 个")
    }

    /**
     * 构建单个小组件的 RemoteViews。
     * 多事件模式（默认）走槽位式多事件列表；单事件模式走大数字卡片。
     */
    private suspend fun buildViewsForWidget(
        context: Context,
        container: AppContainer,
        appWidgetId: Int,
        style: Style
    ): RemoteViews {
        val events = runCatching { container.eventRepository.observeAll().first() }.getOrDefault(emptyList())
        // 今日节假日信息（来自已下载的节假日缓存；未下载时为 null，角标隐藏）。
        // widget 只显示当天实况角标；「明天起」的预告只在 App 内节日卡片状态条展示
        // （小组件没有文案，单凭角标容易误解为今天就开始休息/补班）
        val festival = runCatching {
            container.festivalRepository.todayInfo(LocalDate.now())
        }.getOrNull()
        if (WidgetPrefs.modeFor(context, appWidgetId) == WidgetPrefs.WidgetMode.MULTI) {
            return buildListViews(context, container, appWidgetId, events, festival)
        }
        val picked = pickEvent(events, context, appWidgetId)
        // 跟随节日的假期段序号：假期中显示「假期第 x 天」
        val holidayDayN = picked?.linkedFestival?.takeIf { it.isNotBlank() }
            ?.let { runCatching { container.festivalRepository.holidayDayIndexOf(it, LocalDate.now()) }.getOrNull() }
        val model = picked?.let { buildModel(it, holidayDayN) }
        return buildViews(context, appWidgetId, model, style, picked?.id, festival)
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

    private fun buildModel(picked: EventEntity, holidayDayN: Int?): WidgetModel {
        val today = LocalDate.now()
        val diff = (picked.targetDateEpochDay - today.toEpochDay()).toInt()
        val isFuture = diff >= 0
        val dateStr = LocalDate.ofEpochDay(picked.targetDateEpochDay)
            .format(DateTimeFormatter.ofPattern("yyyy/M/d"))

        // 跟随节日的假期段内（与 App 内同口径）：大字显示假期序号，副标题拼「假期第 x 天」
        // 整句（与事件详情页「假期第 x 天 · 已过 y 天」的结构一致；整句不放大字——
        // 英文 "Day 3 of the holiday" 太长会溢出）。
        if (holidayDayN != null) {
            return WidgetModel(
                title = picked.title,
                subtitle = "$dateStr · " + Tr.s(R.string.unit_holiday_day_n, holidayDayN),
                number = holidayDayN.toString(),
                unit = Tr.s(R.string.unit_days)
            )
        }

        // 数字与单位：跟随事件的显示单位（月/年不足 1 时自动退回更小单位）
        var number = abs(diff).toString()
        var unit = Tr.s(R.string.unit_days)
        if (!isFuture || diff > 0) {
            val period = if (isFuture) Period.between(today, LocalDate.ofEpochDay(picked.targetDateEpochDay))
            else Period.between(LocalDate.ofEpochDay(picked.targetDateEpochDay), today)
            val totalMonths = period.years * 12L + period.months
            when (picked.displayUnit) {
                "MONTH" -> if (totalMonths > 0) {
                    number = totalMonths.toString(); unit = Tr.s(R.string.unit_months)
                }
                "YEAR" -> when {
                    period.years > 0 -> { number = period.years.toString(); unit = Tr.s(R.string.unit_years) }
                    totalMonths > 0 -> { number = totalMonths.toString(); unit = Tr.s(R.string.unit_months) }
                }
            }
        }
        return WidgetModel(
            title = picked.title,
            subtitle = "$dateStr · ${if (isFuture) Tr.s(R.string.widget_remaining) else Tr.s(R.string.widget_passed)}",
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

    /** 多事件列表的槽位上限：再多一行每行就不到 30dp，显示不下。 */
    private const val MAX_LIST_SLOTS = 4
    /** 每行至少 30dp 才显示得下（标题 14sp + 天数 15sp 横排一行的最小舒适高度）。 */
    private const val LIST_MIN_ROW_DP = 30
    /** 列表卡内上下 padding 合计（widget_countdown_list.xml 的 8dp + 8dp）。 */
    private const val LIST_CARD_PADDING_DP = 16

    private val listSlotRoots = intArrayOf(
        R.id.list_row_0, R.id.list_row_1, R.id.list_row_2, R.id.list_row_3
    )
    private val listSlotTitles = intArrayOf(
        R.id.list_row_0_title, R.id.list_row_1_title,
        R.id.list_row_2_title, R.id.list_row_3_title
    )
    private val listSlotDays = intArrayOf(
        R.id.list_row_0_days, R.id.list_row_1_days,
        R.id.list_row_2_days, R.id.list_row_3_days
    )
    private val listSlotDividers = intArrayOf(
        R.id.list_row_0_divider, R.id.list_row_1_divider,
        R.id.list_row_2_divider, R.id.list_row_3_divider
    )

    /**
     * 多事件列表视图（槽位式，三种尺寸共用同一布局，高度由 widget 实际尺寸决定）：
     *  - 行数 = min(事件数, 槽位上限 4, 高度能容纳的行数)，每行 weight=1 平均分割；
     *  - 行间 1dp 浅色分割线（最后一行不画），点击行直达对应事件详情；
     *  - 勾选了事件就只在勾选集合内挑，没勾选则自动挑最近的。
     */
    private suspend fun buildListViews(
        context: Context,
        container: AppContainer,
        appWidgetId: Int,
        events: List<EventEntity>,
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

        val titleColor = if (dark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        val accentColor = if (dark) 0xFF5AA9F0.toInt() else 0xFF1E78D0.toInt()
        val divider = if (dark) R.drawable.widget_divider_dark else R.drawable.widget_divider_light

        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val selected = WidgetPrefs.selectedEventsFor(context, appWidgetId)
        val pool = if (selected.isEmpty()) events else events.filter { it.id in selected }
        // 与单事件卡片同口径：未到期优先，各自按与今天的距离取最近
        val sorted = pool.sortedWith(
            compareBy(
                { if (it.targetDateEpochDay >= todayEpoch) 0 else 1 },
                { abs(it.targetDateEpochDay - todayEpoch) }
            )
        )
        val shown = sorted.take(minOf(MAX_LIST_SLOTS, listSlotCount(context, appWidgetId)))

        if (shown.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            for (i in 0 until MAX_LIST_SLOTS) views.setViewVisibility(listSlotRoots[i], android.view.View.GONE)
            applyFestivalBadge(views, festival)
            return views
        }
        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

        shown.forEachIndexed { i, e ->
            val diff = (e.targetDateEpochDay - todayEpoch).toInt()
            // 跟随节日的假期段内覆盖为「假期第 x 天」（与主页同口径）
            val holidayN = e.linkedFestival?.takeIf { it.isNotBlank() }
                ?.let { runCatching { container.festivalRepository.holidayDayIndexOf(it, today) }.getOrNull() }
            views.setViewVisibility(listSlotRoots[i], android.view.View.VISIBLE)
            views.setTextViewText(listSlotTitles[i], e.title)
            views.setTextViewText(
                listSlotDays[i],
                when {
                    holidayN != null -> Tr.s(R.string.unit_holiday_day_n, holidayN)
                    diff >= 0 -> Tr.s(R.string.unit_days_future, diff)
                    else -> Tr.s(R.string.unit_days_past, -diff)
                }
            )
            views.setTextColor(listSlotTitles[i], titleColor)
            views.setTextColor(listSlotDays[i], if (diff < 0) titleColor else accentColor)
            // 分割线：每行底部一条，最后一行不画
            val withDivider = i < shown.lastIndex
            views.setViewVisibility(
                listSlotDividers[i],
                if (withDivider) android.view.View.VISIBLE else android.view.View.GONE
            )
            if (withDivider) views.setInt(listSlotDividers[i], "setBackgroundResource", divider)
            // 点击行进对应事件详情
            views.setOnClickPendingIntent(listSlotRoots[i], openEventPendingIntent(context, e.id))
        }
        for (i in shown.size until MAX_LIST_SLOTS) {
            views.setViewVisibility(listSlotRoots[i], android.view.View.GONE)
        }
        applyFestivalBadge(views, festival)
        return views
    }

    /**
     * 高度能容纳的行数：widget 实际高度（dp）减卡内 padding，每行至少 LIST_MIN_ROW_DP。
     * 系统不报高度时回退 provider 声明的 minHeight；两者都拿不到时给满槽位。
     */
    private fun listSlotCount(context: Context, appWidgetId: Int): Int {
        val manager = AppWidgetManager.getInstance(context)
        val heightDp = manager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            .takeIf { it > 0 }
            ?: manager.getAppWidgetInfo(appWidgetId)?.minHeight
            ?: 0
        if (heightDp <= 0) return MAX_LIST_SLOTS
        return ((heightDp - LIST_CARD_PADDING_DP) / LIST_MIN_ROW_DP).coerceAtLeast(1)
    }

    /**
     * 节日角标：今天恰逢法定节假日显示绿色「休」，调休上班日显示橙色「班」。无数据隐藏。
     * 「明天起」的预告一律不上小组件——角标没有文案配合，容易被当成当天实况。
     */
    private fun applyFestivalBadge(
        views: RemoteViews,
        festival: com.ayaka7452.daymate.data.festival.FestivalDay?
    ) {
        if (festival == null) {
            views.setViewVisibility(R.id.widget_festival_badge, android.view.View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_festival_badge, android.view.View.VISIBLE)
        views.setTextViewText(
            R.id.widget_festival_badge,
            if (festival.isOffDay) Tr.s(R.string.festival_badge_off) else Tr.s(R.string.festival_badge_work)
        )
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
            views.setTextViewText(R.id.widget_subtitle, Tr.s(R.string.widget_no_events))
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
