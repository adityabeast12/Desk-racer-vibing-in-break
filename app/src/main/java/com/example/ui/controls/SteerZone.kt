package com.example.ui.controls

import android.graphics.Rect as AndroidRect
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.example.ui.hud.HudGlassBg
import com.example.ui.hud.HudGlassBorder
import com.example.ui.hud.HudTextShadow
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
 * Part G1, G2, G4:
 * - G1: Tracks `PointerId` from `awaitFirstDown` and reads ONLY changes matching `it.id == activePointerId`,
 *   releasing when that specific pointer goes up or is cancelled (fixes multi-touch pointer hijack).
 * - G2: Sets `View.setSystemGestureExclusionRects` on API 29+ for the lower 200 dp of the left and right edges.
 * - G4:
 *   - Smart: auto-gas, dead zone 6 dp, full lock 64 dp, curve `x^1.5`, return-to-centre smoothing 70 ms.
 *   - Pro: manual gas, curve `x^1.3`, sensitivity `0.6–1.6`.
 *   - Tilt: game rotation vector, `±22°` full lock, 12 Hz low-pass.
 *   - Buttons: 120 ms ramp.
 *   - Faint thumb ring where the steer gesture started.
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
    val view = LocalView.current
    val fullLockPx = with(density) { 64.dp.toPx() }
    val deadZonePx = with(density) { 6.dp.toPx() }
    val exclusionHeightPx = with(density) { 200.dp.roundToPx() }
    val exclusionWidthPx = with(density) { 48.dp.roundToPx() }

    // Part G2: Exclude lower 200 dp of left and right edges from Android system back gesture on API 29+
    DisposableEffect(view, exclusionHeightPx, exclusionWidthPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val updateExclusion = Runnable {
                val w = view.width
                val h = view.height
                if (w > 0 && h > 0) {
                    val top = (h - exclusionHeightPx).coerceAtLeast(0)
                    view.systemGestureExclusionRects = listOf(
                        AndroidRect(0, top, exclusionWidthPx.coerceAtMost(w), h),
                        AndroidRect((w - exclusionWidthPx).coerceAtLeast(0), top, w, h)
                    )
                }
            }
            view.post(updateExclusion)
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.systemGestureExclusionRects = emptyList()
            }
        }
    }

    when (controlScheme) {
        ControlSchemeType.SMART, ControlSchemeType.PRO -> {
            var isTouching by remember { mutableStateOf(false) }
            var anchorX by remember { mutableFloatStateOf(0f) }
            var anchorY by remember { mutableFloatStateOf(0f) }
            var currentX by remember { mutableFloatStateOf(0f) }
            var targetSteer by remember { mutableFloatStateOf(0f) }
            var smoothedSteer by remember { mutableFloatStateOf(0f) }

            val curveExponent = if (controlScheme == ControlSchemeType.PRO) 1.3f else 1.5f
            val clampedSens = sensitivity.coerceIn(0.6f, 1.6f)

            // Part G4: 70 ms return-to-centre smoothing (and responsive 35 ms active tracking)
            LaunchedEffect(isTouching, targetSteer) {
                while (isTouching || abs(smoothedSteer) > 0.01f) {
                    val goal = if (isTouching) targetSteer else 0f
                    val dt = 0.016f
                    val tau = if (isTouching) 0.035f else 0.070f // 70 ms return-to-centre smoothing
                    val alpha = dt / (tau + dt)
                    smoothedSteer += (goal - smoothedSteer) * alpha
                    if (!isTouching && abs(smoothedSteer) < 0.015f) {
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
                    .pointerInput(clampedSens, controlScheme) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val activePointerId: PointerId = down.id
                            isTouching = true
                            anchorX = down.position.x
                            anchorY = down.position.y
                            currentX = anchorX
                            targetSteer = 0f

                            var pointerActive = true
                            while (pointerActive) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == activePointerId }
                                if (change == null || !change.pressed) {
                                    pointerActive = false
                                } else {
                                    currentX = change.position.x
                                    val dx = currentX - anchorX
                                    val absDx = abs(dx)
                                    targetSteer = if (absDx <= deadZonePx) {
                                        0f
                                    } else {
                                        val norm = ((absDx - deadZonePx) / (fullLockPx - deadZonePx).coerceAtLeast(1f))
                                            .coerceIn(0f, 1f)
                                        val curved = norm.pow(curveExponent)
                                        (sign(dx) * curved * clampedSens).coerceIn(-1f, 1f)
                                    }
                                }
                            }

                            isTouching = false
                            targetSteer = 0f
                        }
                    }
                    .testTag("steer_thumb_zone")
            ) {
                // Part G4: Faint thumb ring where the steer gesture started
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val alphaScale = opacity.coerceIn(0.3f, 1f)
                    if (isTouching) {
                        drawCircle(
                            color = BoostCyan.copy(alpha = 0.28f * alphaScale),
                            radius = fullLockPx,
                            center = Offset(anchorX, anchorY),
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f * alphaScale),
                            radius = deadZonePx.coerceAtLeast(10f),
                            center = Offset(anchorX, anchorY),
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = BoostCyan.copy(alpha = 0.72f * alphaScale),
                            radius = 24f,
                            center = Offset(
                                currentX.coerceIn(anchorX - fullLockPx, anchorX + fullLockPx),
                                anchorY
                            )
                        )
                    } else {
                        val cx = size.width * 0.34f
                        val cy = size.height * 0.64f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.14f * alphaScale),
                            radius = 44f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = BoostCyan.copy(alpha = 0.30f * alphaScale),
                            radius = 16f,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }

        ControlSchemeType.BUTTONS -> {
            // Part G4: 120 ms analogue ramp with strict PointerId tracking
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
            // Part G4: Game rotation vector, ±22° full lock, 12 Hz low-pass + left-side brake
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
                            .background(HudGlassBg, RoundedCornerShape(10.dp))
                            .border(1.dp, HudGlassBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "GYRO TILT (±22°)",
                            style = DRType.Caption.copy(
                                fontWeight = FontWeight.W700,
                                shadow = HudTextShadow
                            ),
                            color = BoostCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .scale(if (leftBrakePressed) 0.94f else 1f)
                            .clip(CircleShape)
                            .background(
                                if (leftBrakePressed) DangerCoral.copy(alpha = 0.9f)
                                else InkSurface.copy(alpha = 0.80f * opacity)
                            )
                            .border(2.dp, DangerCoral, CircleShape)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val activePointerId = down.id
                                    leftBrakePressed = true
                                    inputState.brakePressed = true
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == activePointerId }
                                        if (ch == null || !ch.pressed) break
                                    }
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
            .size(84.dp)
            .scale(if (pressed) 0.94f else 1f)
            .clip(CircleShape)
            .background(
                if (pressed) LampAmber.copy(alpha = 0.90f)
                else InkSurface.copy(alpha = 0.80f * opacity)
            )
            .border(2.dp, if (pressed) Color.White else InkBorder, CircleShape)
            .pointerInput(testTag) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val activePointerId = down.id
                    pressed = true
                    onPressChanged(true)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == activePointerId }
                        if (ch == null || !ch.pressed) break
                    }
                    pressed = false
                    onPressChanged(false)
                }
            }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = DRType.Title.copy(fontSize = 24.sp),
            color = if (pressed) Color(0xFF0B1020) else TextPrimary,
            maxLines = 1
        )
    }
}
