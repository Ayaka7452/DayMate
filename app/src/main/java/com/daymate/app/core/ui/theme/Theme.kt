package com.ayaka7452.daymate.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
 * 固定原生配色组合（Material 色板取色）：浅色 = 白底 + 主题色，深色 = 墨底 + 提亮主题色。
 * key 与 SettingsRepository.COLOR_MODE 的取值对应。
 */
private val AccentSchemes: Map<String, Pair<Long, Long>> = mapOf(
    //            浅色 primary     深色 primary
    "blue"   to Pair(0xFF1565C0, 0xFF90CAF9),
    "green"  to Pair(0xFF2E7D32, 0xFFA5D6A7),
    "orange" to Pair(0xFFE65100, 0xFFFFB74D),
    "purple" to Pair(0xFF6A1B9A, 0xFFCE93D8)
)

private fun accentScheme(mode: String, darkTheme: Boolean): ColorScheme {
    val (lightPrimary, darkPrimary) = AccentSchemes[mode] ?: return if (darkTheme) DarkColors else LightColors
    val primary = Color(if (darkTheme) darkPrimary else lightPrimary)
    return if (darkTheme) {
        DarkColors.copy(primary = primary, onPrimary = Color(0xFF1A1A18))
    } else {
        LightColors.copy(primary = primary, onPrimary = Color.White)
    }
}

/**
 * DayMate 主题。
 * - mode: system / light / dark（深浅模式）
 * - colorMode: white（白底品牌色，默认）/ system（Material You 壁纸取色，Android 12+，
 *   低版本回退白色）/ blue / green / orange / purple（固定原生配色）
 */
@Composable
fun DayMateTheme(
    mode: String = "system",
    colorMode: String = "white",
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        colorMode == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        colorMode == "system" -> if (darkTheme) DarkColors else LightColors // 低版本回退白色
        AccentSchemes.containsKey(colorMode) -> accentScheme(colorMode, darkTheme)
        else -> if (darkTheme) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
