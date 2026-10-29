package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeskRacerColorScheme = darkColorScheme(
    primary = SkyCyan,
    onPrimary = SlateAbyss,
    primaryContainer = JoinBlue,
    onPrimaryContainer = Color.White,
    secondary = HostEmerald,
    onSecondary = SlateAbyss,
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color.White,
    tertiary = DriftAmber,
    onTertiary = SlateAbyss,
    background = SlateAbyss,
    onBackground = Color.White,
    surface = SlateSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = ArcadeCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DeskRacerColorScheme,
        typography = Typography,
        content = content
    )
}
