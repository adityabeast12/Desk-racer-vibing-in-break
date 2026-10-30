package com.example.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ControlSchemeType
import com.example.game.InputState
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.SkyCyan
import kotlin.math.abs

@Composable
fun SteerZone(
    inputState: InputState,
    controlScheme: ControlSchemeType,
    sensitivity: Float,
    modifier: Modifier = Modifier
) {
    val maxDragPx = with(LocalDensity.current) { 70.dp.toPx() }
    val deadZonePx = with(LocalDensity.current) { 5.dp.toPx() }

    when (controlScheme) {
        ControlSchemeType.THUMB_STEER -> {
            var isTouching by remember { mutableStateOf(false) }
            var anchorX by remember { mutableFloatStateOf(0f) }
            var anchorY by remember { mutableFloatStateOf(0f) }
            var currentX by remember { mutableFloatStateOf(0f) }
            var currentY by remember { mutableFloatStateOf(0f) }

            val steerZoneDesc = stringResource(R.string.cd_steer_zone)
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .semantics { contentDescription = steerZoneDesc }
                    .pointerInput(sensitivity) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isTouching = true
                            anchorX = down.position.x
                            anchorY = down.position.y
                            currentX = anchorX
                            currentY = anchorY
                            inputState.updateSteer(0f)

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    currentX = change.position.x
                                    currentY = change.position.y
                                    val dx = currentX - anchorX
                                    val rawSteer = if (abs(dx) < deadZonePx) {
                                        0f
                                    } else {
                                        ((dx / maxDragPx) * sensitivity).coerceIn(-1f, 1f)
                                    }
                                    inputState.updateSteer(rawSteer)
                                }
                            } while (event.changes.any { it.pressed })

                            isTouching = false
                            inputState.updateSteer(0f)
                        }
                    }
                    .testTag("steer_thumb_zone")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (isTouching) {
                        drawCircle(
                            color = Color(0x5538BDF8),
                            radius = maxDragPx,
                            center = Offset(anchorX, anchorY),
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = SkyCyan.copy(alpha = 0.75f),
                            radius = 34f,
                            center = Offset(currentX.coerceIn(anchorX - maxDragPx, anchorX + maxDragPx), anchorY)
                        )
                    } else {
                        // Subtle bottom-left resting steering indicator at 70% opacity
                        val cx = size.width * 0.36f
                        val cy = size.height * 0.76f
                        drawCircle(
                            color = Color(0x44FFFFFF),
                            radius = 64f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = Color(0x6638BDF8),
                            radius = 24f,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }

        ControlSchemeType.DPAD_BUTTONS -> {
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .padding(start = 16.dp, bottom = 16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SteerDirButton(
                        label = "◀",
                        testTag = "btn_steer_left",
                        onPressChanged = { pressed ->
                            inputState.updateSteer(if (pressed) -1f else 0f)
                        }
                    )
                    SteerDirButton(
                        label = "▶",
                        testTag = "btn_steer_right",
                        onPressChanged = { pressed ->
                            inputState.updateSteer(if (pressed) 1f else 0f)
                        }
                    )
                }
            }
        }

        ControlSchemeType.TILT_STEER -> {
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .padding(start = 16.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0x990F172A), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "TILT PHONE TO STEER",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = SkyCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun SteerDirButton(
    label: String,
    testTag: String,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (pressed) SkyCyan.copy(alpha = 0.85f) else Color(0xB31E293B))
            .border(2.dp, Color(0x88FFFFFF), CircleShape)
            .pointerInput(testTag) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPressChanged(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onPressChanged(false)
                }
            }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color.White
        )
    }
}
