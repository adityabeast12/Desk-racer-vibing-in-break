package com.example.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ControlSchemeType
import com.example.game.InputState
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * Part F1-F5: Best-in-class mobile steering inside BOTTOM_LEFT zone (0-40% w x 58-100% h):
 * - SMART / PRO: thumb landing point becomes the centre, ±60 dp = full lock, response curve x^1.6,
 *   dead zone 6 dp, 50 ms touch smoothing.
 * - BUTTONS: large 88 dp ◀ ▶ buttons with a soft analogue ramp (0->1 over 120 ms while held).
 * - TILT: calibrated rotation-vector indicator + left-half BRAKE button (>= 72 dp).
 */
@Composable
fun SteerZone(
    inputState: InputState,
    controlScheme: ControlSchemeType,
    sensitivity: Float,
    opacity: Float = 0.90f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fullLockPx = with(density) { 60.dp.toPx() }
    val deadZonePx = with(density) { 6.dp.toPx() }

    when (controlScheme) {
        ControlSchemeType.SMART, ControlSchemeType.PRO -> {
            var isTouching by remember { mutableStateOf(false) }
            var anchorX by remember { mutableFloatStateOf(0f) }
            var anchorY by remember { mutableFloatStateOf(0f) }
            var currentX by remember { mutableFloatStateOf(0f) }
            var targetSteer by remember { mutableFloatStateOf(0f) }
            var smoothedSteer by remember { mutableFloatStateOf(0f) }

            // Part F5: 50 ms steering input smoothing
            LaunchedEffect(isTouching, targetSteer) {
                while (isTouching || abs(smoothedSteer) > 0.01f) {
                    val goal = if (isTouching) targetSteer else 0f
                    val dt = 0.016f
                    val alpha = dt / (0.050f + dt)
                    smoothedSteer += (goal - smoothedSteer) * alpha
                    if (!isTouching && abs(smoothedSteer) < 0.02f) {
                        smoothedSteer = 0f
                    }
                    inputState.updateSteer(smoothedSteer.coerceIn(-1f, 1f))
                    delay(16L)
                }
                inputState.updateSteer(0f)
            }

            val steerZoneDesc = stringResource(R.string.cd_steer_zone)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .semantics { contentDescription = steerZoneDesc }
                    .pointerInput(sensitivity, controlScheme) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isTouching = true
                            anchorX = down.position.x
                            anchorY = down.position.y
                            currentX = anchorX
                            targetSteer = 0f

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    currentX = change.position.x
                                    val dx = currentX - anchorX
                                    val absDx = abs(dx)
                                    targetSteer = if (absDx <= deadZonePx) {
                                        0f
                                    } else {
                                        val norm = ((absDx - deadZonePx) / (fullLockPx - deadZonePx).coerceAtLeast(1f))
                                            .coerceIn(0f, 1f)
                                        val curved = norm.pow(1.6f)
                                        (sign(dx) * curved * sensitivity).coerceIn(-1f, 1f)
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            isTouching = false
                            targetSteer = 0f
                        }
                    }
                    .testTag("steer_thumb_zone")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val alphaScale = opacity.coerceIn(0.3f, 1f)
                    if (isTouching) {
                        drawCircle(
                            color = BoostCyan.copy(alpha = 0.38f * alphaScale),
                            radius = fullLockPx,
                            center = Offset(anchorX, anchorY),
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = BoostCyan.copy(alpha = 0.78f * alphaScale),
                            radius = 30f,
                            center = Offset(
                                currentX.coerceIn(anchorX - fullLockPx, anchorX + fullLockPx),
                                anchorY
                            )
                        )
                    } else {
                        val cx = size.width * 0.36f
                        val cy = size.height * 0.62f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f * alphaScale),
                            radius = 52f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = BoostCyan.copy(alpha = 0.45f * alphaScale),
                            radius = 20f,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }

        ControlSchemeType.BUTTONS -> {
            // Part F4: Large 88 dp ◀ ▶ buttons with a soft analogue ramp (0->1 over 120 ms while held)
            var activeDir by remember { mutableIntStateOf(0) }
            var rampedSteer by remember { mutableFloatStateOf(0f) }

            LaunchedEffect(activeDir) {
                while (activeDir != 0 || abs(rampedSteer) > 0.01f) {
                    val step = 16f / 120f // 120 ms ramp 0 -> 1
                    rampedSteer = when {
                        activeDir < 0 -> (rampedSteer - step).coerceAtLeast(-1f)
                        activeDir > 0 -> (rampedSteer + step).coerceAtMost(1f)
                        rampedSteer > 0f -> (rampedSteer - step * 1.5f).coerceAtLeast(0f)
                        else -> (rampedSteer + step * 1.5f).coerceAtMost(0f)
                    }
                    inputState.updateSteer(rampedSteer)
                    delay(16L)
                }
                inputState.updateSteer(0f)
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SteerDirButton(
                        label = "◀",
                        testTag = "btn_steer_left",
                        opacity = opacity,
                        onPressChanged = { pressed ->
                            activeDir = if (pressed) -1 else if (activeDir == -1) 0 else activeDir
                        }
                    )
                    SteerDirButton(
                        label = "▶",
                        testTag = "btn_steer_right",
                        opacity = opacity,
                        onPressChanged = { pressed ->
                            activeDir = if (pressed) 1 else if (activeDir == 1) 0 else activeDir
                        }
                    )
                }
            }
        }

        ControlSchemeType.TILT -> {
            // Part F3: Rotation-vector tilt scheme with BRAKE on the left half
            var leftBrakePressed by remember { mutableStateOf(false) }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .background(InkSurface.copy(alpha = 0.82f * opacity), RoundedCornerShape(10.dp))
                            .border(1.dp, InkBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "GYRO TILT (±25°)",
                            style = DRType.Caption.copy(fontWeight = FontWeight.W700),
                            color = BoostCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                if (leftBrakePressed) DangerCoral.copy(alpha = 0.9f)
                                else InkSurface.copy(alpha = 0.82f * opacity)
                            )
                            .border(2.dp, DangerCoral, CircleShape)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    leftBrakePressed = true
                                    inputState.brakePressed = true
                                    waitForUpOrCancellation()
                                    leftBrakePressed = false
                                    inputState.brakePressed = false
                                }
                            }
                            .testTag("btn_tilt_left_brake")
                    ) {
                        Text(
                            text = "BRAKE",
                            style = DRType.Label.copy(fontWeight = FontWeight.W800),
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteerDirButton(
    label: String,
    testTag: String,
    opacity: Float,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(
                if (pressed) LampAmber.copy(alpha = 0.90f)
                else InkSurface.copy(alpha = 0.85f * opacity)
            )
            .border(2.dp, if (pressed) Color.White else InkBorder, CircleShape)
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
            style = DRType.Title.copy(fontSize = 26.sp),
            color = if (pressed) Color(0xFF0B1020) else TextPrimary,
            maxLines = 1
        )
    }
}
