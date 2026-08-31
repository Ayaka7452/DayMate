package com.ayaka7452.daymate

import android.os.Bundle
import com.ayaka7452.daymate.feature.home.HomeScreen

/**
 * 启动 Activity（Launcher）。多 Activity 架构下仅承载主页，
 * 其余页面为独立 Activity，跳转通过 [route] 由系统套用原生转场。
 *
 * 主库始终位于应用内部沙盒，无需任何外部存储授权即可直接使用，
 * 因此首次启动直接进入主页；数据备份（可选）在「设置 → 数据备份」中配置。
 */
class MainActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDayMateContent {
            HomeScreen(container = container, onNavigate = { this@MainActivity.route(it) })
        }
    }
}
