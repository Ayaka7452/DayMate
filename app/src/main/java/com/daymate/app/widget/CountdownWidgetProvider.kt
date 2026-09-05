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
 * - 每 30 分钟系统定时刷新（覆盖跨天）；应用内数据变更即时刷新；
 * - 深浅色跟随系统（监听 CONFIGURATION_CHANGED 后全量重绘）。
 */
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.renderAll(context.applicationContext, manager, appWidgetIds, WidgetRenderer.Style.WIDE)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            // 系统深浅色切换：立即重绘所有尺寸的小组件
            WidgetRenderer.onSystemConfigurationChanged(context)
        }
        super.onReceive(context, intent)
    }
}
