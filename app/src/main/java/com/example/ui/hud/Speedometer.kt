package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkElevated
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.SuccessLime
import com.example.ui.theme.TextPrimary

@Composable
fun Speedometer(
    speedKmh: Int,
    speedRatio: Float,
    isDrifting: Boolean,
    driftChargeRatio: Float,
    sparkTier: Int,
    modifier: Modifier = Modifier
) {
    val tierColor = when (sparkTier) {
        3 -> Color(0xFFA855F7) // Purple Ultra Mini-Turbo
        2 -> LampAmber         // Orange Super Mini-Turbo
        1 -> BoostCyan         // Blue Mini-Turbo
        else -> SuccessLime
    }
    val tierLabel = when (sparkTier) {
        3 -> "PURPLE ULTRA!"
        2 -> "ORANGE SUPER!"
        1 -> "BLUE TURBO!"
        else -> if (isDrifting) "DRIFT CHARGE" else "SPEED"
    }

    Column(
        modifier = modifier
            .widthIn(min = 104.dp, max = 148.dp)
            .background(InkSurface.copy(alpha = 0.88f), RoundedCornerShape(14.dp))
            .border(1.dp, InkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("hud_speedometer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 102.dp, height = 38.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawArc(
                    color = InkElevated,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(6f, 4f),
                    size = Size(w - 12f, h * 1.55f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = tierColor,
                    startAngle = 190f,
                    sweepAngle = 160f * speedRatio.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(6f, 4f),
                    size = Size(w - 12f, h * 1.55f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "$speedKmh",
                    style = DRType.HudNumber.copy(fontSize = 20.sp, lineHeight = 22.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "KM/H",
                    style = DRType.Caption.copy(fontSize = 9.sp, fontWeight = FontWeight.W700),
                    color = BoostCyan,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { if (isDrifting) driftChargeRatio else speedRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = tierColor,
            trackColor = InkElevated
        )
        Text(
            text = tierLabel,
            style = DRType.Caption.copy(fontSize = 9.sp, fontWeight = FontWeight.W800),
            color = tierColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
