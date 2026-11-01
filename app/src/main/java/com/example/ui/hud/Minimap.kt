package com.example.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.game.MinimapDot
import com.example.game.Tracks
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkSurface

/**
 * Part E1 & E2: Minimap sits strictly in LEFT_MID (0-18% w x 26-56% h), never intruding into
 * the BOTTOM_LEFT steering zone, and is non-interactive.
 */
@Composable
fun Minimap(
    trackId: Int,
    dots: List<MinimapDot>,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 108.dp
) {
    val track = Tracks.byId(trackId)
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(14.dp))
            .background(InkSurface.copy(alpha = 0.82f))
            .border(1.5.dp, InkBorder, RoundedCornerShape(14.dp))
            .testTag("hud_minimap")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 8f
            val scaleX = (w - pad * 2f) / (track.deskHalfExtentX * 1.65f)
            val scaleZ = (h - pad * 2f) / (track.deskHalfExtentZ * 1.45f)

            fun toScreen(wx: Float, wz: Float): Offset {
                val sx = w * 0.5f + wx * scaleX
                val sy = h * 0.5f + wz * scaleZ
                return Offset(sx.coerceIn(4f, w - 4f), sy.coerceIn(4f, h - 4f))
            }

            val path = Path()
            val samples = track.samples
            if (samples.isNotEmpty()) {
                val first = toScreen(samples[0].pos.x, samples[0].pos.z)
                path.moveTo(first.x, first.y)
                for (i in 1 until samples.size step 3) {
                    val p = toScreen(samples[i].pos.x, samples[i].pos.z)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color = Color(0x44FFFFFF), style = Stroke(width = 6f))
                drawPath(path, color = BoostCyan.copy(alpha = 0.75f), style = Stroke(width = 2.5f))
            }

            for (d in dots) {
                val pt = toScreen(d.x, d.z)
                if (d.isLocal) {
                    drawCircle(color = Color.White, radius = 6.2f, center = pt)
                    drawCircle(color = Color(d.colorHex), radius = 4.5f, center = pt)
                } else {
                    drawCircle(color = Color(0xFF0B1020), radius = 4.6f, center = pt)
                    drawCircle(color = Color(d.colorHex), radius = 3.4f, center = pt)
                }
            }
        }
    }
}
