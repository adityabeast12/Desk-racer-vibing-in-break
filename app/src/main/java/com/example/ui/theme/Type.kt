package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/** UI text: names, descriptions, settings rows. */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.W400),
    Font(R.font.poppins_medium, FontWeight.W500),
    Font(R.font.poppins_semibold, FontWeight.W600),
    Font(R.font.poppins_bold, FontWeight.W700),
    Font(R.font.poppins_extrabold, FontWeight.W800),
    Font(R.font.poppins_black, FontWeight.W900),
    Font(R.font.poppins_italic, FontWeight.W400, FontStyle.Italic)
)

/** Display: hero names, race position, screen titles. Always italic. */
val Kanit = FontFamily(
    Font(R.font.kanit_bold_italic, FontWeight.W700, FontStyle.Italic),
    Font(R.font.kanit_extrabold_italic, FontWeight.W800, FontStyle.Italic),
    Font(R.font.kanit_black_italic, FontWeight.W900, FontStyle.Italic)
)

/** Numerics + tracked labels. Has real tabular figures (tnum) — Poppins does not. */
val BarlowSC = FontFamily(
    Font(R.font.barlow_sc_medium, FontWeight.W500),
    Font(R.font.barlow_sc_semibold, FontWeight.W600),
    Font(R.font.barlow_sc_bold, FontWeight.W700),
    Font(R.font.barlow_sc_bold_italic, FontWeight.W700, FontStyle.Italic)
)

object DRType {
    // DISPLAY — Kanit Black Italic
    val DisplayXL = TextStyle(fontFamily = Kanit, fontWeight = FontWeight.W900, fontStyle = FontStyle.Italic, fontSize = 72.sp, lineHeight = 70.sp, letterSpacing = (-0.5).sp)
    val Display = DisplayXL.copy(fontSize = 40.sp, lineHeight = 40.sp)
    val DisplayM = DisplayXL.copy(fontSize = 32.sp, lineHeight = 32.sp)
    // TITLE — Kanit ExtraBold Italic
    val Title = TextStyle(fontFamily = Kanit, fontWeight = FontWeight.W800, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 24.sp)
    val TitleS = Title.copy(fontSize = 18.sp, lineHeight = 20.sp)
    val Headline = Title.copy(fontSize = 20.sp, lineHeight = 22.sp)
    // LABEL — Barlow SC Bold, tracked; callers uppercase via DRLabel
    val Label = TextStyle(fontFamily = BarlowSC, fontWeight = FontWeight.W700, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 1.7.sp)
    val LabelS = Label.copy(fontSize = 10.5.sp, lineHeight = 12.sp, letterSpacing = 1.5.sp)
    // NUMERIC — Barlow SC tabular
    private val num = TextStyle(fontFamily = BarlowSC, fontWeight = FontWeight.W700, fontFeatureSettings = "tnum")
    val NumXL = num.copy(fontSize = 28.sp, lineHeight = 30.sp, fontStyle = FontStyle.Italic)
    val NumL = num.copy(fontSize = 20.sp, lineHeight = 22.sp)
    val NumM = num.copy(fontSize = 15.sp, lineHeight = 18.sp)
    val NumS = num.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.W600)
    // BODY — Poppins
    val BodyStrong = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.W600, fontSize = 13.5.sp, lineHeight = 18.sp)
    val Body = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 19.sp)
    val Caption = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.W400, fontSize = 11.5.sp, lineHeight = 16.sp)

    // ---- Legacy aliases (re-pointed to the v6 families) ----
    val Subtitle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.W600, fontSize = 15.sp, lineHeight = 20.sp)
    val CardTitle = BodyStrong
    val MicroTag = num.copy(fontSize = 10.5.sp, lineHeight = 12.sp, letterSpacing = 0.6.sp)
    val HudNumber = num.copy(fontSize = 34.sp, lineHeight = 34.sp, fontStyle = FontStyle.Italic)
    val TabularSubtitle = num.copy(fontSize = 16.sp, lineHeight = 20.sp)
    val TabularLabel = num.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600)
    val TabularTimer = TabularLabel
}

val Typography = Typography(
    displayLarge = DRType.Display,
    displayMedium = DRType.DisplayM,
    headlineLarge = DRType.Title,
    headlineMedium = DRType.Headline,
    titleLarge = DRType.Title,
    titleMedium = DRType.Subtitle,
    titleSmall = DRType.BodyStrong,
    bodyLarge = DRType.Body,
    bodyMedium = DRType.Body,
    bodySmall = DRType.Caption,
    labelLarge = DRType.Label,
    labelMedium = DRType.LabelS,
    labelSmall = DRType.LabelS
)
