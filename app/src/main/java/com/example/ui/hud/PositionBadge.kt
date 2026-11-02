package com.example.ui.hud

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.LampAmber
import com.example.ui.theme.SuccessLime
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Part C1, C2, F2, F4, F6:
 * - Position: 44 sp ExtraBold + "/8" 14 sp; lap 13 sp below.
 * - Animates position changes with vertical slide + 1.15x pulse; ▲/▼ chip only for >= 400 ms changes.
 * - Lap display capped at totalLaps and shows "FINISHED" after crossing the finish line.
 * - 45% black glass (#73000000) with 1 dp white 12% border (#1FFFFFFF) and drop shadows on text.
 */
@Composable
fun PositionBadge(
    position: Int,
    totalRacers: Int,
    lap: Int,
    totalLaps: Int,
    isFinished: Boolean = false,
    coinsHeld: Int = 0,
    positionDeltaChip: String? = null,
    balloonsRemaining: Int = -1,
    modifier: Modifier = Modifier
) {
    val rankColor = when (position) {
        1 -> LampAmber
        2 -> Color(0xFFE2E8F0)
        3 -> Color(0xFFF97316)
        else -> BoostCyan
    }
    val suffix = when (position) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }

    // Part C1: 1.15x scale pulse on position change
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(position) {
        pulseScale.snapTo(1.15f)
        pulseScale.animateTo(1f, animationSpec = tween(durationMillis = 180))
    }

    val safeLap = lap.coerceIn(1, totalLaps.coerceAtLeast(1))
    val finishedText = stringResource(R.string.hud_finished)

    Column(
        modifier = modifier
            .widthIn(min = 92.dp, max = 176.dp)
            .background(HudGlassBg, RoundedCornerShape(12.dp))
            .border(1.dp, HudGlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("hud_position_badge"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedContent(
                targetState = position,
                transitionSpec = {
                    if (targetState < initialState) {
                        (slideInVertically { height -> height / 2 } + fadeIn(tween(120)))
                            .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(120)))
                    } else {
                        (slideInVertically { height -> -height / 2 } + fadeIn(tween(120)))
                            .togetherWith(slideOutVertically { height -> height / 2 } + fadeOut(tween(120)))
                    }
                },
                label = "position_slide"
            ) { targetPos ->
                val sfx = when (targetPos) {
                    1 -> "st"
                    2 -> "nd"
                    3 -> "rd"
                    else -> "th"
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.scale(pulseScale.value)
                ) {
                    Text(
                        text = "$targetPos",
                        style = DRType.HudNumber.copy(
                            fontSize = 44.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = HudTextShadow
                        ),
                        color = rankColor,
                        maxLines = 1
                    )
                    Text(
                        text = sfx,
                        style = DRType.TabularLabel.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = HudTextShadow
                        ),
                        color = rankColor,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }

            Text(
                text = "/$totalRacers",
                style = DRType.TabularLabel.copy(
                    fontSize = 14.sp,
                    shadow = HudTextShadow
                ),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 5.dp)
            )

            if (!positionDeltaChip.isNullOrBlank()) {
                val isGain = positionDeltaChip.startsWith("▲")
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isGain) SuccessLime else DangerCoral,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = positionDeltaChip,
                        style = DRType.Caption.copy(
                            fontWeight = FontWeight.W800,
                            fontFeatureSettings = "tnum"
                        ),
                        color = Color(0xFF0B1020),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val lapLabel = when {
                balloonsRemaining >= 0 -> "BALLOONS $balloonsRemaining"
                isFinished -> finishedText
                else -> "LAP $safeLap/$totalLaps"
            }
            Text(
                text = lapLabel,
                style = DRType.TabularLabel.copy(
                    fontSize = 13.sp,
                    shadow = HudTextShadow
                ),
                color = if (isFinished) LampAmber else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hud_lap_counter")
            )
            Text(
                text = "COIN ${coinsHeld.coerceIn(0, 10)}",
                style = DRType.TabularLabel.copy(
                    fontSize = 12.sp,
                    shadow = HudTextShadow
                ),
                color = LampAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hud_coin_counter")
            )
        }
    }
}
