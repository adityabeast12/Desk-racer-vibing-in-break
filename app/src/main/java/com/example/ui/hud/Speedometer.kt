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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.GasGreen
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.SkyCyan

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
        2 -> DriftAmber        // Orange Super Mini-Turbo
        1 -> SkyCyan           // Blue Mini-Turbo
        else -> GasGreen
    }
    val tierLabel = when (sparkTier) {
        3 -> "PURPLE ULTRA TURBO!"
        2 -> "ORANGE SUPER TURBO!"
        1 -> "BLUE MINI-TURBO!"
        else -> if (isDrifting) "CHARGING DRIFT..." else "SPEED"
    }

    Column(
        modifier = modifier
            .width(176.dp)
            .background(Color(0xB30F172A), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("hud_speedometer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 120.dp, height = 46.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawArc(
                    color = Color(0xFF1E293B),
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(8f, 6f),
                    size = Size(w - 16f, h * 1.6f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = tierColor,
                    startAngle = 190f,
                    sweepAngle = 160f * speedRatio.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(8f, 6f),
                    size = Size(w - 16f, h * 1.6f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    text = "$speedKmh",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "KM/H",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = SkyCyan,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { if (isDrifting) driftChargeRatio else speedRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = tierColor,
            trackColor = Color(0xFF1E293B)
        )
        Text(
            text = tierLabel,
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = tierColor
        )
    }
}
