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
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.renderAll(context.applicationContext, manager, appWidgetIds, WidgetRenderer.Style.WIDE)
                // 借系统更新时机续订午夜闹钟（保证链条不中断）
                WidgetRefreshScheduler.scheduleNextMidnight(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_CONFIGURATION_CHANGED ->
                // 系统深浅色切换：立即重绘所有尺寸的小组件
                WidgetRenderer.onSystemConfigurationChanged(context)
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
