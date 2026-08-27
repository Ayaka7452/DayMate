package com.ayaka7452.daymate

import android.content.Intent
import android.os.Bundle
import com.ayaka7452.daymate.core.StorageConfig
import com.ayaka7452.daymate.feature.home.HomeScreen

/**
 * 启动 Activity（Launcher）。多 Activity 架构下仅承载主页，
 * 其余页面为独立 Activity，跳转通过 [route] 由系统套用原生转场。
 *
 * 首次启动（尚未选择外部存储目录）时跳转初始化向导，完成后再进入主页。
 */
class MainActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!StorageConfig.isConfigured(this)) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }
        setDayMateContent {
            HomeScreen(container = container, onNavigate = { this@MainActivity.route(it) })
        }
    }
}
