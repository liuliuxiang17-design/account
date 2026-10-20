package com.redcakefinance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 「红糕镇」配色：深红黑底 + 珊瑚红支出 + 蜜金收入，与 Android 版逐值一致。
 *
 * 收支用色相区分（红 vs 金）而不是两档红：柱状图正负、账单增减一眼可辨，
 * 且红绿色盲也能靠亮度差分开。
 */
val DarkBg = Color(0xFF100E0F)
val DarkSurface = Color(0xFF1A1516)
val DarkSurfaceHigh = Color(0xFF241D1F)
val DarkSurfaceHighest = Color(0xFF2E2528)

val AccentRed = Color(0xFFD4594C)
val AccentRedLight = Color(0xFFF08A7C)
val AccentGold = Color(0xFFE6B45C)

val OnDarkPrimary = Color(0xFFF4EFEB)
val OnDarkSub = Color(0xFFA7ACB6)
val OnDarkFaint = Color(0xFF7C828C)
val ErrorRed = Color(0xFFFF7A6E)

private val DarkColors = darkColorScheme(
    primary = AccentRed,
    onPrimary = Color(0xFF2A1210),
    primaryContainer = Color(0xFF3A2020),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = AccentRedLight,
    onSecondary = Color(0xFF2A1210),
    secondaryContainer = Color(0xFF3A2020),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = AccentGold,
    onTertiary = Color(0xFF2A2010),
    tertiaryContainer = Color(0xFF3A2E18),
    onTertiaryContainer = Color(0xFFFFE7BE),
    background = DarkBg,
    onBackground = OnDarkPrimary,
    surface = DarkSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = OnDarkSub,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = Color(0xFF6D5D5F),
    outlineVariant = Color(0xFF423538),
    error = ErrorRed,
    onError = Color(0xFF2A1210),
)

/** 深色暖红主题。 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
