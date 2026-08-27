package com.daymate.app

import android.os.Bundle
import com.daymate.app.feature.home.HomeScreen

/**
 * 启动 Activity（Launcher）。多 Activity 架构下仅承载主页，
 * 其余页面为独立 Activity，跳转通过 [route] 由系统套用原生转场。
 */
class MainActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDayMateContent {
            HomeScreen(container = container, onNavigate = { this@MainActivity.route(it) })
        }
    }
}
