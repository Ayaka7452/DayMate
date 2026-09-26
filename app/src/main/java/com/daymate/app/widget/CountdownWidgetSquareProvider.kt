package com.ayaka7452.daymate.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 桌面小组件（方形 2×2）：大号数字卡片，渲染逻辑与标准版共用。 */
class CountdownWidgetSquareProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetLogger.log(context, "Square", "onUpdate ids=${appWidgetIds.joinToString()}")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.renderAll(context.applicationContext, manager, appWidgetIds, WidgetRenderer.Style.SQUARE)
                WidgetLogger.log(context, "Square", "onUpdate 渲染完成")
            } catch (t: Throwable) {
                WidgetLogger.logError(context, "Square", "onUpdate 渲染异常", t)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetLogger.log(context, "Square", "onDeleted ids=${appWidgetIds.joinToString()}")
        appWidgetIds.forEach { WidgetPrefs.clearWidget(context, it) }
    }
}
