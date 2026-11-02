package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.InkElevated
import com.example.ui.theme.LampAmber
import com.example.ui.theme.SuccessLime
import com.example.ui.theme.TextPrimary

/**
 * Part F2, F4, F5, F6:
 * - Speedometer: compact 120 x 32 dp bar + number inside BOTTOM_CENTER (43-57% w x 84-100% h).
 * - Contextual hiding (Part F5): hides the "SPEED" label after the player's first 3 completed races
 *   (still displays drift spark tier labels while drifting).
 * - Uses 45% black glass (#73000000) with 1 dp white 12% border (#1FFFFFFF) and drop shadow on text.
 */
@Composable
fun Speedometer(
    speedKmh: Int,
    speedRatio: Float,
    isDrifting: Boolean,
    driftChargeRatio: Float,
    sparkTier: Int,
    showSpeedLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tierColor = when (sparkTier) {
        3 -> Color(0xFFA855F7) // Purple Ultra Mini-Turbo
        2 -> LampAmber         // Orange Super Mini-Turbo
        1 -> BoostCyan         // Blue Mini-Turbo
        else -> SuccessLime
    }
    val tierLabel: String? = when (sparkTier) {
        3 -> "PURPLE ULTRA!"
        2 -> "ORANGE SUPER!"
        1 -> "BLUE TURBO!"
        else -> when {
            isDrifting -> "DRIFT"
            showSpeedLabel -> "SPEED"
            else -> null
        }
    }

    Column(
        modifier = modifier
            .width(120.dp)
            .heightIn(min = 32.dp)
            .background(HudGlassBg, RoundedCornerShape(10.dp))
            .border(1.dp, HudGlassBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("hud_speedometer"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$speedKmh",
                    style = DRType.HudNumber.copy(
                        fontSize = 16.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        shadow = HudTextShadow
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "KM/H",
                    style = DRType.Caption.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.W700,
                        shadow = HudTextShadow
                    ),
                    color = BoostCyan,
                    maxLines = 1
                )
            }
            if (tierLabel != null) {
                Text(
                    text = tierLabel,
                    style = DRType.Caption.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.W800,
                        shadow = HudTextShadow
                    ),
                    color = tierColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (if (isDrifting) driftChargeRatio else speedRatio).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = tierColor,
            trackColor = InkElevated
        )
    }
}
