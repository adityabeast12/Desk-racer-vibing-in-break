package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NetworkWifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.ItemMode
import com.example.game.Tracks
import com.example.net.DiscoveredRoomBeacon
import com.example.net.LobbySlotInfo
import com.example.ui.components.DRBadge
import com.example.ui.components.DRBadgeKind
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRLabel
import com.example.ui.components.DRPanel
import com.example.ui.components.DRScreenHeader
import com.example.ui.components.DRStatusDot
import com.example.ui.components.DRTextField
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType
import androidx.compose.foundation.border

@Composable
fun JoinScreen(
    discoveredRooms: List<DiscoveredRoomBeacon>,
    defaultGatewayIp: String,
    statusMessage: String,
    isConnectedToLobby: Boolean,
    roomCode: String,
    trackId: Int,
    laps: Int,
    itemMode: ItemMode = ItemMode.NORMAL,
    ccClass: CcClass = CcClass.CC_150,
    cpuFill: Boolean = true,
    botCount: Int = 7,
    botDifficulty: CpuDifficulty = CpuDifficulty.PRO,
    slots: List<LobbySlotInfo>,
    isClientReady: Boolean,
    onConnectIp: (String) -> Unit,
    onToggleReady: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var ipInput by remember(defaultGatewayIp) { mutableStateOf(defaultGatewayIp) }
    var selectedRoomIp by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Bg.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("join_screen")
    ) {
        Column(Modifier.fillMaxSize()) {
            DRScreenHeader(
                kicker = "Join LAN",
                title = if (isConnectedToLobby) "Room $roomCode" else "Find a room",
                onBack = onBack,
                backTestTag = "btn_join_back"
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!isConnectedToLobby) {
                        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DRStatusDot(DR.Ok)
                            DRLabel("Scanning this Wi-Fi")
                            Spacer(Modifier.weight(1f))
                            Text(statusMessage, style = DRType.Caption.copy(fontSize = 11.sp), color = DR.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f))
                        }
                        Spacer(Modifier.height(6.dp))
                        if (discoveredRooms.isEmpty()) {
                            DRPanel(Modifier.fillMaxWidth().weight(1f)) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Rounded.WifiFind, contentDescription = null, tint = DR.TextLo, modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally))
                                Spacer(Modifier.height(8.dp))
                                Text("No rooms yet", style = DRType.Title.copy(fontSize = 18.sp), color = DR.TextHi, modifier = Modifier.align(Alignment.CenterHorizontally))
                                Text(
                                    "Join the host phone's hotspot, then ask them to open the lobby. Rooms appear here automatically.",
                                    style = DRType.Caption,
                                    color = DR.TextMid,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 24.dp, vertical = 4.dp)
                                )
                                Spacer(Modifier.weight(1f))
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(discoveredRooms) { room ->
                                    val sel = selectedRoomIp == room.hostIp
                                    val full = room.playerCount >= room.maxPlayers
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .clip(DRShape.Card)
                                            .background(if (sel) DR.AmberTint else DR.S2)
                                            .border(if (sel) 1.5.dp else 1.dp, if (sel) DR.Amber else DR.Line, DRShape.Card)
                                            .clickable(enabled = !full && !room.raceInProgress, role = Role.Button) {
                                                selectedRoomIp = room.hostIp
                                                onConnectIp(room.hostIp)
                                            }
                                            .padding(horizontal = 14.dp)
                                            .testTag("discovered_room_${room.roomCode}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.NetworkWifi, contentDescription = null, tint = if (room.pingMs < 80) DR.Ok else DR.Amber, modifier = Modifier.size(22.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("${room.hostName} · ${room.roomCode}", style = DRType.BodyStrong.copy(fontSize = 14.sp), color = if (full) DR.TextLo else DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(Tracks.byId(room.trackId).name, style = DRType.Caption, color = DR.TextMid, maxLines = 1)
                                        }
                                        Text("${room.playerCount}/${room.maxPlayers}", style = DRType.NumL, color = if (full) DR.TextLo else DR.TextHi)
                                        when {
                                            room.raceInProgress -> DRBadge("Racing", DRBadgeKind.MUTED)
                                            full -> DRBadge("Full", DRBadgeKind.MUTED)
                                            else -> DRBadge("Lobby", DRBadgeKind.OK)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        DRPanel(Modifier.fillMaxWidth().weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                DRLabel("Players ${slots.count { it.isHuman }}/$LOBBY_MAX_SLOTS")
                                DRLabel("${slots.count { it.isReady || it.slotIndex == 0 }} ready", color = DR.Ok)
                            }
                            for (row in 0 until LOBBY_MAX_SLOTS / 2) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (col in 0 until 2) {
                                        val idx = row * 2 + col
                                        LobbySlotRow(
                                            slotIdx = idx,
                                            info = slots.firstOrNull { it.slotIndex == idx },
                                            cpuFill = cpuFill,
                                            onKick = null,
                                            localSlot = -1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    if (!isConnectedToLobby) {
                        DRPanel(Modifier.fillMaxWidth().weight(1f)) {
                            DRLabel("Join by address")
                            Spacer(Modifier.height(8.dp))
                            DRTextField(
                                value = ipInput,
                                onValueChange = { ipInput = it.trim() },
                                leadingIcon = Icons.Rounded.Lan,
                                placeholder = "192.168.43.1",
                                testTag = "join_ip_input",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Pre-filled with your hotspot's gateway. Only needed if the room doesn't show up on the left.",
                                style = DRType.Caption,
                                color = DR.TextMid
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        DRButton(
                            text = "Join room",
                            icon = Icons.AutoMirrored.Rounded.Login,
                            size = DRButtonSize.L,
                            enabled = ipInput.isNotBlank(),
                            onClick = { onConnectIp(selectedRoomIp ?: ipInput) },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "btn_join_connect_ip"
                        )
                    } else {
                        val track = Tracks.byId(trackId)
                        DRPanel(Modifier.fillMaxWidth().weight(1f)) {
                            DRLabel("Race")
                            Text(track.name.uppercase(), style = DRType.Title, color = DR.TextHi, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "$laps laps · items ${itemMode.label.lowercase()} · ${ccClass.label}" +
                                    if (cpuFill) " · $botCount ${botDifficulty.label.lowercase()} bots" else "",
                                style = DRType.Caption,
                                color = DR.TextMid
                            )
                            Spacer(Modifier.weight(1f))
                            Text(statusMessage, style = DRType.Caption, color = DR.TextLo, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(10.dp))
                        DRButton(
                            text = if (isClientReady) "Ready ✓" else "I'm ready",
                            icon = if (isClientReady) null else Icons.Rounded.Check,
                            size = DRButtonSize.L,
                            onClick = onToggleReady,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "btn_client_ready"
                        )
                        if (isClientReady) {
                            Text("Tap again to un-ready. The host starts the race.", style = DRType.Caption.copy(fontSize = 10.5.sp), color = DR.TextLo, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
