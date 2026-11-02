package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.MinimapDot
import com.example.game.Tracks
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape

/**
 * v6 minimap: 124x96 chamfered plate (42% Bg), white 55% track line, rivals as small dots
 * (leader gold, 2nd silver), the local player as a larger pink dot with a white ring drawn last.
 */
@Composable
fun Minimap(
    trackId: Int,
    dots: List<MinimapDot>,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 96.dp,
    widthDp: Dp = 124.dp,
    leaderSlot: Int = -1,
    secondSlot: Int = -1
) {
    val track = Tracks.byId(trackId)
    Box(
        modifier = modifier
            .size(width = widthDp, height = sizeDp)
            .clip(DRShape.HudPad)
            .background(DR.Bg.copy(alpha = 0.42f))
            .testTag("hud_minimap")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 9.dp.toPx()
            val spanX = (track.deskHalfExtentX * 1.65f).coerceAtLeast(1f)
            val spanZ = (track.deskHalfExtentZ * 1.45f).coerceAtLeast(1f)
            val scale = minOf((w - pad * 2f) / spanX, (h - pad * 2f) / spanZ)

            fun toScreen(wx: Float, wz: Float): Offset {
                val sx = w * 0.5f + wx * scale
                val sy = h * 0.5f + wz * scale
                return Offset(sx.coerceIn(4f, w - 4f), sy.coerceIn(4f, h - 4f))
            }

            val samples = track.samples
            if (samples.isNotEmpty()) {
                val path = Path()
                val first = toScreen(samples[0].pos.x, samples[0].pos.z)
                path.moveTo(first.x, first.y)
                for (i in 1 until samples.size step 3) {
                    val p = toScreen(samples[i].pos.x, samples[i].pos.z)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color = Color.White.copy(alpha = 0.55f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val start = toScreen(samples[0].pos.x, samples[0].pos.z)
                drawCircle(DR.Amber, radius = 2.dp.toPx(), center = start)
            }

            val rival = 3.dp.toPx()
            for (d in dots) {
                if (d.isLocal) continue
                val pt = toScreen(d.x, d.z)
                val c = when (d.slotIndex) {
                    leaderSlot -> DR.Gold
                    secondSlot -> DR.Silver
                    else -> Color.White.copy(alpha = 0.9f)
                }
                drawCircle(Color.Black.copy(alpha = 0.6f), radius = rival + 1.2f, center = pt)
                drawCircle(c, radius = rival, center = pt)
            }
            dots.firstOrNull { it.isLocal }?.let { d ->
                val pt = toScreen(d.x, d.z)
                drawCircle(Color.White, radius = 5.dp.toPx(), center = pt)
                drawCircle(DR.You, radius = 3.6.dp.toPx(), center = pt)
            }
        }
    }
}

/** v6 top-right HUD cluster: the only pause control, with the minimap under it. */
@Composable
fun HudPauseAndMap(
    trackId: Int,
    dots: List<MinimapDot>,
    showMinimap: Boolean,
    onPause: () -> Unit,
    leaderSlot: Int = -1,
    secondSlot: Int = -1,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(DRShape.Card)
                .background(DR.HudPlate2)
                .border(1.dp, DR.HudLine, DRShape.Card)
                .clickable(role = Role.Button, onClick = onPause)
                .testTag("hud_pause_button")
        ) {
            Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.cd_pause), tint = Color.White, modifier = Modifier.size(24.dp))
        }
        if (showMinimap) {
            Minimap(trackId = trackId, dots = dots, leaderSlot = leaderSlot, secondSlot = secondSlot)
        }
    }
}
