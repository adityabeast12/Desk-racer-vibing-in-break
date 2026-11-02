package com.example.ui.hud

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DR
import com.example.ui.theme.DRType
import com.example.ui.theme.SkewShape

/**
 * v6 bottom-centre speed plate: italic tabular speed + KM/H, and a skewed cyan drift-charge bar.
 * Cyan is reserved for boost/drift. Tier name appears when a mini-turbo is charged.
 */
@Composable
fun Speedometer(
    speedKmh: Int,
    speedRatio: Float,
    isDrifting: Boolean,
    driftChargeRatio: Float,
    sparkTier: Int,
    modifier: Modifier = Modifier
) {
    val charge by animateFloatAsState(driftChargeRatio.coerceIn(0f, 1f), tween(90), label = "drift_charge")
    val tierLabel = when (sparkTier) {
        3 -> "ULTRA BOOST"
        2 -> "SUPER BOOST"
        1 -> "MINI BOOST"
        else -> if (isDrifting) "DRIFT" else "DRIFT BOOST"
    }
    Column(
        modifier = modifier
            .width(176.dp)
            .clip(SkewShape(8f))
            .background(DR.HudPlate2)
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .testTag("hud_speedometer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${speedKmh.coerceAtLeast(0)}",
                style = DRType.NumXL.copy(fontSize = 28.sp, shadow = HudTextShadow),
                color = DR.TextHi,
                maxLines = 1
            )
            Text("KM/H", style = DRType.LabelS.copy(fontSize = 10.sp), color = DR.TextMid, modifier = Modifier.padding(bottom = 5.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(SkewShape(40f))
                .background(Color0x73)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(charge)
                    .fillMaxHeight()
                    .background(if (sparkTier > 0) DR.Boost else DR.Boost.copy(alpha = 0.6f))
            )
        }
        Text(
            text = tierLabel,
            style = DRType.LabelS.copy(fontSize = 9.sp),
            color = if (sparkTier > 0 || isDrifting) DR.Boost else DR.TextLo,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1
        )
    }
}

private val Color0x73 = androidx.compose.ui.graphics.Color(0x73000000)
