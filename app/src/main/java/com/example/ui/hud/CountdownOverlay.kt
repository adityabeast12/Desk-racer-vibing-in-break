package com.example.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.GasGreen
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import java.util.Locale

@Composable
fun CountdownOverlay(
    countdownNumber: Int,
    showRocketStartTip: Boolean,
    isWrongWay: Boolean,
    bannerText: String?,
    toastText: String?,
    dnfCountdownSec: Int = -1,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 0. 30-second DNF Countdown after first racer finishes (Part B4)
        AnimatedVisibility(
            visible = dnfCountdownSec >= 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xD90F172A), RoundedCornerShape(10.dp))
                    .border(1.5.dp, ArcadeCrimson, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("hud_dnf_countdown")
            ) {
                Text(
                    text = String.format(Locale.US, "RACE ENDS IN 0:%02d", dnfCountdownSec.coerceAtLeast(0)),
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = RulerYellow
                )
            }
        }

        // 1. 3-2-1-GO Countdown
        AnimatedVisibility(
            visible = countdownNumber in 0..3,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val label = if (countdownNumber == 0) "GO!" else "$countdownNumber"
            val color = when (countdownNumber) {
                3 -> ArcadeCrimson
                2 -> RulerYellow
                1 -> SkyCyan
                else -> GasGreen
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .background(Color(0xCC0F172A), RoundedCornerShape(20.dp))
                        .border(2.5.dp, color, RoundedCornerShape(20.dp))
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                        .testTag("hud_countdown")
                ) {
                    Text(
                        text = label,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        color = color
                    )
                }
                if (showRocketStartTip && countdownNumber in 1..3) {
                    Text(
                        text = "TIP: Tap GAS in the 0.35s window right as '2' fades for a ROCKET START!",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RulerYellow,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 2. WRONG WAY Warning
        AnimatedVisibility(
            visible = isWrongWay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xD97F1D1D), RoundedCornerShape(12.dp))
                    .border(2.dp, ArcadeCrimson, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("hud_wrong_way")
            ) {
                Text(
                    text = "⚠ WRONG WAY!",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }

        // 3. Lap / Final Lap / Finish Banner
        AnimatedVisibility(
            visible = !bannerText.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xCC0F172A), RoundedCornerShape(12.dp))
                    .border(1.5.dp, RulerYellow, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("hud_banner")
            ) {
                Text(
                    text = bannerText.orEmpty(),
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RulerYellow
                )
            }
        }

        // 4. Event Toasts ("Hit by <name>!", "<name> finished 1st!", "RESPAWN")
        AnimatedVisibility(
            visible = !toastText.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Toasts(message = toastText.orEmpty())
        }
    }
}
