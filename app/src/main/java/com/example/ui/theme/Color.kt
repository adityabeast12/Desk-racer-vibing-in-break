package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * v6 "Desk GP" design tokens. Every colour in the UI comes from [DR].
 * Rules: Amber is the ONLY interactive/selected colour. Pink = the local player only.
 * Cyan = boost/drift only. Green = ready/connected. Red = danger/disconnect.
 */
object DR {
    // Surfaces (darkest → lightest)
    val Bg = Color(0xFF0C0D10)

    // v7 livery swatch inks (garage)
    val SwatchStage = Color(0xFF0E1320)
    val SwatchGraphite = Color(0xFF1C1F26)
    val SwatchGlass = Color(0xFF2A3344)
    val SwatchTyre = Color(0xFF0B0B0D)
    val SwatchStripeLight = Color(0xFFF5F5F7)
    val SwatchStripeDark = Color(0xFF121216)
    val S1 = Color(0xFF121419)
    val S2 = Color(0xFF17191F)
    val S3 = Color(0xFF1E2128)
    val S4 = Color(0xFF272A33)
    val Line = Color(0xFF262932)
    val Line2 = Color(0xFF343843)
    // Text
    val TextHi = Color(0xFFF2F2F0)
    val TextMid = Color(0xFFA2A7B2)
    val TextLo = Color(0xFF6E7380)
    val TextDis = Color(0xFF4A4E59)
    // Interactive
    val Amber = Color(0xFFFFB000)
    val AmberHi = Color(0xFFFFC233)
    val AmberDk = Color(0xFFC98A00)
    val OnAmber = Color(0xFF0C0D10)
    val AmberTint = Color(0xFF2A2410)
    // Semantics
    val You = Color(0xFFFF3D7F)
    val Boost = Color(0xFF2DE2E6)
    val Ok = Color(0xFF3DDC84)
    val Bad = Color(0xFFFF4D4F)
    val Kerb = Color(0xFFD7262B)
    val KerbWhite = Color(0xFFE8E8E6)
    val Gold = Color(0xFFFFD23F)
    val Silver = Color(0xFFC9D1DB)
    val Bronze = Color(0xFFE0915A)
    val Marker = Color(0xFFFFD23F)
    val Ink = Color(0xFF14110C)
    // Overlays
    val Scrim = Color(0xB8060709)
    val HudPlate = Color(0x610C0D10)
    val HudPlate2 = Color(0x8C0C0D10)
    val HudLine = Color(0x38FFFFFF)
    val YouWash = Color(0x1FFF3D7F)

    fun podium(position: Int): Color = when (position) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> TextMid
    }
}

// ---- Legacy names kept so older call-sites compile; all re-pointed at the v6 palette ----
val NightNavy = DR.Bg
val InkSurface = DR.S1
val InkElevated = DR.S2
val InkBorder = DR.Line2
val LampAmber = DR.Amber
val HotPink = DR.You
val BoostCyan = DR.Boost
val SuccessLime = DR.Ok
val DangerCoral = DR.Bad
val TextPrimary = DR.TextHi
val TextSecondary = DR.TextMid
val TextMuted = DR.TextLo

/** v6: primary CTAs are solid amber — no gradients. */
val PrimaryCtaGradient = Brush.horizontalGradient(listOf(DR.Amber, DR.Amber))
val WordmarkGradient = Brush.horizontalGradient(listOf(DR.TextHi, DR.TextHi))

val SlateAbyss = DR.Bg
val SlateSurface = DR.S2
val SlateBorder = DR.Line2
val ArcadeCrimson = DR.You
val RivalTeal = DR.Boost
val SkyCyan = DR.TextMid
val DriftAmber = DR.Amber
val RulerYellow = DR.Amber
val GasGreen = DR.Ok
val HostEmerald = DR.Ok
val JoinBlue = DR.S3
val ItemRed = DR.Bad
val DeskWood = Color(0xFF8B5A2B)
val DeskWoodDark = Color(0xFF6B421D)
val BookBlue = Color(0xFF2563EB)
val BookRed = Color(0xFFDC2626)
val BookGreen = Color(0xFF16A34A)
val DriverHelmet = Color(0xFFFDE047)

object DRColors {
    val NightNavy = DR.Bg
    val SlateCard = DR.S1
    val ElevatedCard = DR.S2
    val StrokeHairline = DR.Line2
    val KartGold = DR.Amber
    val DriftOrange = DR.Amber
    val MintFinish = DR.Ok
    val ElectricBlue = DR.TextMid
    val DangerRed = DR.Bad
    val RoyalPurple = DR.TextMid
    val CrispWhite = DR.TextHi
    val MutedSlate = DR.TextMid
}

object DRRadius {
    val Card = 8.dp
    val Button = 8.dp
    val Pill = 6.dp
}
