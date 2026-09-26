package com.ayaka7452.daymate.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面小组件（标准宽版 3×1）：统一由 WidgetRenderer 渲染。
 * - 默认显示最近的倒数日事件；部署时可通过配置页选择固定事件；
 * - 每 30 分钟系统定时刷新兜底；数据变更/深浅色切换/跨天午夜均即时重绘；
 * - 本 provider 还承担跨天闹钟（ACTION_MIDNIGHT_REFRESH）与开机（BOOT_COMPLETED）
 *   广播的接收，触发全量刷新并续订下一天闹钟。
 * - 生命周期全程写 WidgetLogger 诊断日志（默认开，设置页可关）。
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetLogger.log(context, "Wide", "onUpdate ids=${appWidgetIds.joinToString()}")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.renderAll(context.applicationContext, manager, appWidgetIds, WidgetRenderer.Style.WIDE)
                // 借系统更新时机续订午夜闹钟（保证链条不中断）
                WidgetRefreshScheduler.scheduleNextMidnight(context.applicationContext)
                WidgetLogger.log(context, "Wide", "onUpdate 渲染完成")
            } catch (t: Throwable) {
                WidgetLogger.logError(context, "Wide", "onUpdate 渲染异常", t)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetLogger.log(context, "Wide", "onEnabled（桌面上出现第一个宽版小组件）")
        super.onEnabled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetLogger.log(context, "Wide", "onDeleted ids=${appWidgetIds.joinToString()}")
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        WidgetLogger.log(context, "Wide", "onDisabled（桌面上最后一个宽版小组件被移除）")
        super.onDisabled(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle
    ) {
        WidgetLogger.log(context, "Wide", "onAppWidgetOptionsChanged id=$appWidgetId")
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        WidgetLogger.log(context, "Wide", "onReceive action=${intent.action}")
        when (intent.action) {
            // 深浅色切换不在这里处理：CONFIGURATION_CHANGED 只投递给运行时注册的接收器，
            // 本组件未在 manifest 声明该 action，由 DayMateApp.registerUiModeWatcher 负责重绘
            WidgetRefreshScheduler.ACTION_MIDNIGHT_REFRESH,
            Intent.ACTION_BOOT_COMPLETED -> {
                // 跨天 / 开机：重绘并续订下一个午夜的闹钟
                WidgetRenderer.onSystemConfigurationChanged(context)
                WidgetRefreshScheduler.scheduleNextMidnight(context.applicationContext)
            }
        }
        super.onReceive(context, intent)
    }
}
