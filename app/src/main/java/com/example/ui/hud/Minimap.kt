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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.MinimapDot
import com.example.game.Tracks

@Composable
fun Minimap(
    trackId: Int,
    dots: List<MinimapDot>,
    modifier: Modifier = Modifier
) {
    val track = Tracks.byId(trackId)
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xB30F172A))
            .border(1.5.dp, Color(0x44FFFFFF), RoundedCornerShape(14.dp))
            .testTag("hud_minimap")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 10f
            val scaleX = (w - pad * 2f) / (track.deskHalfExtentX * 1.65f)
            val scaleZ = (h - pad * 2f) / (track.deskHalfExtentZ * 1.45f)

            fun toScreen(wx: Float, wz: Float): Offset {
                val sx = w * 0.5f + wx * scaleX
                val sy = h * 0.5f + wz * scaleZ
                return Offset(sx.coerceIn(4f, w - 4f), sy.coerceIn(4f, h - 4f))
            }

            // Draw Track Spline Outline
            val path = Path()
            val samples = track.samples
            if (samples.isNotEmpty()) {
                val first = toScreen(samples[0].pos.x, samples[0].pos.z)
                path.moveTo(first.x, first.y)
                for (i in 1 until samples.size step 2) {
                    val p = toScreen(samples[i].pos.x, samples[i].pos.z)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color = Color(0x55FFFFFF), style = Stroke(width = 7f))
                drawPath(path, color = Color(0xFF94A3B8), style = Stroke(width = 3f))
            }

            // Draw All Racers
            for (d in dots) {
                val pt = toScreen(d.x, d.z)
                if (d.isLocal) {
                    val pulse = 6.5f + ((System.currentTimeMillis() % 600L) / 600f) * 3.5f
                    drawCircle(color = Color.White, radius = pulse, center = pt)
                    drawCircle(color = Color(d.colorHex), radius = 5.2f, center = pt)
                } else {
                    drawCircle(color = Color(0xFF0F172A), radius = 5.0f, center = pt)
                    drawCircle(color = Color(d.colorHex), radius = 3.8f, center = pt)
                }
            }
        }
    }
}
