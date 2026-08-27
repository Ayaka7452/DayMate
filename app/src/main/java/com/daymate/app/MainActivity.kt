package com.daymate.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.daymate.app.core.ui.theme.DayMateTheme
import com.daymate.app.feature.about.AboutScreen
import com.daymate.app.feature.create.EventFormScreen
import com.daymate.app.feature.home.HomeScreen
import com.daymate.app.feature.settings.SettingsScreen
import com.daymate.app.feature.vault.VaultScreen

object Routes {
    const val HOME = "home"
    const val EVENT_FORM = "event_form"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val VAULT = "vault"
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    DayMateTheme(mode = themeMode) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.HOME) {
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
                    onExit = { navController.popBackStack() }
                )
            }
        }
    }
}
