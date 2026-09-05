package com.ayaka7452.daymate

import android.content.Intent
import android.os.Bundle
import com.ayaka7452.daymate.feature.home.HomeScreen

/**
 * 启动 Activity（Launcher）。多 Activity 架构下仅承载主页，
 * 其余页面为独立 Activity，跳转通过 [route] 由系统套用原生转场。
 *
 * 主库始终位于应用内部沙盒，无需任何外部存储授权即可直接使用，
 * 因此首次启动直接进入主页；数据备份（可选）在「设置 → 数据备份」中配置。
 *
 * 桌面小组件深链：2×2 多事件列表的行点击会携带 eventId extra 打开本页，
 * 由 [handleWidgetDeepLink] 转跳到对应事件的编辑详情页（singleTop + onNewIntent）。
 */
class MainActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDayMateContent {
            HomeScreen(container = container, onNavigate = { this@MainActivity.route(it) })
        }
        handleWidgetDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetDeepLink(intent)
    }

    private fun handleWidgetDeepLink(intent: Intent?) {
        val id = intent?.getLongExtra("eventId", -1L) ?: -1L
        if (id > 0) route("event_form?eventId=$id")
    }
}
