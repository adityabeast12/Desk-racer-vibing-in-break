package com.example.ui.controls

import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.InputState
import com.example.ui.hud.HudTextShadow
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.HotPink
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.NightNavy
import com.example.ui.theme.SuccessLime
import com.example.ui.theme.TextPrimary

/**
 * Part F4, G1, G5:
 * - Visual button diameters at 100%: DRIFT 84 dp, ITEM 68 dp, BRAKE 52 dp (GAS 68 dp when manual).
 * - Hit area is 1.25x the visual button size so thumbs never miss while keeping visual footprint compact.
 * - Press scale 0.94, 40 ms haptic pulse on press, and strict `PointerId` tracking (fixes multi-touch hijack).
 * - DRIFT button shows tier colour fill and pulses when boost is ready.
 * - ITEM button swipe-down threshold = 24 dp, hold-for-shield = 220 ms.
 */
@Composable
fun ActionButtons(
    inputState: InputState,
    autoAccelerate: Boolean,
    driftChargeRatio: Float = 0f,
    sparkTier: Int = 0,
    opacity: Float = 0.90f,
    buttonScale: Float = 1.00f,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val swipeDownThresholdPx = with(density) { 24.dp.toPx() } // Part G5: 24 dp swipe-down threshold
    var lastHapticTier by remember { mutableIntStateOf(0) }

    LaunchedEffect(sparkTier) {
        if (sparkTier > lastHapticTier && sparkTier in 1..3) {
            triggerButtonHaptic40ms(view)
        }
        lastHapticTier = sparkTier
    }

    val infiniteTransition = rememberInfiniteTransition(label = "drift_pulse")
    val readyPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(240),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_ready_pulse"
    )

    val clampedScale = buttonScale.coerceIn(0.85f, 1.15f)
    val clampedOpacity = opacity.coerceIn(0.30f, 1.00f)

    // Visual sizes at 100% (Part F4): DRIFT 84 dp, ITEM 68 dp, BRAKE 52 dp
    val itemVisualDp = 68.dp * clampedScale
    val itemHitDp = (itemVisualDp * 1.25f).coerceAtLeast(64.dp)

    val brakeVisualDp = 52.dp * clampedScale
    val brakeHitDp = (brakeVisualDp * 1.25f).coerceAtLeast(56.dp)

    val driftVisualDp = 84.dp * clampedScale
    val driftHitDp = (driftVisualDp * 1.25f).coerceAtLeast(84.dp)

    val gasVisualDp = 64.dp * clampedScale
    val gasHitDp = (gasVisualDp * 1.25f).coerceAtLeast(64.dp)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Left column: ITEM button (visual 68 dp, hit area 1.25x = 85 dp)
        var itemPressed by remember { mutableStateOf(false) }
        val itemDesc = stringResource(R.string.cd_item_slot)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(itemHitDp)
                .semantics { contentDescription = itemDesc }
                .pointerInput(swipeDownThresholdPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val activePointerId: PointerId = down.id
                        val downUptimeMs = SystemClock.uptimeMillis()
                        itemPressed = true
                        triggerButtonHaptic40ms(view)
                        var maxDy = 0f
                        var shieldActivated = false

                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == activePointerId }
                            if (ch == null || !ch.pressed) break
                            val dy = ch.position.y - down.position.y
                            if (dy > maxDy) maxDy = dy
                            val heldMs = SystemClock.uptimeMillis() - downUptimeMs
                            // Part G5: Hold-for-shield = 220 ms
                            if (!shieldActivated && heldMs >= 220L) {
                                shieldActivated = true
                                inputState.updateItemHold(true, fireBackward = maxDy >= swipeDownThresholdPx)
                            }
                        }

                        itemPressed = false
                        val fireBack = maxDy >= swipeDownThresholdPx
                        if (shieldActivated) {
                            inputState.updateItemHold(false, fireBackward = fireBack)
                        } else {
                            inputState.triggerItem(fireBackward = fireBack)
                        }
                    }
                }
                .testTag("btn_action_item")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(itemVisualDp)
                    .scale(if (itemPressed) 0.94f else 1f)
                    .clip(CircleShape)
                    .background(HotPink.copy(alpha = 0.82f * clampedOpacity))
                    .border(2.dp, Color.White.copy(alpha = 0.78f), CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ITEM",
                        style = DRType.Label.copy(
                            fontWeight = FontWeight.W800,
                            fontSize = 12.sp,
                            shadow = HudTextShadow
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "▼ BACK",
                        style = DRType.Caption.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.W700
                        ),
                        color = TextPrimary.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
            }
        }

        // Middle column: BRAKE (52 dp visual) above DRIFT (84 dp visual)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ControlCircleButton(
                label = "BRAKE",
                contentDesc = stringResource(R.string.cd_brake),
                testTag = "btn_action_brake",
                visualSize = brakeVisualDp,
                hitSize = brakeHitDp,
                fontSizeSp = 10f,
                baseColor = InkSurface.copy(alpha = 0.82f * clampedOpacity),
                activeColor = DangerCoral,
                view = view,
                onPressChanged = { pressed -> inputState.brakePressed = pressed }
            )

            // DRIFT button (84 dp visual, 1.25x hit area, tier colour fill & ready pulse)
            var driftPressed by remember { mutableStateOf(false) }
            val driftDesc = stringResource(R.string.cd_drift)
            val tierRingColor = when (sparkTier) {
                3 -> Color(0xFFA855F7)
                2 -> LampAmber
                1 -> BoostCyan
                else -> LampAmber.copy(alpha = 0.75f)
            }
            val driftScale = when {
                driftPressed && sparkTier >= 1 -> readyPulse
                driftPressed -> 0.94f
                else -> 1f
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(driftHitDp)
                    .semantics { contentDescription = driftDesc }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val activePointerId: PointerId = down.id
                            driftPressed = true
                            triggerButtonHaptic40ms(view)
                            inputState.updateDrift(true)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == activePointerId }
                                if (ch == null || !ch.pressed) break
                            }
                            driftPressed = false
                            inputState.updateDrift(false)
                        }
                    }
                    .testTag("btn_action_drift")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(driftVisualDp)
                        .scale(driftScale)
                        .clip(CircleShape)
                        .background(
                            when {
                                sparkTier >= 1 -> tierRingColor.copy(alpha = 0.88f)
                                driftPressed -> LampAmber.copy(alpha = 0.92f)
                                else -> LampAmber.copy(alpha = 0.78f * clampedOpacity)
                            }
                        )
                        .border(
                            width = 2.5.dp,
                            color = if (sparkTier >= 1) Color.White else Color.White.copy(alpha = 0.75f),
                            shape = CircleShape
                        )
                ) {
                    if (driftChargeRatio > 0.02f || sparkTier > 0) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val strokeW = 5.dp.toPx()
                            val pad = strokeW * 0.7f
                            drawArc(
                                color = if (sparkTier >= 1) Color.White else tierRingColor,
                                startAngle = -90f,
                                sweepAngle = 360f * driftChargeRatio.coerceIn(0f, 1f),
                                useCenter = false,
                                topLeft = Offset(pad, pad),
                                size = Size(size.width - pad * 2f, size.height - pad * 2f),
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Text(
                        text = if (sparkTier >= 1) "RELEASE!" else "DRIFT",
                        style = DRType.Label.copy(
                            fontWeight = FontWeight.W900,
                            fontSize = if (sparkTier >= 1) 11.sp else 14.sp
                        ),
                        color = NightNavy,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right column: GAS / ROCKET START button
        ControlCircleButton(
            label = if (!autoAccelerate) "GAS" else "BOOST\nSTART",
            contentDesc = stringResource(R.string.cd_gas),
            testTag = "btn_action_gas",
            visualSize = gasVisualDp,
            hitSize = gasHitDp,
            fontSizeSp = if (!autoAccelerate) 13f else 10f,
            baseColor = SuccessLime.copy(alpha = 0.80f * clampedOpacity),
            activeColor = Color(0xFFBEF264),
            textColor = NightNavy,
            view = view,
            onPressChanged = { pressed -> inputState.gasPressed = pressed }
        )
    }
}

@Composable
private fun ControlCircleButton(
    label: String,
    contentDesc: String,
    testTag: String,
    visualSize: Dp,
    hitSize: Dp,
    fontSizeSp: Float,
    baseColor: Color,
    activeColor: Color,
    textColor: Color = TextPrimary,
    view: View,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(hitSize)
            .semantics { contentDescription = contentDesc }
            .pointerInput(testTag) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val activePointerId: PointerId = down.id
                    pressed = true
                    triggerButtonHaptic40ms(view)
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(visualSize)
                .scale(if (pressed) 0.94f else 1f)
                .clip(CircleShape)
                .background(if (pressed) activeColor else baseColor)
                .border(2.dp, if (pressed) Color.White else InkBorder, CircleShape)
        ) {
            Text(
                text = label,
                style = DRType.Label.copy(
                    fontWeight = FontWeight.W800,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp + 2f).sp
                ),
                color = if (pressed) NightNavy else textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun triggerButtonHaptic40ms(view: View) {
    try {
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
                return
            }
        }
    } catch (_: Exception) {
    }
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}
