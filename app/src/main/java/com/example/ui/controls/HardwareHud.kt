package com.example.ui.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.InputSource
import com.example.game.ItemType
import com.example.ui.hud.HudTextShadow
import com.example.ui.hud.ItemModelMiniGlyph
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType
import kotlinx.coroutines.delay

/**
 * v7.5: HUD for keyboard / mouse / controller play. The touch pedals are hidden, so this shows the
 * held item (with the key that fires it) and a control reminder that fades out after a few seconds
 * and comes back whenever the input device changes.
 */
@Composable
fun HardwareControlsHud(
    source: InputSource,
    heldItem: ItemType,
    isRouletteSpinning: Boolean,
    rouletteDisplayItem: ItemType,
    modifier: Modifier = Modifier
) {
    var showHints by remember(source) { mutableStateOf(true) }
    LaunchedEffect(source) {
        delay(7000L)
        showHints = false
    }
    val pad = source == InputSource.GAMEPAD
    val itemKey = if (pad) "LB" else "E"
    val backKey = if (pad) "Y" else "Q"
    val shown = if (isRouletteSpinning) rouletteDisplayItem else heldItem
    val hasItem = shown != ItemType.NONE

    Column(
        modifier = modifier.padding(end = 18.dp, bottom = 14.dp).testTag("hw_hud"),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(visible = showHints, enter = fadeIn(), exit = fadeOut()) {
            val lines = if (pad) listOf(
                "RT / A  Gas", "LT / B  Brake", "RB  Drift (hop)", "LB / X  Item · hold = shield", "Y  Throw back", "Start  Pause"
            ) else listOf(
                "W S  Gas · Brake", "A D  Steer", "Space  Drift (hop)", "E  Item · hold = shield", "Q  Throw back", "Esc  Pause",
                "Mouse: click to steer with it · RMB drift · LMB item"
            )
            Column(
                modifier = Modifier
                    .clip(DRShape.Card)
                    .background(DR.Bg.copy(alpha = 0.62f))
                    .border(1.dp, DR.HudLine, DRShape.Card)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(if (pad) "CONTROLLER" else "KEYBOARD + MOUSE", style = DRType.LabelS.copy(fontSize = 10.sp), color = DR.Amber)
                for (l in lines) {
                    Text(l, style = DRType.Caption.copy(fontSize = 11.sp, shadow = HudTextShadow), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasItem) {
                Text(
                    "$itemKey use · $backKey back",
                    style = DRType.LabelS.copy(fontSize = 10.sp, shadow = HudTextShadow),
                    color = DR.TextHi
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(DRShape.HudSkew)
                    .background(DR.Bg.copy(alpha = 0.55f))
                    .border(if (hasItem) 2.dp else 1.dp, if (hasItem) DR.Amber else DR.HudLine, DRShape.HudSkew)
            ) {
                if (hasItem) {
                    ItemModelMiniGlyph(item = shown, showItemBoxWhenEmpty = true, modifier = Modifier.size(46.dp))
                } else {
                    Text("ITEM", style = DRType.LabelS.copy(fontSize = 9.sp), color = DR.TextLo)
                }
            }
        }
    }
}
