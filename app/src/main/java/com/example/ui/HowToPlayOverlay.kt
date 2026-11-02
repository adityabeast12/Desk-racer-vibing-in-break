package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ControlSchemeType
import com.example.game.ItemType
import com.example.ui.hud.ItemModelMiniGlyph
import com.example.ui.hud.shortLabel
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.Poppins
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface

/**
 * Part D5: Tutorial / How-to-Play overlay displaying the 3 control scheme cards AND the
 * 6 official v4 PNG item icons (`textures/ui/items/<item>.png`).
 */
@Composable
fun HowToPlayOverlay(
    selectedScheme: ControlSchemeType = ControlSchemeType.SMART,
    onSelectScheme: (ControlSchemeType) -> Unit = {},
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60B1020))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(20.dp)
            .testTag("how_to_play_overlay")
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            color = SlateAbyss,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SkyCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CHOOSE YOUR DRIVING STYLE, ITEMS & HOTSPOT SETUP",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = SkyCyan
                        )
                        Text(
                            text = "Tap any control card below to activate it • 100% Offline Wi-Fi Hotspot",
                            fontFamily = Poppins,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .background(SlateSurface, RoundedCornerShape(12.dp))
                            .testTag("btn_close_how_to")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // 3 Interactive Control Cards (Smart / Pro / Tilt)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StepCard(
                        stepNum = "1",
                        title = "SMART (DEFAULT)",
                        desc = "Auto-gas + floating thumb steer (64dp full lock). Hold DRIFT around corners to charge Blue & Orange Mini-Turbos!",
                        accent = RulerYellow,
                        selected = selectedScheme == ControlSchemeType.SMART,
                        onClick = { onSelectScheme(ControlSchemeType.SMART) },
                        modifier = Modifier.weight(1f)
                    )
                    StepCard(
                        stepNum = "2",
                        title = "PRO MANUAL",
                        desc = "Manual GAS + BRAKE/DRIFT pedals + Purple Ultra Mini-Turbos. Full off-road shortcut freedom.",
                        accent = HostEmerald,
                        selected = selectedScheme == ControlSchemeType.PRO,
                        onClick = { onSelectScheme(ControlSchemeType.PRO) },
                        modifier = Modifier.weight(1f)
                    )
                    StepCard(
                        stepNum = "3",
                        title = "TILT & HOTSPOT LAN",
                        desc = "Steer by tilting your phone (±22°)! For 2–8 player LAN: one friend turns ON Wi-Fi Hotspot, others tap JOIN RACE.",
                        accent = DriftAmber,
                        selected = selectedScheme == ControlSchemeType.TILT,
                        onClick = { onSelectScheme(ControlSchemeType.TILT) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Part D5: Desk Item Guide Row using PNG item icons (`textures/ui/items/<item>.png`)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tutorialItems = listOf(
                            ItemType.MARBLE to "Ricochet Shot",
                            ItemType.PAPER_PLANE to "Homing Strike",
                            ItemType.BATTERY to "1.5s Turbo",
                            ItemType.ERASER to "Spin Hazard",
                            ItemType.TRIPLE_TACKS to "3x Orbit Shield",
                            ItemType.MAGNET to "Pull Leader"
                        )
                        tutorialItems.forEach { (item, desc) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ItemModelMiniGlyph(
                                    item = item,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = item.shortLabel,
                                        fontFamily = Poppins,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = desc,
                                        fontFamily = Poppins,
                                        fontSize = 8.sp,
                                        color = RulerYellow,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNum: String,
    title: String,
    desc: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else SlateSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else accent.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .background(accent, CircleShape)
                ) {
                    Text(
                        text = stepNum,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SlateAbyss
                    )
                }
                Text(
                    text = if (selected) "$title ✓" else title,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
            Text(
                text = desc,
                fontFamily = Poppins,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}
