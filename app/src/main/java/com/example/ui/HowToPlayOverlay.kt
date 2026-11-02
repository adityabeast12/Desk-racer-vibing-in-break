package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ControlSchemeType
import com.example.game.ItemType
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRLabel
import com.example.ui.components.DRPanel
import com.example.ui.components.DRTabs
import com.example.ui.hud.ItemModelMiniGlyph
import com.example.ui.hud.shortLabel
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType

/** v6 How To Play: three indexed pages (Steer & drift · Items · LAN hotspot). */
@Composable
fun HowToPlayOverlay(
    selectedScheme: ControlSchemeType = ControlSchemeType.SMART,
    onSelectScheme: (ControlSchemeType) -> Unit = {},
    onDismiss: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 28.dp, vertical = 16.dp)
            .testTag("how_to_play_overlay")
    ) {
        DRPanel(Modifier.fillMaxSize(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DRLabel("How to play", color = DR.Amber)
                    Text(
                        listOf("STEER & DRIFT", "ITEMS", "PLAY WITH FRIENDS")[page],
                        style = DRType.Display.copy(fontSize = 30.sp, lineHeight = 30.sp),
                        color = DR.TextHi
                    )
                }
                DRButton("Skip", onClick = onDismiss, variant = DRButtonVariant.GHOST, testTag = "btn_how_to_skip")
            }
            DRTabs(listOf("Drive", "Items", "LAN"), page, { page = it }, Modifier.fillMaxWidth(), testTagPrefix = "how_to_tab")
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    0 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SchemeCard("Smart", "Auto-gas. Drag anywhere on the left to steer. Hold DRIFT in corners, release for a boost.", selectedScheme == ControlSchemeType.SMART, Modifier.weight(1f)) { onSelectScheme(ControlSchemeType.SMART) }
                        SchemeCard("Pro", "Manual GAS and BRAKE, bigger drift boosts. For players who want full control.", selectedScheme == ControlSchemeType.PRO, Modifier.weight(1f)) { onSelectScheme(ControlSchemeType.PRO) }
                        SchemeCard("Tilt", "Tilt the phone to steer. Brake sits on the left.", selectedScheme == ControlSchemeType.TILT, Modifier.weight(1f)) { onSelectScheme(ControlSchemeType.TILT) }
                        SchemeCard("Buttons", "Big left / right buttons with a smooth ramp.", selectedScheme == ControlSchemeType.BUTTONS, Modifier.weight(1f)) { onSelectScheme(ControlSchemeType.BUTTONS) }
                    }
                    1 -> {
                        val items = listOf(
                            ItemType.MARBLE to "Bounces off walls",
                            ItemType.PAPER_PLANE to "Homes in on the kart ahead",
                            ItemType.BATTERY to "Turbo boost",
                            ItemType.ERASER to "Drop it behind you",
                            ItemType.TRIPLE_TACKS to "Orbiting shield",
                            ItemType.MAGNET to "Pulls you toward the leader"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Drive through item boxes. Tap ITEM to fire forward, swipe down on it to fire back, hold it to keep a shield.", style = DRType.Body, color = DR.TextMid)
                            items.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (item, desc) ->
                                        Row(
                                            Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                                .clip(DRShape.Card)
                                                .background(DR.S2)
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ItemModelMiniGlyph(item = item, modifier = Modifier.size(38.dp))
                                            Column {
                                                Text(item.shortLabel, style = DRType.Label, color = DR.TextHi)
                                                Text(desc, style = DRType.Caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = DR.TextMid, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StepCard(1, Icons.Rounded.WifiTethering, "Host turns on hotspot", "Mobile data can stay off. No internet needed.", Modifier.weight(1f))
                        StepCard(2, Icons.Rounded.SportsEsports, "Friends join it", "Connect to the host's hotspot in Wi-Fi settings.", Modifier.weight(1f))
                        StepCard(3, Icons.Rounded.Inventory2, "Host → Join", "Host taps Host LAN race. Friends tap Join and pick the room.", Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < 2) {
                    DRButton("Next", onClick = { page++ }, size = DRButtonSize.M, testTag = "btn_how_to_next")
                } else {
                    DRButton("Got it", icon = Icons.Rounded.Check, onClick = onDismiss, size = DRButtonSize.M, testTag = "btn_how_to_done")
                }
            }
        }
    }
}

@Composable
private fun SchemeCard(title: String, desc: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(DRShape.Card)
            .background(if (selected) DR.AmberTint else DR.S2)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) DR.Amber else DR.Line, DRShape.Card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp)
            .testTag("how_to_scheme_$title")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = DRType.Title.copy(fontSize = 18.sp), color = DR.TextHi)
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = DR.Amber, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(desc, style = DRType.Caption, color = DR.TextMid)
    }
}

@Composable
private fun StepCard(n: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(DRShape.Card)
            .background(DR.S2)
            .padding(12.dp)
    ) {
        Text(String.format(java.util.Locale.US, "%02d", n), style = DRType.NumS, color = DR.Amber)
        Icon(icon, contentDescription = null, tint = DR.TextHi, modifier = Modifier.padding(vertical = 6.dp).size(24.dp))
        Text(title, style = DRType.Title.copy(fontSize = 16.sp), color = DR.TextHi)
        Spacer(Modifier.height(4.dp))
        Text(desc, style = DRType.Caption, color = DR.TextMid)
    }
}

@Suppress("unused")
private val unusedWidth = 0.dp.let { Modifier.width(it) }
