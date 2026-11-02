package com.example.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.tan

/** Cut top-right and bottom-left corners — the Desk GP signature shape. */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            close()
        }
        return Outline.Generic(p)
    }
}

/** Parallelogram leaning right: primary CTA, HUD pedals, toggles, progress, badges. */
class SkewShape(private val angleDeg: Float = 12f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val dx = (size.height * tan(Math.toRadians(angleDeg.toDouble())).toFloat()).coerceAtMost(size.width / 3f)
        val p = Path().apply {
            moveTo(dx, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - dx, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}

object DRShape {
    val Panel = ChamferShape(18.dp)
    val Card = ChamferShape(8.dp)
    val Control = ChamferShape(6.dp)
    val HudPad = ChamferShape(10.dp)
    val Cta = SkewShape(12f)
    val HudSkew = SkewShape(10f)
}

object DRDim {
    val Gutter = 16.dp
    val GutterL = 20.dp
    val Touch = 48.dp
    val CtaH = 50.dp
    val CtaHs = 46.dp
    val BtnH = 48.dp
    val RowH = 48.dp
    val SidePanelW = 340.dp
    val TabH = 40.dp
    val KerbH = 4.dp
}

object DRMotion {
    const val Press = 90
    const val Select = 160
    const val Panel = 220
    const val Screen = 320
    val Ease = FastOutSlowInEasing
}
