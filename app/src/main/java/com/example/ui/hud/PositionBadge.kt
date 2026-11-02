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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.DRCoin
import com.example.ui.theme.DR
import com.example.ui.theme.DRType
import kotlinx.coroutines.delay

private fun ordinalSuffix(p: Int): String = when {
    p % 100 in 11..13 -> "TH"
    p % 10 == 1 -> "ST"
    p % 10 == 2 -> "ND"
    p % 10 == 3 -> "RD"
    else -> "TH"
}

/**
 * v6 HUD top-left block (no backing plate, hard text shadow):
 * - Position: Kanit Black Italic 64 sp (gold/silver/bronze for 1–3) + suffix + "/N".
 * - LAP n/N + race time (Barlow tabular, never jitters).
 * - Coins (and balloons in Battle). Position changes roll vertically and flash green/red.
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
    raceTimeMs: Long = -1L,
    modifier: Modifier = Modifier
) {
    val pulse = remember { Animatable(1f) }
    var lastPos by remember { mutableStateOf(position) }
    var flash by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(position) {
        if (position != lastPos) {
            flash = if (position < lastPos) DR.Ok else DR.Bad
            lastPos = position
            pulse.snapTo(1.12f)
            pulse.animateTo(1f, tween(180))
            delay(400)
            flash = null
        }
    }

    val shadow = HudTextShadow
    val safeLap = lap.coerceIn(1, totalLaps.coerceAtLeast(1))
    val finishedText = stringResource(R.string.hud_finished)

    Column(
        modifier = modifier
            .padding(start = 4.dp)
            .testTag("hud_position_badge")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Position $position of $totalRacers, lap $safeLap of $totalLaps"
            }
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = position,
                transitionSpec = {
                    if (targetState < initialState) {
                        (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(120)))
                            .togetherWith(slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(120)))
                    } else {
                        (slideInVertically(tween(180)) { -it / 2 } + fadeIn(tween(120)))
                            .togetherWith(slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(120)))
                    }
                },
                label = "position_roll"
            ) { p ->
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.scale(pulse.value)) {
                    Text(
                        text = "$p",
                        style = DRType.DisplayXL.copy(fontSize = 64.sp, lineHeight = 60.sp, shadow = shadow),
                        color = flash ?: if (p <= 3) DR.podium(p) else DR.TextHi,
                        maxLines = 1
                    )
                    Text(
                        text = ordinalSuffix(p),
                        style = DRType.Title.copy(fontSize = 22.sp, shadow = shadow),
                        color = DR.TextHi,
                        modifier = Modifier.padding(bottom = 8.dp, start = 1.dp),
                        maxLines = 1
                    )
                }
            }
            Text(
                text = "/$totalRacers",
                style = DRType.NumL.copy(fontSize = 18.sp, shadow = shadow),
                color = DR.TextMid,
                modifier = Modifier.padding(bottom = 10.dp, start = 6.dp),
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isFinished) {
                Text(finishedText.uppercase(), style = DRType.Label.copy(fontSize = 13.sp, shadow = shadow), color = DR.Amber)
            } else {
                Text("LAP", style = DRType.Label.copy(fontSize = 12.sp, shadow = shadow), color = DR.TextMid, modifier = Modifier.padding(bottom = 3.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$safeLap", style = DRType.NumL.copy(fontSize = 22.sp, shadow = shadow), color = DR.TextHi)
                    Text("/$totalLaps", style = DRType.NumL.copy(fontSize = 16.sp, shadow = shadow), color = DR.TextMid, modifier = Modifier.padding(bottom = 1.dp))
                }
            }
            if (raceTimeMs >= 0L) {
                Text(formatRaceTime(raceTimeMs), style = DRType.NumL.copy(fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600, shadow = shadow), color = DR.TextHi, modifier = Modifier.testTag("hud_lap_timer"))
            }
        }

        Row(
            modifier = Modifier.padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DRCoin(18.dp)
            Text(String.format(java.util.Locale.US, "%02d", coinsHeld.coerceAtLeast(0)), style = DRType.NumL.copy(fontSize = 18.sp, shadow = shadow), color = DR.TextHi)
            if (balloonsRemaining >= 0) {
                Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (i < balloonsRemaining) DR.You else DR.TextDis.copy(alpha = 0.6f))
                        )
                    }
                }
            }
            if (positionDeltaChip != null) {
                val up = positionDeltaChip.contains("▲") || positionDeltaChip.startsWith("+")
                Text(
                    text = positionDeltaChip,
                    style = DRType.NumS.copy(fontSize = 13.sp, shadow = shadow),
                    color = if (up) DR.Ok else DR.Bad,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}
