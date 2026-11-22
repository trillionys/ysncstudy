package com.cpaconceptlab.study.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

// 딥네이비·화이트·차콜·블루 포인트, 절제된 그린/레드
val Navy = Color(0xFF0B1F3A)
val NavyLight = Color(0xFF1C355E)
val Blue = Color(0xFF2F6FEB)
val BlueSoft = Color(0xFFE6EEFD)
val Charcoal = Color(0xFF1F2933)
val Slate = Color(0xFF5B6675)
val Line = Color(0xFFD9DFE7)
val SurfaceLight = Color(0xFFF5F7FA)

@Immutable
data class StatusColors(
    val correct: Color, val correctContainer: Color,
    val wrong: Color, val wrongContainer: Color,
    val warn: Color, val warnContainer: Color,
    val muted: Color,
)

private val LightStatus = StatusColors(
    correct = Color(0xFF2E7D5B), correctContainer = Color(0xFFE5F2EC),
    wrong = Color(0xFFB23B3B), wrongContainer = Color(0xFFF8E7E7),
    warn = Color(0xFF9A6A00), warnContainer = Color(0xFFFFF3D6),
    muted = Slate,
)
private val DarkStatus = StatusColors(
    correct = Color(0xFF7CC4A1), correctContainer = Color(0xFF173A2C),
    wrong = Color(0xFFE59393), wrongContainer = Color(0xFF3F1E1E),
    warn = Color(0xFFE6C36B), warnContainer = Color(0xFF3A2F12),
    muted = Color(0xFF9AA6B5),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val LightScheme = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = BlueSoft, onPrimaryContainer = Navy,
    secondary = Blue, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7FB), onSecondaryContainer = Navy,
    tertiary = NavyLight,
    background = Color.White, onBackground = Charcoal,
    surface = Color.White, onSurface = Charcoal,
    surfaceVariant = SurfaceLight, onSurfaceVariant = Slate,
    surfaceContainer = SurfaceLight, surfaceContainerHigh = Color(0xFFEEF2F7),
    surfaceContainerLow = Color(0xFFFAFBFC), surfaceContainerHighest = Color(0xFFE8EDF3),
    outline = Line, outlineVariant = Color(0xFFE6EAF0),
    error = Color(0xFFB23B3B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DBDFF), onPrimary = Color(0xFF0A1A33),
    primaryContainer = Color(0xFF1C355E), onPrimaryContainer = Color(0xFFDCE7FB),
    secondary = Color(0xFF7FA7FF), onSecondary = Color(0xFF0A1A33),
    secondaryContainer = Color(0xFF223A63), onSecondaryContainer = Color(0xFFDCE7FB),
    background = Color(0xFF0F1720), onBackground = Color(0xFFE3E8EF),
    surface = Color(0xFF0F1720), onSurface = Color(0xFFE3E8EF),
    surfaceVariant = Color(0xFF17212C), onSurfaceVariant = Color(0xFFA3AEBC),
    surfaceContainer = Color(0xFF17212C), surfaceContainerHigh = Color(0xFF1D2935),
    surfaceContainerLow = Color(0xFF131C26), surfaceContainerHighest = Color(0xFF243140),
    outline = Color(0xFF314050), outlineVariant = Color(0xFF253241),
    error = Color(0xFFE59393),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
)

/**
 * @param themeMode SYSTEM / LIGHT / DARK
 * @param fontScale 사용자 글자 크기 배율. 시스템 글꼴 크기 설정에 곱해져 모든 학습 화면에 적용된다.
 */
@Composable
fun CpaTheme(themeMode: String = "SYSTEM", fontScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
