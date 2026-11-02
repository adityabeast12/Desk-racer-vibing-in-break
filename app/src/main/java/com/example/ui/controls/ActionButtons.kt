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
import com.example.game.ItemType
import com.example.ui.hud.HudTextShadow
import com.example.ui.hud.ItemModelMiniGlyph
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    heldItem: ItemType = ItemType.NONE,
    isRouletteSpinning: Boolean = false,
    rouletteDisplayItem: ItemType = ItemType.NONE,
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

    // v6 visual sizes at 100%: ITEM 72, BRAKE 64x52, DRIFT 96x88, GAS 76x128 (parallelograms/chamfers)
    val plateAlpha = (0.30f + 0.45f * clampedOpacity).coerceIn(0.3f, 0.8f)
    val itemVisualDp = 72.dp * clampedScale
    val itemHitDp = itemVisualDp + 10.dp

    val brakeVisualDp = 56.dp * clampedScale
    val brakeHitDp = brakeVisualDp + 8.dp

    val driftVisualDp = 92.dp * clampedScale
    val driftHitDp = driftVisualDp + 10.dp

    val gasVisualDp = 76.dp * clampedScale
    val gasHitDp = gasVisualDp + 10.dp
    val shownItem = if (isRouletteSpinning) rouletteDisplayItem else heldItem
    val hasItem = shownItem != ItemType.NONE || isRouletteSpinning

    Row(
        modifier = modifier.padding(end = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                    .clip(DRShape.HudSkew)
                    .background(DR.Bg.copy(alpha = plateAlpha))
                    .border(if (hasItem) 2.dp else 1.dp, if (hasItem) DR.Amber else DR.HudLine, DRShape.HudSkew)
            ) {
                if (hasItem) {
                    ItemModelMiniGlyph(item = shownItem, showItemBoxWhenEmpty = true, modifier = Modifier.size(itemVisualDp * 0.72f))
                } else {
                    Text("EMPTY", style = DRType.LabelS.copy(fontSize = 9.sp), color = DR.TextLo, maxLines = 1)
                }
                Text(
                    text = if (isRouletteSpinning) "SPIN" else "ITEM",
                    style = DRType.LabelS.copy(fontSize = 8.5.sp, shadow = HudTextShadow),
                    color = if (hasItem) DR.Amber else DR.TextLo,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 3.dp)
                )
            }
        }

        ControlPadButton(
            label = "BRAKE",
            contentDesc = stringResource(R.string.cd_brake),
            testTag = "btn_action_brake",
            width = brakeVisualDp * 1.15f,
            height = brakeVisualDp * 0.9f,
            hitPad = 4.dp,
            plateAlpha = plateAlpha,
            activeColor = DR.Bad,
            view = view,
            onPressChanged = { pressed -> inputState.brakePressed = pressed }
        )
      }

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
                        .size(width = driftVisualDp, height = driftVisualDp * 0.92f)
                        .scale(driftScale)
                        .clip(DRShape.HudSkew)
                        .background(DR.Bg.copy(alpha = plateAlpha))
                        .border(
                            2.dp,
                            if (sparkTier >= 1) DR.Boost else if (driftPressed) DR.TextHi else DR.HudLine,
                            DRShape.HudSkew
                        )
                ) {
                    // charge fills from the bottom in boost cyan
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(driftChargeRatio.coerceIn(0f, 1f))
                            .background(DR.Boost.copy(alpha = if (sparkTier >= 1) 0.55f else 0.32f))
                    )
                    Text(
                        text = if (sparkTier >= 1) "RELEASE" else "DRIFT",
                        style = DRType.Title.copy(fontSize = if (sparkTier >= 1) 15.sp else 18.sp, shadow = HudTextShadow),
                        color = DR.TextHi,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

        // Right: tall GAS pedal (ROCKET START when auto-accelerate is on)
        ControlPadButton(
            label = if (!autoAccelerate) "GAS" else "ROCKET\nSTART",
            contentDesc = stringResource(R.string.cd_gas),
            testTag = "btn_action_gas",
            width = gasVisualDp,
            height = gasVisualDp * 1.62f,
            hitPad = 5.dp,
            plateAlpha = plateAlpha,
            activeColor = DR.Amber,
            idleTint = DR.Amber.copy(alpha = 0.22f + 0.18f * clampedOpacity),
            borderIdle = DR.AmberHi.copy(alpha = 0.8f),
            skew = true,
            view = view,
            onPressChanged = { pressed -> inputState.gasPressed = pressed }
        )
    }
}

@Composable
private fun ControlPadButton(
    label: String,
    contentDesc: String,
    testTag: String,
    width: Dp,
    height: Dp,
    hitPad: Dp,
    plateAlpha: Float,
    activeColor: Color,
    view: View,
    idleTint: Color? = null,
    borderIdle: Color = DR.HudLine,
    skew: Boolean = false,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = if (skew) DRShape.HudSkew else DRShape.HudPad
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = width + hitPad * 2, height = height + hitPad * 2)
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
                .size(width = width, height = height)
                .scale(if (pressed) 0.95f else 1f)
                .clip(shape)
                .background(if (pressed) activeColor.copy(alpha = 0.85f) else (idleTint ?: DR.Bg.copy(alpha = plateAlpha)))
                .border(if (pressed) 2.dp else 1.5.dp, if (pressed) Color.White else borderIdle, shape)
        ) {
            Text(
                text = label,
                style = DRType.Label.copy(fontSize = 12.sp, lineHeight = 14.sp, shadow = HudTextShadow),
                color = if (pressed) DR.OnAmber else DR.TextHi,
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
