package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DeskRacerColorScheme = darkColorScheme(
    primary = LampAmber,
    onPrimary = NightNavy,
    primaryContainer = InkElevated,
    onPrimaryContainer = TextPrimary,
    secondary = HotPink,
    onSecondary = TextPrimary,
    secondaryContainer = InkElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = BoostCyan,
    onTertiary = NightNavy,
    background = NightNavy,
    onBackground = TextPrimary,
    surface = InkSurface,
    onSurface = TextPrimary,
    surfaceVariant = InkElevated,
    onSurfaceVariant = TextSecondary,
    error = DangerCoral,
    onError = NightNavy
)

@Composable
fun MyApplicationTheme(
    maxFontScale: Float = 1.15f,
    content: @Composable () -> Unit
) {
    val curDensity = LocalDensity.current
    val clampedDensity = Density(
        density = curDensity.density,
        fontScale = curDensity.fontScale.coerceIn(0.85f, maxFontScale)
    )
    CompositionLocalProvider(LocalDensity provides clampedDensity) {
        MaterialTheme(
            colorScheme = DeskRacerColorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun HudFontScaleScope(
    content: @Composable () -> Unit
) {
    val curDensity = LocalDensity.current
    val clampedHudDensity = Density(
        density = curDensity.density,
        fontScale = curDensity.fontScale.coerceIn(0.85f, 1.15f)
    )
    CompositionLocalProvider(LocalDensity provides clampedHudDensity) {
        content()
    }
}
