package com.daymate.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.daymate.app.core.AppContainer
import com.daymate.app.core.ui.theme.DayMateTheme
import com.daymate.app.feature.about.AboutScreen
import com.daymate.app.feature.create.EventFormScreen
import com.daymate.app.feature.folder.FolderScreen
import com.daymate.app.feature.home.HomeScreen
import com.daymate.app.feature.settings.SettingsScreen
import com.daymate.app.feature.vault.VaultFolderScreen
import com.daymate.app.feature.vault.VaultScreen

/**
 * 所有页面 Activity 的基类：统一
 *  - 全面屏（enableEdgeToEdge）
 *  - 主题模式（跟随设置：系统/深/浅）+ 状态栏/导航栏图标对比度
 *  - 共享 DI 容器（来自 DayMateApp.application.container）
 *
 * 多 Activity 架构下，页面间跳转由系统框架套用原生 Activity 转场，
 * 因此切换动画即「系统默认」，与 Thanox / 系统设置一致（无需手写）。
 */
abstract class ComposeActivity : FragmentActivity() {
    val container: AppContainer
        get() = (application as DayMateApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    fun setDayMateContent(content: @Composable () -> Unit) {
        val repo = container.settingsRepository
        setContent {
            val themeMode by repo.themeMode.collectAsState(initial = "system")
            DayMateChrome(themeMode = themeMode) { content() }
        }
    }
}

@Composable
private fun DayMateChrome(themeMode: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(darkTheme) {
        activity?.window?.let { win ->
            val c = WindowInsetsControllerCompat(win, win.decorView)
            c.isAppearanceLightStatusBars = !darkTheme
            c.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    DayMateTheme(mode = themeMode) { content() }
}

/**
 * 把原 NavHost 的 route 字符串映射为 startActivity，实现多 Activity 跳转。
 * Home 等页面仍用 onNavigate(String) 回调，由宿主 Activity 调用本函数。
 */
fun Context.route(route: String) {
    val intent = when {
        route == "vault" ->
            Intent(this, VaultActivity::class.java)
        route == "settings" ->
            Intent(this, SettingsActivity::class.java)
        route == "about" ->
            Intent(this, AboutActivity::class.java)
        route.startsWith("folder/") -> {
            val id = route.substringAfter("folder/").toLongOrNull() ?: 0L
            Intent(this, FolderActivity::class.java).apply { putExtra("folderId", id) }
        }
        route.startsWith("vault_folder/") -> {
            val id = route.substringAfter("vault_folder/").toLongOrNull() ?: 0L
            Intent(this, VaultFolderActivity::class.java).apply { putExtra("folderId", id) }
        }
        route.startsWith("event_form") -> {
            val qs = route.substringAfter("?").split("&")
            var eventId: Long? = null
            var folderId: Long? = null
            for (p in qs) {
                if (p.startsWith("eventId=")) eventId = p.substringAfter("=").toLongOrNull()
                if (p.startsWith("folderId=")) folderId = p.substringAfter("=").toLongOrNull()
            }
            Intent(this, EventFormActivity::class.java).apply {
                eventId?.let { putExtra("eventId", it) }
                folderId?.let { putExtra("folderId", it) }
            }
        }
        else -> null
    } ?: return
    // 不调用 overridePendingTransition：交给系统/设备原生 Activity 转场（与系统设置一致）
    startActivity(intent)
}

class EventFormActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getLongExtra("eventId", -1L).let { if (it == -1L) null else it }
        val folderId = intent.getLongExtra("folderId", -1L).let { if (it == -1L) null else it }
        setDayMateContent {
            EventFormScreen(
                container = container,
                eventId = eventId,
                folderId = folderId,
                onBack = { finish() }
            )
        }
    }
}

class SettingsActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDayMateContent {
            SettingsScreen(
                container = container,
                onBack = { finish() },
                onOpenAbout = { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) }
            )
        }
    }
}

class AboutActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDayMateContent { AboutScreen(onBack = { finish() }) }
    }
}

class VaultActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDayMateContent {
            VaultScreen(
                container = container,
                onExit = { finish() },
                onNavigate = { this@VaultActivity.route(it) }
            )
        }
    }
}

class FolderActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folderId = intent.getLongExtra("folderId", 0L)
        setDayMateContent {
            FolderScreen(
                container = container,
                folderId = folderId,
                onBack = { finish() },
                onNavigate = { this@FolderActivity.route(it) }
            )
        }
    }
}

class VaultFolderActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folderId = intent.getLongExtra("folderId", 0L)
        setDayMateContent {
            VaultFolderScreen(container = container, folderId = folderId, onBack = { finish() })
        }
    }
}
