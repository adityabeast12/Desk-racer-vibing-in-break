package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.Tracks
import com.example.net.LobbySlotInfo
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface

@Composable
fun HostLobbyScreen(
    roomCode: String,
    hostIpAddress: String,
    slots: List<LobbySlotInfo>,
    selectedTrackId: Int,
    selectedLaps: Int,
    cpuFillEnabled: Boolean,
    itemMode: ItemMode,
    canForceStart: Boolean,
    onSelectTrack: (Int) -> Unit,
    onSelectLaps: (Int) -> Unit,
    onToggleCpuFill: () -> Unit,
    onCycleItemMode: () -> Unit,
    onKickSlot: (Int) -> Unit,
    onStartRace: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val allHumansReady = slots.all { it.isReady }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9090D16))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("host_lobby_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT CARD: "Connect your friends"
            Surface(
                modifier = Modifier
                    .weight(0.90f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = SlateAbyss,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .background(SlateSurface, RoundedCornerShape(10.dp))
                                .testTag("btn_host_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "CONNECT YOUR FRIENDS",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SkyCyan
                            )
                            Text(
                                text = "Zero internet required • 2–8 players",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Step 1: Open Hotspot Settings
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SlateSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "STEP 1: TURN ON WI-FI HOTSPOT",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = RulerYellow
                            )
                            Text(
                                text = "Turn ON your hotspot, then friends join it. Mobile data can be OFF.",
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0)
                            )
                            Button(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JoinBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("btn_open_hotspot_settings")
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OPEN HOTSPOT SETTINGS",
                                    fontFamily = OrbitronFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Step 2: Room Code & Detected Host IP:Port
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SlateSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, HostEmerald.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "STEP 2: FRIENDS TAP 'JOIN RACE'",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = HostEmerald
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "ROOM CODE", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = roomCode,
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = RulerYellow,
                                        modifier = Modifier.testTag("host_room_code")
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "HOST ADDRESS (IP:PORT)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "$hostIpAddress:${GameConstants.TCP_CONTROL_PORT}",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = SkyCyan,
                                        modifier = Modifier.testTag("host_ip_address")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // RIGHT CARD: "Race" (8 Player Slots, 8-Track Picker, Laps, CPU Fill, Item Mode, START Button)
            Surface(
                modifier = Modifier
                    .weight(1.10f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = SlateAbyss,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Track Picker (8 Tracks in scrollable row) & Race Options
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Tracks.all.forEach { t ->
                                val selected = t.id == selectedTrackId
                                Surface(
                                    modifier = Modifier
                                        .width(126.dp)
                                        .height(44.dp)
                                        .clickable { onSelectTrack(t.id) }
                                        .testTag("host_track_${t.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) SkyCyan.copy(alpha = 0.25f) else SlateSurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) SkyCyan else SlateBorder
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = t.name,
                                            fontFamily = OrbitronFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = t.difficultyLabel,
                                            fontSize = 8.sp,
                                            color = if (selected) RulerYellow else Color(0xFF94A3B8),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Laps (1/3/5), CPU Fill, Item Mode Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 3, 5).forEach { l ->
                                val active = selectedLaps == l
                                Surface(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clickable { onSelectLaps(l) }
                                        .testTag("host_laps_$l"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) DriftAmber else SlateSurface
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = "$l LAPS",
                                            fontFamily = OrbitronFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (active) SlateAbyss else Color.White
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable(onClick = onToggleCpuFill)
                                    .testTag("host_cpu_fill_toggle"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (cpuFillEnabled) HostEmerald.copy(alpha = 0.28f) else SlateSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (cpuFillEnabled) HostEmerald else SlateBorder
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = if (cpuFillEnabled) "CPU FILL: ON" else "CPU FILL: OFF",
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable(onClick = onCycleItemMode)
                                    .testTag("host_item_mode_toggle"),
                                shape = RoundedCornerShape(8.dp),
                                color = SlateSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SkyCyan)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = "ITEMS: ${itemMode.label.uppercase()}",
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = SkyCyan
                                    )
                                }
                            }
                        }
                    }

                    // 8 Racer Slots (2x4 Compact Grid)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until 2) {
                                    val slotIdx = row * 2 + col
                                    val slotInfo = slots.firstOrNull { it.slotIndex == slotIdx }
                                    LobbySlotBadge(
                                        slotIdx = slotIdx,
                                        info = slotInfo,
                                        cpuFill = cpuFillEnabled,
                                        onKick = { onKickSlot(slotIdx) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Big START RACE Button
                    val startEnabled = allHumansReady || canForceStart
                    Button(
                        onClick = onStartRace,
                        enabled = startEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = HostEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_host_start_race")
                    ) {
                        Text(
                            text = if (allHumansReady) "START RACE" else "FORCE START RACE",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbySlotBadge(
    slotIdx: Int,
    info: LobbySlotInfo?,
    cpuFill: Boolean,
    onKick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorHex = if (info != null) {
        GameConstants.KART_COLORS.getOrElse(info.colorIndex) { GameConstants.KART_COLORS[0] }
    } else {
        0xFF475569L
    }

    Surface(
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(8.dp),
        color = SlateSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (info != null) Color(colorHex).copy(alpha = 0.7f) else Color(0xFF334155)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(colorHex), CircleShape)
                )
                Text(
                    text = info?.playerName ?: if (cpuFill) "CPU BOT ${slotIdx + 1}" else "OPEN SLOT",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (info != null) Color.White else Color(0xFF94A3B8)
                )
            }
            if (info != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${info.pingMs}ms",
                        fontSize = 8.sp,
                        color = Color(0xFF94A3B8)
                    )
                    if (info.isReady) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Ready",
                            tint = HostEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (slotIdx > 0) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kick Player",
                            tint = ArcadeCrimson,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable(onClick = onKick)
                        )
                    }
                }
            }
        }
    }
}
