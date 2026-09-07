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

val PaperLight = Color(0xFFFAFAF7)
val InkDark = Color(0xFF1A1A18)

// 「默认」配色：纸白统一底色（背景 = 表面，避免顶部栏纯白与底部灰白的割裂感）+ 深青蓝控件
// 控件主色取自用户指定的 #00668C（截图采样），容器色为同色调 M3 派生
private val LightColors = lightColorScheme(
    primary = Color(0xFF00668C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC1E8FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF4C616E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E5F0),
    onSecondaryContainer = Color(0xFF0C1D27),
    background = PaperLight,
    surface = PaperLight,
    onBackground = Color(0xFF1C1C1C),
    onSurface = Color(0xFF1C1C1C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83D1F2),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004B66),
    onPrimaryContainer = Color(0xFFC1E8FF),
    secondary = Color(0xFFB7C9D4),
    onSecondary = Color(0xFF22333D),
    secondaryContainer = Color(0xFF384954),
    onSecondaryContainer = Color(0xFFD3E5F0),
    background = InkDark,
    surface = Color(0xFF242422),
    onBackground = Color(0xFFE8E6DF),
    onSurface = Color(0xFFE8E6DF)
)

/**
 * 固定原生配色组合（Material 3 色调规范）。
 * 每组 8 色：[primary, onPrimary, primaryContainer, onPrimaryContainer,
 *           secondary, onSecondary, secondaryContainer, onSecondaryContainer]
 * 浅色 = 白底 + 彩色主色；深色 = 墨底 + 提亮主色 + 深容器色。
 * key 与 SettingsRepository.COLOR_MODE 的取值对应。
 */
private val LightAccents: Map<String, List<Long>> = mapOf(
    "blue" to listOf(
        0xFF1565C0, 0xFFFFFFFF, 0xFFD5E3FF, 0xFF001C3B,
        0xFF565E71, 0xFFFFFFFF, 0xFFDAE2F9, 0xFF131C2B
    ),
    "green" to listOf(
        0xFF2E7D32, 0xFFFFFFFF, 0xFFB9F0B2, 0xFF00210A,
        0xFF52634F, 0xFFFFFFFF, 0xFFD5E8CF, 0xFF101F10
    ),
    "orange" to listOf(
        0xFFE65100, 0xFFFFFFFF, 0xFFFFDBCB, 0xFF3F1500,
        0xFF755845, 0xFFFFFFFF, 0xFFFFDCC2, 0xFF2B1707
    ),
    "purple" to listOf(
        0xFF6A1B9A, 0xFFFFFFFF, 0xFFEFDBFF, 0xFF290052,
        0xFF655D6F, 0xFFFFFFFF, 0xFFECDFF4, 0xFF201A2B
    )
)

private val DarkAccents: Map<String, List<Long>> = mapOf(
    "blue" to listOf(
        0xFFA6C8FF, 0xFF00315E, 0xFF004785, 0xFFD5E3FF,
        0xFFBEC6DC, 0xFF263141, 0xFF3C4758, 0xFFDAE2F9
    ),
    "green" to listOf(
        0xFF9ED69C, 0xFF003911, 0xFF1E5324, 0xFFB9F0B2,
        0xFFB9CCB4, 0xFF243424, 0xFF3A4B39, 0xFFD5E8CF
    ),
    "orange" to listOf(
        0xFFFFB77C, 0xFF4F2000, 0xFF703400, 0xFFFFDBCB,
        0xFFE4BFA5, 0xFF432B1A, 0xFF5C412E, 0xFFFFDCC2
    ),
    "purple" to listOf(
        0xFFDFB9FF, 0xFF460A75, 0xFF512394, 0xFFEFDBFF,
        0xFFCFC2DB, 0xFF352D40, 0xFF4C4358, 0xFFECDFF4
    )
)

/**
 * 各配色的浅色底色调（背景 = 表面，统一的近白着色，让配色切换一眼可辨）。
 */
private val LightBgTint: Map<String, Long> = mapOf(
    "blue" to 0xFFF2F7FD,
    "green" to 0xFFF2F9F1,
    "orange" to 0xFFFDF6F0,
    "purple" to 0xFFF8F4FD
)

private fun accentScheme(mode: String, darkTheme: Boolean): ColorScheme {
    val tones = (if (darkTheme) DarkAccents[mode] else LightAccents[mode])
        ?: return if (darkTheme) DarkColors else LightColors
    val c = tones.map { Color(it) }
    return if (darkTheme) {
        DarkColors.copy(
            primary = c[0], onPrimary = c[1],
            primaryContainer = c[2], onPrimaryContainer = c[3],
            secondary = c[4], onSecondary = c[5],
            secondaryContainer = c[6], onSecondaryContainer = c[7]
        )
    } else {
        val bg = LightBgTint[mode]?.let { Color(it) } ?: PaperLight
        LightColors.copy(
            primary = c[0], onPrimary = c[1],
            primaryContainer = c[2], onPrimaryContainer = c[3],
            secondary = c[4], onSecondary = c[5],
            secondaryContainer = c[6], onSecondaryContainer = c[7],
            background = bg, surface = bg,
            onBackground = Color(0xFF1C1C1C), onSurface = Color(0xFF1C1C1C)
        )
    }
}

/**
 * DayMate 主题。
 * - mode: system / light / dark（深浅模式）
 * - colorMode: white（默认：纸白底 + 蓝色控件）/ system（Material You 壁纸取色，Android 12+，
 *   低版本回退默认）/ blue / green / orange / purple（固定原生配色，背景带近白着色）
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
        colorMode == "system" -> if (darkTheme) DarkColors else LightColors // 低版本回退默认
        LightAccents.containsKey(colorMode) -> accentScheme(colorMode, darkTheme)
        else -> if (darkTheme) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
