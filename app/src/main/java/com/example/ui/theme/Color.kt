package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Part G1 Visual Identity Palette (matches new pencil-rocket app icon)
val NightNavy = Color(0xFF0B1020)
val InkSurface = Color(0xFF121A33)
val InkElevated = Color(0xFF1A2547)
val InkBorder = Color(0xFF2D3C6B)
val LampAmber = Color(0xFFFFB547)
val HotPink = Color(0xFFFF3D7F)
val BoostCyan = Color(0xFF2DE2E6)
val SuccessLime = Color(0xFFA3E635)
val DangerCoral = Color(0xFFFB7185)
val TextPrimary = Color(0xEBFFFFFF)   // White 92%
val TextSecondary = Color(0xA3FFFFFF) // White 64%
val TextMuted = Color(0x70FFFFFF)

// Gradient brushes ONLY for primary CTAs and logo wordmark (Part G1)
val PrimaryCtaGradient = Brush.horizontalGradient(
    colors = listOf(LampAmber, HotPink)
)

val WordmarkGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFFF3D1), LampAmber, HotPink)
)

// Semantic aliases used across HUD & screens
val SlateAbyss = NightNavy
val SlateSurface = InkSurface
val SlateBorder = InkBorder
val ArcadeCrimson = HotPink
val RivalTeal = BoostCyan
val SkyCyan = BoostCyan
val DriftAmber = LampAmber
val RulerYellow = LampAmber
val GasGreen = SuccessLime
val HostEmerald = SuccessLime
val JoinBlue = BoostCyan
val ItemRed = DangerCoral
val DeskWood = Color(0xFF8B5A2B)
val DeskWoodDark = Color(0xFF6B421D)
val BookBlue = Color(0xFF2563EB)
val BookRed = Color(0xFFDC2626)
val BookGreen = Color(0xFF16A34A)
val DriverHelmet = Color(0xFFFDE047)

object DRColors {
    val NightNavy = com.example.ui.theme.NightNavy
    val SlateCard = InkSurface
    val ElevatedCard = InkElevated
    val StrokeHairline = InkBorder
    val KartGold = LampAmber
    val DriftOrange = Color(0xFFFF7A00)
    val MintFinish = SuccessLime
    val ElectricBlue = BoostCyan
    val DangerRed = DangerCoral
    val RoyalPurple = Color(0xFF8338EC)
    val CrispWhite = TextPrimary
    val MutedSlate = TextSecondary
}

object DRRadius {
    val Card = 16.dp
    val Button = 14.dp
    val Pill = 999.dp
}
