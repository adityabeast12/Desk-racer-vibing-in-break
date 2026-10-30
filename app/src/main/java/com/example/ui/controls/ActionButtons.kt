package com.example.ui.controls

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.InputState
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.GasGreen
import com.example.ui.theme.ItemRed
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SlateAbyss

@Composable
fun ActionButtons(
    inputState: InputState,
    autoAccelerate: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // ITEM Button (Tap to fire forward; swipe down or hold to fire backward / hold shield)
        var itemPressed by remember { mutableStateOf(false) }
        val itemDesc = stringResource(R.string.cd_item_slot)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(66.dp)
                .scale(if (itemPressed) 0.92f else 1f)
                .clip(CircleShape)
                .background(ItemRed.copy(alpha = 0.72f))
                .border(2.5.dp, Color(0x99FFFFFF), CircleShape)
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
                        val fireBack = maxDy > 36f
                        inputState.updateItemHold(false, fireBackward = fireBack)
                        if (fireBack) {
                            inputState.triggerItem(fireBackward = true)
                        }
                    }
                }
                .testTag("btn_action_item")
        ) {
            Text(
                text = "ITEM",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        // BRAKE / REVERSE Button
        ControlCircleButton(
            label = "BRAKE",
            contentDesc = stringResource(R.string.cd_brake),
            testTag = "btn_action_brake",
            size = 60.dp,
            fontSizeSp = 11f,
            baseColor = Color(0xB3334155),
            activeColor = ArcadeCrimson,
            onPressChanged = { pressed -> inputState.brakePressed = pressed }
        )

        // HOP + DRIFT Button
        ControlCircleButton(
            label = "DRIFT",
            contentDesc = stringResource(R.string.cd_drift),
            testTag = "btn_action_drift",
            size = 74.dp,
            fontSizeSp = 14f,
            baseColor = DriftAmber.copy(alpha = 0.74f),
            activeColor = RulerYellow,
            onPressChanged = { pressed -> inputState.updateDrift(pressed) }
        )

        // GAS Button (Always shown during countdown for Rocket Start or when Auto-Accelerate is OFF)
        if (!autoAccelerate) {
            ControlCircleButton(
                label = "GAS",
                contentDesc = stringResource(R.string.cd_gas),
                testTag = "btn_action_gas",
                size = 82.dp,
                fontSizeSp = 16f,
                baseColor = GasGreen.copy(alpha = 0.74f),
                activeColor = Color(0xFF86EFAC),
                onPressChanged = { pressed -> inputState.gasPressed = pressed }
            )
        } else {
            ControlCircleButton(
                label = "BOOST\nSTART",
                contentDesc = stringResource(R.string.cd_gas),
                testTag = "btn_action_gas",
                size = 68.dp,
                fontSizeSp = 11f,
                baseColor = GasGreen.copy(alpha = 0.68f),
                activeColor = Color(0xFF86EFAC),
                onPressChanged = { pressed -> inputState.gasPressed = pressed }
            )
        }
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
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(if (pressed) 0.92f else 1f)
            .clip(CircleShape)
            .background(if (pressed) activeColor else baseColor)
            .border(2.5.dp, if (pressed) Color.White else Color(0x88FFFFFF), CircleShape)
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
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 2f).sp,
            color = if (pressed) SlateAbyss else Color.White,
            textAlign = TextAlign.Center
        )
    }
}
