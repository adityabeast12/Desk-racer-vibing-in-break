package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.Tracks
import com.example.net.DiscoveredRoomBeacon
import com.example.net.LobbySlotInfo
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
fun JoinScreen(
    discoveredRooms: List<DiscoveredRoomBeacon>,
    defaultGatewayIp: String,
    statusMessage: String,
    isConnectedToLobby: Boolean,
    roomCode: String,
    trackId: Int,
    laps: Int,
    slots: List<LobbySlotInfo>,
    isClientReady: Boolean,
    onConnectIp: (String) -> Unit,
    onToggleReady: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var ipInput by remember(defaultGatewayIp) { mutableStateOf(defaultGatewayIp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9090D16))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("join_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Column: Auto-Discovered Rooms List
            Surface(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = SlateAbyss,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .background(SlateSurface, RoundedCornerShape(10.dp))
                                .testTag("btn_join_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "AUTO-DISCOVERED ROOMS (UDP 47777)",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SkyCyan
                            )
                            Text(
                                text = statusMessage,
                                fontSize = 11.sp,
                                color = RulerYellow,
                                modifier = Modifier.testTag("join_status_text")
                            )
                        }
                    }

                    if (discoveredRooms.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SlateSurface, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = SkyCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Scanning local Wi-Fi / Hotspot subnet...",
                                    fontFamily = OrbitronFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Make sure you joined the Host phone's Wi-Fi Hotspot.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredRooms) { room ->
                                val track = Tracks.byId(room.trackId)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onConnectIp(room.hostIp) }
                                        .testTag("discovered_room_${room.roomCode}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SlateSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, HostEmerald)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "ROOM ${room.roomCode} • ${room.hostName}",
                                                fontFamily = OrbitronFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = RulerYellow
                                            )
                                            Text(
                                                text = "${track.name} • ${room.hostIp}",
                                                fontSize = 11.sp,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }
                                        Text(
                                            text = "${room.playerCount}/${room.maxPlayers} • TAP TO JOIN",
                                            fontFamily = OrbitronFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = HostEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Column: Manual Gateway/Host IP Entry OR Connected Client Lobby View
            Surface(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = SlateAbyss,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                if (!isConnectedToLobby) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DIRECT HOST IP CONNECT",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SkyCyan
                            )
                            Text(
                                text = "Pre-filled with your detected Wi-Fi Hotspot gateway IP:",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            BasicTextField(
                                value = ipInput,
                                onValueChange = { ipInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                ),
                                cursorBrush = SolidColor(SkyCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(SlateSurface, RoundedCornerShape(10.dp))
                                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("join_ip_input")
                            )
                        }

                        Button(
                            onClick = { onConnectIp(ipInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = JoinBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_join_connect_ip")
                        ) {
                            Text(
                                text = "JOIN HOST ($ipInput)",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    val track = Tracks.byId(trackId)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "LOBBY ROOM $roomCode",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = RulerYellow
                            )
                            Text(
                                text = "${track.name} • $laps LAPS • Waiting for Host...",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = SkyCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            slots.forEach { s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateSurface, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = s.playerName,
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (s.isReady) "READY ✓" else "NOT READY",
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (s.isReady) HostEmerald else DriftAmber
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onToggleReady,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isClientReady) HostEmerald else DriftAmber
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_client_ready")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isClientReady) "READY! (TAP TO UNREADY)" else "TAP WHEN READY",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = SlateAbyss
                            )
                        }
                    }
                }
            }
        }
    }
}
