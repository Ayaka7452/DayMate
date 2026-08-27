package com.daymate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daymate.app.core.ui.theme.DayMateTheme
import com.daymate.app.feature.about.AboutScreen
import com.daymate.app.feature.create.EventFormScreen
import com.daymate.app.feature.folder.FolderScreen
import com.daymate.app.feature.home.HomeScreen
import com.daymate.app.feature.settings.SettingsScreen
import com.daymate.app.feature.vault.VaultFolderScreen
import com.daymate.app.feature.vault.VaultScreen

object Routes {
    const val HOME = "home"
    const val EVENT_FORM = "event_form"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val VAULT = "vault"
    const val FOLDER = "folder/{folderId}"
    const val VAULT_FOLDER = "vault_folder/{folderId}"
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全面屏：内容延伸到状态栏/导航栏之下，由 Compose Scaffold 处理内边距
        enableEdgeToEdge()
        setContent {
            DayMateAppContent()
        }
    }
}

@Composable
fun DayMateAppContent() {
    val context = LocalContext.current
    val app = context.applicationContext as DayMateApp
    val themeMode by app.container.settingsRepository.themeMode
        .collectAsState(initial = "system")

    // 跟随主题模式（含手动 dark/light）同步状态栏/导航栏图标对比度
    val activity = context as? ComponentActivity
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(darkTheme) {
        activity?.window?.let { win ->
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    DayMateTheme(mode = themeMode) {
        val navController = rememberNavController()
        // 系统风过渡：侧滑 + 淡入淡出（push 时新页从右滑入、旧页左滑+淡出；pop 反向）
        val animDuration = 300
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(animDuration)
                ) + fadeIn(animationSpec = tween(animDuration))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(animDuration)
                ) + fadeOut(animationSpec = tween(animDuration))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(animDuration)
                ) + fadeIn(animationSpec = tween(animDuration))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(animDuration)
                ) + fadeOut(animationSpec = tween(animDuration))
            }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = app.container,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.EVENT_FORM) {
                EventFormScreen(
                    container = app.container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = app.container,
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) }
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.VAULT) {
                VaultScreen(
                    container = app.container,
                    onExit = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(
                route = "event_form?eventId={eventId}&folderId={folderId}",
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("folderId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    }
                )
            ) { backStack ->
                val eventId = backStack.arguments?.getString("eventId")?.toLongOrNull()
                val folderId = backStack.arguments?.getString("folderId")?.toLongOrNull()
                EventFormScreen(
                    container = app.container,
                    eventId = eventId,
                    folderId = folderId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "folder/{folderId}",
                arguments = listOf(navArgument("folderId") { type = NavType.LongType })
            ) { backStack ->
                val folderId = backStack.arguments?.getLong("folderId") ?: 0
                FolderScreen(
                    container = app.container,
                    folderId = folderId,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(
                route = "vault_folder/{folderId}",
                arguments = listOf(navArgument("folderId") { type = NavType.LongType })
            ) { backStack ->
                val folderId = backStack.arguments?.getLong("folderId") ?: 0
                VaultFolderScreen(
                    container = app.container,
                    folderId = folderId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
