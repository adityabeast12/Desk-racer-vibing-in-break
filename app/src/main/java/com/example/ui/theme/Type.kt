package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Part D1: Single Poppins font family everywhere
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.W400),
    Font(R.font.poppins_medium, FontWeight.W500),
    Font(R.font.poppins_semibold, FontWeight.W600),
    Font(R.font.poppins_bold, FontWeight.W700),
    Font(R.font.poppins_extrabold, FontWeight.W800),
    Font(R.font.poppins_black, FontWeight.W900),
    Font(R.font.poppins_italic, FontWeight.W400, FontStyle.Italic)
)

// Part D2 & D3: DRType scale with tabular numerals ("tnum") for changing HUD numbers
object DRType {
    val Display = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W900,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    )
    val Title = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W800,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )
    val Headline = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    val Subtitle = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    val CardTitle = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
    val Body = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
    val Label = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp
    )
    val Caption = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )
    val MicroTag = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.3.sp,
        fontFeatureSettings = "tnum"
    )
    val HudNumber = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W800,
        fontSize = 34.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )
    val TabularSubtitle = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W700,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )
    val TabularLabel = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )
    val TabularTimer = TabularLabel
}

val Typography = Typography(
    displayLarge = DRType.Display,
    displayMedium = DRType.Title,
    headlineLarge = DRType.Title,
    headlineMedium = DRType.Headline,
    titleLarge = DRType.Headline,
    titleMedium = DRType.Subtitle,
    titleSmall = DRType.Label,
    bodyLarge = DRType.Body,
    bodyMedium = DRType.Label.copy(letterSpacing = 0.sp),
    bodySmall = DRType.Caption,
    labelLarge = DRType.Label,
    labelMedium = DRType.Caption,
    labelSmall = DRType.Caption
)
