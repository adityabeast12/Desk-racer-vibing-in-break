package com.example.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.game.DeskRacerGlRenderer
import com.example.game.DeskRacerUiState
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.RivalTeal
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan

@Composable
fun DeskArena3DCanvas(
    state: DeskRacerUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember { DeskRacerGlRenderer() }
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    SideEffect {
        renderer.currentState = state
    }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView.onPause()
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        update = { renderer.currentState = state },
        modifier = modifier
            .fillMaxSize()
            .testTag("desk_arena_3d_canvas")
    )
}

@Composable
fun DeskTrackMiniMap(
    state: DeskRacerUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
            .testTag("desk_minimap")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun toMap(wx: Float, wz: Float): Offset {
                val nx = ((wx + 40f) / 80f).coerceIn(0f, 1f)
                val nz = ((wz + 40f) / 80f).coerceIn(0f, 1f)
                return Offset(nx * w, nz * h)
            }

            // Desk border
            drawRect(
                color = Color(0xFF8B5A2B).copy(alpha = 0.35f),
                topLeft = Offset(4f, 4f),
                size = Size(w - 8f, h - 8f)
            )
            drawRect(
                color = Color(0x66FFFFFF),
                topLeft = Offset(4f, 4f),
                size = Size(w - 8f, h - 8f),
                style = Stroke(width = 1.2f)
            )

            // Books on radar
            for (b in state.books) {
                val topLeft = toMap(b.x - b.width / 2f, b.z - b.depth / 2f)
                val br = toMap(b.x + b.width / 2f, b.z + b.depth / 2f)
                drawRect(
                    color = Color(b.colorHex),
                    topLeft = topLeft,
                    size = Size((br.x - topLeft.x).coerceAtLeast(3f), (br.y - topLeft.y).coerceAtLeast(3f))
                )
            }

            // Yellow Ruler Ramp on radar
            val rampTl = toMap(state.rulerRamp.x - 3f, state.rulerRamp.z - 6f)
            val rampBr = toMap(state.rulerRamp.x + 3f, state.rulerRamp.z + 6f)
            drawRect(
                color = RulerYellow,
                topLeft = rampTl,
                size = Size(rampBr.x - rampTl.x, rampBr.y - rampTl.y)
            )

            // Item boxes
            for (box in state.itemBoxes) {
                if (box.visible) {
                    drawCircle(
                        color = SkyCyan,
                        radius = 2.5f,
                        center = toMap(box.x, box.z)
                    )
                }
            }

            // AI Rival Kart
            if (state.aiRivalEnabled) {
                drawCircle(
                    color = RivalTeal,
                    radius = 4.2f,
                    center = toMap(state.aiRivalKart.x, state.aiRivalKart.z)
                )
            }

            // Remote Hotspot Peers
            for ((_, peer) in state.remoteKarts) {
                drawCircle(
                    color = Color.White,
                    radius = 4.2f,
                    center = toMap(peer.x, peer.z)
                )
            }

            // Local Player Kart
            val myPos = toMap(state.localKart.x, state.localKart.z)
            drawCircle(
                color = Color.White,
                radius = 5.8f,
                center = myPos
            )
            drawCircle(
                color = ArcadeCrimson,
                radius = 4.2f,
                center = myPos
            )
        }
    }
}
