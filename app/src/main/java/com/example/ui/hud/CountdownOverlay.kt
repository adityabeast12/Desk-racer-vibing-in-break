package com.example.ui.hud

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DRKerbStrip
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType
import java.util.Locale

/**
 * v6 centre overlays (the only things allowed in the centre, and only while true):
 * countdown (amber Display numerals on a dark band), wrong-way plate, lap/final-lap banner on a
 * kerb band, and the post-first-finish "race ends in" pill. Event toasts live in [HudEventFeed].
 */
@Composable
fun CountdownOverlay(
    countdownNumber: Int,
    showRocketStartTip: Boolean,
    isWrongWay: Boolean,
    bannerText: String?,
    toastText: String? = null,
    dnfCountdownSec: Int = -1,
    /** Grand Prix: "DESK CUP · RACE 2/4" above the numbers so the player knows where they are. */
    cupCaption: String? = null,
    modifier: Modifier = Modifier
) {
    val band = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.25f to DR.Bg.copy(alpha = 0.75f),
        0.75f to DR.Bg.copy(alpha = 0.75f),
        1f to Color.Transparent
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        AnimatedVisibility(visible = dnfCountdownSec >= 0, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .clip(DRShape.Card)
                    .background(DR.HudPlate2)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("hud_dnf_countdown"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = DR.Bad, modifier = Modifier.size(18.dp))
                Text("RACE ENDS IN", style = DRType.Label.copy(fontSize = 12.sp), color = DR.TextHi)
                Text(String.format(Locale.US, "0:%02d", dnfCountdownSec.coerceAtLeast(0)), style = DRType.NumL, color = DR.Amber)
            }
        }

        AnimatedVisibility(visible = countdownNumber in 0..3, enter = fadeIn(tween(120)), exit = fadeOut(tween(200))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("hud_countdown")) {
                if (!cupCaption.isNullOrBlank()) {
                    Text(
                        text = cupCaption,
                        style = DRType.Label.copy(fontSize = 14.sp, shadow = HudTextShadow),
                        color = DR.TextHi,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 2.dp).testTag("hud_cup_caption")
                    )
                }
                Box(Modifier.fillMaxWidth().background(band), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = countdownNumber,
                        transitionSpec = {
                            (scaleIn(spring(0.55f, 500f), initialScale = 1.4f) + fadeIn(tween(80)))
                                .togetherWith(fadeOut(tween(160)))
                        },
                        label = "countdown_num"
                    ) { n ->
                        Text(
                            text = if (n == 0) "GO!" else "$n",
                            style = DRType.DisplayXL.copy(fontSize = if (n == 0) 104.sp else 128.sp, lineHeight = 120.sp, shadow = HudTextShadow),
                            color = if (n == 0) DR.Ok else DR.Amber,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (showRocketStartTip && countdownNumber in 1..3) {
                    Text(
                        text = "HOLD GAS AS “2” FADES FOR A ROCKET START",
                        style = DRType.LabelS.copy(fontSize = 11.sp, shadow = HudTextShadow),
                        color = DR.TextMid,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = isWrongWay, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .clip(DRShape.Card)
                    .background(DR.Bad.copy(alpha = 0.92f))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("hud_wrong_way"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.UTurnLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text("WRONG WAY", style = DRType.Title.copy(fontSize = 22.sp), color = Color.White)
            }
        }

        AnimatedVisibility(
            visible = !bannerText.isNullOrBlank(),
            enter = slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(200)) { -it / 2 } + fadeOut(tween(160))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("hud_banner")) {
                DRKerbStrip(Modifier.padding(horizontal = 40.dp))
                Box(Modifier.fillMaxWidth().background(band).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = bannerText.orEmpty().uppercase(),
                        style = DRType.Display.copy(fontSize = 38.sp, shadow = HudTextShadow),
                        color = if (bannerText.orEmpty().contains("FINAL", ignoreCase = true)) DR.Amber else DR.TextHi,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                DRKerbStrip(Modifier.padding(horizontal = 40.dp))
            }
        }
    }
}
