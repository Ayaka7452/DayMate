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

// 浅色模式统一底色：Android 标准纯白（原 #FAFAF7 为暖纸白，蓝通道偏低会导致整体观感发黄）
val PaperLight = Color(0xFFFFFFFF)
val InkDark = Color(0xFF1A1A18)

// 「默认」配色：纯白统一底色（背景 = 表面，避免顶部栏与底部的割裂感）+ 深青蓝控件
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
    onSurface = Color(0xFF1C1C1C),
    // surface 系列槽位必须显式定义：M3 默认基线是紫粉调（surfaceVariant #E7E2EC /
    // surfaceContainer #F3EDF7），在纸白底上观感发红。此处统一为低饱和的浅青灰——
    // v1.12.7 首版 #C9E2EE 被用户反馈「太蓝」，v1.12.8 降饱和提亮度。
    surfaceVariant = Color(0xFFE2EBEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F8),
    surfaceContainer = Color(0xFFE4EDF2),
    surfaceContainerHigh = Color(0xFFDAE5EB),
    surfaceContainerHighest = Color(0xFFD0DDE4),
    outline = Color(0xFF6B7E88),
    outlineVariant = Color(0xFFBCCDD6)
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

/**
 * 各配色浅色的 surface 分层（与主色同族的容器色，取代 M3 紫粉基线）。
 * 顺序：[surfaceVariant, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, outlineVariant]
 * 默认主题的这套值直接写在 LightColors 里；「default」项供兜底（mode 不在表内时复用）。
 */
private val LightSurfaces: Map<String, List<Long>> = mapOf(
    "default" to listOf(
        0xFFE2EBEF, 0xFFF0F5F8, 0xFFE4EDF2, 0xFFDAE5EB, 0xFFD0DDE4, 0xFFBCCDD6
    ),
    "blue" to listOf(
        0xFFC7DBF0, 0xFFE7EFF8, 0xFFDBE7F5, 0xFFD0E0F1, 0xFFC4D8ED, 0xFFA9C1DC
    ),
    "green" to listOf(
        0xFFC6E2C4, 0xFFE6F1E4, 0xFFD8EAD6, 0xFFCDE2CB, 0xFFC1DBBF, 0xFFA4C2A2
    ),
    "orange" to listOf(
        0xFFF0D9C2, 0xFFFAF0E6, 0xFFF6E6D4, 0xFFF1DEC7, 0xFFECD6BB, 0xFFDCC3A6
    ),
    "purple" to listOf(
        0xFFE0D3EE, 0xFFF3EDF9, 0xFFEAE1F5, 0xFFE3D7F0, 0xFFDBCCED, 0xFFC9B7DC
    )
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
        val s = (LightSurfaces[mode] ?: LightSurfaces["default"]!!).map { Color(it) }
        LightColors.copy(
            primary = c[0], onPrimary = c[1],
            primaryContainer = c[2], onPrimaryContainer = c[3],
            secondary = c[4], onSecondary = c[5],
            secondaryContainer = c[6], onSecondaryContainer = c[7],
            background = bg, surface = bg,
            surfaceVariant = s[0],
            surfaceContainerLow = s[1],
            surfaceContainer = s[2],
            surfaceContainerHigh = s[3],
            surfaceContainerHighest = s[4],
            outlineVariant = s[5],
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
