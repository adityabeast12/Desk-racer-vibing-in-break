package com.example.ui.controls

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
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
 * Part E3, F1, F2, F5: Action buttons cluster inside BOTTOM_RIGHT (60-100% w x 52-100% h).
 * Every action button is >= 72 dp (Brake 72 dp, Item 74 dp, Drift 84 dp, Gas 78 dp) with >= 10 dp
 * spacing between tappables so no two interactive bounds ever overlap.
 * Includes a visual drift-tier ring around DRIFT that fills with the tier colour, haptic ticks
 * at each tier transition, and a pulse when a drift boost is ready to release.
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
    var lastHapticTier by remember { mutableIntStateOf(0) }

    // Part F5: Haptic tick at each drift tier transition
    LaunchedEffect(sparkTier) {
        if (sparkTier > lastHapticTier && sparkTier in 1..3) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastHapticTier = sparkTier
    }

    val infiniteTransition = rememberInfiniteTransition(label = "drift_pulse")
    val readyPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(260),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_ready_pulse"
    )

    val clampedScale = buttonScale.coerceIn(0.88f, 1.08f)
    val clampedOpacity = opacity.coerceIn(0.30f, 1.00f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Left column in cluster: ITEM button (>= 72 dp)
        var itemPressed by remember { mutableStateOf(false) }
        val itemDesc = stringResource(R.string.cd_item_slot)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((74.dp * clampedScale).coerceAtLeast(72.dp))
                .scale(if (itemPressed) 0.94f else 1f)
                .clip(CircleShape)
                .background(HotPink.copy(alpha = 0.80f * clampedOpacity))
                .border(2.5.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                .semantics { contentDescription = itemDesc }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        itemPressed = true
                        var maxDy = 0f
                        inputState.updateItemHold(true, fireBackward = false)
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull()
                            if (ch != null && ch.pressed) {
                                val dy = ch.position.y - down.position.y
                                if (dy > maxDy) maxDy = dy
                            }
                        } while (ev.changes.any { it.pressed })
                        itemPressed = false
                        val fireBack = maxDy > 32f
                        inputState.updateItemHold(false, fireBackward = fireBack)
                        if (fireBack) {
                            inputState.triggerItem(fireBackward = true)
                        }
                    }
                }
                .testTag("btn_action_item")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ITEM",
                    style = DRType.Label.copy(fontWeight = FontWeight.W800, fontSize = 13.sp),
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "▼ BACK",
                    style = DRType.Caption.copy(fontSize = 8.sp, fontWeight = FontWeight.W700),
                    color = TextPrimary.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }

        // Middle column: BRAKE above DRIFT (Part F1: "BRAKE is a button above DRIFT", >= 72 dp each, >= 8 dp gap)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ControlCircleButton(
                label = "BRAKE",
                contentDesc = stringResource(R.string.cd_brake),
                testTag = "btn_action_brake",
                size = (72.dp * clampedScale).coerceAtLeast(72.dp),
                fontSizeSp = 12f,
                baseColor = InkSurface.copy(alpha = 0.86f * clampedOpacity),
                activeColor = DangerCoral,
                onPressChanged = { pressed -> inputState.brakePressed = pressed }
            )

            // DRIFT button with tier ring & pulse when boost is ready
            var driftPressed by remember { mutableStateOf(false) }
            val driftDesc = stringResource(R.string.cd_drift)
            val tierRingColor = when (sparkTier) {
                3 -> Color(0xFFA855F7)
                2 -> LampAmber
                1 -> BoostCyan
                else -> LampAmber.copy(alpha = 0.65f)
            }
            val driftScale = when {
                driftPressed && sparkTier >= 1 -> readyPulse
                driftPressed -> 0.95f
                else -> 1f
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size((82.dp * clampedScale).coerceAtLeast(74.dp))
                    .scale(driftScale)
                    .clip(CircleShape)
                    .background(
                        if (driftPressed) LampAmber.copy(alpha = 0.92f)
                        else LampAmber.copy(alpha = 0.78f * clampedOpacity)
                    )
                    .border(2.5.dp, if (sparkTier >= 1) tierRingColor else Color.White.copy(alpha = 0.75f), CircleShape)
                    .semantics { contentDescription = driftDesc }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            driftPressed = true
                            inputState.updateDrift(true)
                            waitForUpOrCancellation()
                            driftPressed = false
                            inputState.updateDrift(false)
                        }
                    }
                    .testTag("btn_action_drift")
            ) {
                if (driftChargeRatio > 0.02f || sparkTier > 0) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val strokeW = 5.dp.toPx()
                        val pad = strokeW * 0.7f
                        drawArc(
                            color = tierRingColor,
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

        // Right column: GAS / ROCKET START button (>= 72 dp)
        ControlCircleButton(
            label = if (!autoAccelerate) "GAS" else "BOOST\nSTART",
            contentDesc = stringResource(R.string.cd_gas),
            testTag = "btn_action_gas",
            size = (76.dp * clampedScale).coerceAtLeast(72.dp),
            fontSizeSp = if (!autoAccelerate) 14f else 11f,
            baseColor = SuccessLime.copy(alpha = 0.80f * clampedOpacity),
            activeColor = Color(0xFFBEF264),
            textColor = NightNavy,
            onPressChanged = { pressed -> inputState.gasPressed = pressed }
        )
    }
}

@Composable
private fun ControlCircleButton(
    label: String,
    contentDesc: String,
    testTag: String,
    size: Dp,
    fontSizeSp: Float,
    baseColor: Color,
    activeColor: Color,
    textColor: Color = TextPrimary,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(if (pressed) 0.94f else 1f)
            .clip(CircleShape)
            .background(if (pressed) activeColor else baseColor)
            .border(2.5.dp, if (pressed) Color.White else InkBorder, CircleShape)
            .semantics { contentDescription = contentDesc }
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
