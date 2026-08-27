package com.ayaka7452.daymate.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DayMateGreen = Color(0xFF2F5D62)
val DayMateWarm = Color(0xFFC97A3B)
val PaperLight = Color(0xFFFAFAF7)
val InkDark = Color(0xFF1A1A18)

private val LightColors = lightColorScheme(
    primary = DayMateGreen,
    secondary = DayMateWarm,
    background = PaperLight,
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1C1C),
    onSurface = Color(0xFF1C1C1C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FBFBF),
    secondary = Color(0xFFE0A06B),
    background = InkDark,
    surface = Color(0xFF242422),
    onPrimary = Color(0xFF102A2E),
    onBackground = Color(0xFFE8E6DF),
    onSurface = Color(0xFFE8E6DF)
)

/**
 * DayMate 主题。
 * - mode: system / light / dark
 * - dynamicColor: Android 12+ 时跟随壁纸动态取色（Material You），低于该版本回退到品牌配色
 */
@Composable
fun DayMateTheme(
    mode: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
