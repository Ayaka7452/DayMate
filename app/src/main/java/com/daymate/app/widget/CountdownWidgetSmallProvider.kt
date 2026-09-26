package com.ayaka7452.daymate.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.ayaka7452.daymate.core.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 桌面小组件（迷你 2×1）：紧凑单行卡片，渲染逻辑与标准版共用。 */
class CountdownWidgetSmallProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        AppLogger.log(context, "Small", "onUpdate ids=${appWidgetIds.joinToString()}")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.renderAll(context.applicationContext, manager, appWidgetIds, WidgetRenderer.Style.SMALL)
                AppLogger.log(context, "Small", "onUpdate 渲染完成")
            } catch (t: Throwable) {
                AppLogger.logError(context, "Small", "onUpdate 渲染异常", t)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        AppLogger.log(context, "Small", "onDeleted ids=${appWidgetIds.joinToString()}")
        appWidgetIds.forEach { WidgetPrefs.clearWidget(context, it) }
    }
}
