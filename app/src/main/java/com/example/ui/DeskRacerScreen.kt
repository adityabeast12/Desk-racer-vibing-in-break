package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.game.DeskRacerUiState
import com.example.game.DeskRacerViewModel
import com.example.game.LanRole
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.GasGreen
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.ItemRed
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface

@Composable
fun DeskRacerScreen(
    viewModel: DeskRacerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Hardware-Accelerated OpenGL ES 2.0 3D Desk Arena (or WebGL Sandbox)
        if (state.useWebGlEngine) {
            WebGlDeskRacerView(modifier = Modifier.fillMaxSize())
        } else {
            DeskArena3DCanvas(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Crisp Arcade Racing HUD & Touch Controls Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Top-Left: Sleek Hotspot LAN Bar + Expandable Room Code Modal + Mini-Map
            TopLeftHotspotPanel(
                state = state,
                onRoomInputChanged = viewModel::updateRoomInput,
                onHostClicked = viewModel::hostHotspotSession,
                onJoinClicked = { viewModel.joinHotspotSession() },
                onDiscoveredHostClicked = { hostIp -> viewModel.joinHotspotSession(hostIp) },
                onDisconnectClicked = viewModel::disconnectLanSession,
                onToggleLobby = viewModel::toggleLobbyExpanded,
                onOpenBlueprint = { viewModel.toggleBlueprintSheet(true) },
                onRespawnKart = viewModel::respawnKart,
                onCycleCamera = viewModel::cycleCameraMode,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Top-Right: Compact Mario-Kart Telemetry HUD (Item Slot, Speedometer, Drift Gauge, Lap)
            TopRightTelemetryHud(
                state = state,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // Bottom-Left: Multi-Touch Left & Right Steering + Brake
            BottomLeftSteeringCluster(
                isLeftActive = state.controls.left,
                isRightActive = state.controls.right,
                isBrakeActive = state.controls.brake,
                onLeftChanged = viewModel::setSteeringLeft,
                onRightChanged = viewModel::setSteeringRight,
                onBrakeChanged = viewModel::setBrakePressed,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            // Bottom-Right: Multi-Touch FIRE, DRIFT (Hop), and GAS Pedals
            BottomRightPedalsCluster(
                hasItem = state.localKart.item != null,
                isDriftActive = state.controls.drift,
                isGasActive = state.controls.gas,
                onFireClicked = viewModel::fireHeldItem,
                onDriftChanged = viewModel::setDriftPressed,
                onGasChanged = viewModel::setGasPressed,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // 3. Complete Blueprint Archive & Engine Modal Sheet
        if (state.isBlueprintSheetOpen) {
            BlueprintSheetOverlay(
                useWebGlEngine = state.useWebGlEngine,
                aiRivalEnabled = state.aiRivalEnabled,
                onToggleEngine = viewModel::setEngineMode,
                onToggleAiRival = viewModel::toggleAiRival,
                onDismiss = { viewModel.toggleBlueprintSheet(false) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopLeftHotspotPanel(
    state: DeskRacerUiState,
    onRoomInputChanged: (String) -> Unit,
    onHostClicked: () -> Unit,
    onJoinClicked: () -> Unit,
    onDiscoveredHostClicked: (String) -> Unit,
    onDisconnectClicked: () -> Unit,
    onToggleLobby: () -> Unit,
    onOpenBlueprint: () -> Unit,
    onRespawnKart: () -> Unit,
    onCycleCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(max = 340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Top Action Pill Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .clickable(onClick = onToggleLobby)
                    .testTag("toggle_lobby_button"),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC0F172A),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    when (state.lanRole) {
                        LanRole.HOSTING_HOTSPOT -> HostEmerald
                        LanRole.JOINED_CLIENT -> SkyCyan
                        LanRole.STANDALONE -> Color(0x55FFFFFF)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = stringResource(R.string.cd_open_lobby),
                        tint = when (state.lanRole) {
                            LanRole.HOSTING_HOTSPOT -> HostEmerald
                            LanRole.JOINED_CLIENT -> SkyCyan
                            LanRole.STANDALONE -> DriftAmber
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when (state.lanRole) {
                            LanRole.HOSTING_HOTSPOT -> "HOST: ${state.roomInput}"
                            LanRole.JOINED_CLIENT -> "LAN (${state.remoteKarts.size})"
                            LanRole.STANDALONE -> "LAN LOBBY"
                        },
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }

            IconButton(
                onClick = onCycleCamera,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xCC0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                    .testTag("camera_mode_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.cd_switch_camera),
                    tint = SkyCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRespawnKart,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xCC0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                    .testTag("respawn_kart_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.cd_respawn_kart),
                    tint = DriftAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenBlueprint,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xCC0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                    .testTag("open_blueprint_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = stringResource(R.string.cd_open_blueprint),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Expandable Hotspot Room Code / IP Input Panel
        AnimatedVisibility(visible = state.isLobbyExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lobby_panel"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xE80F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyCyan.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WI-FI HOTSPOT ZERO-INTERNET LAN",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SkyCyan
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = state.roomInput,
                            onValueChange = onRoomInputChanged,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = OrbitronFontFamily
                            ),
                            cursorBrush = SolidColor(SkyCyan),
                            modifier = Modifier
                                .width(120.dp)
                                .height(48.dp)
                                .background(SlateSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 14.dp)
                                .testTag("room_input")
                        )

                        Button(
                            onClick = onHostClicked,
                            colors = ButtonDefaults.buttonColors(containerColor = HostEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("host_button")
                        ) {
                            Text("HOST", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        }

                        Button(
                            onClick = onJoinClicked,
                            colors = ButtonDefaults.buttonColors(containerColor = JoinBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("join_button")
                        ) {
                            Text("JOIN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        }

                        if (state.lanRole != LanRole.STANDALONE) {
                            Button(
                                onClick = onDisconnectClicked,
                                colors = ButtonDefaults.buttonColors(containerColor = ArcadeCrimson),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("disconnect_button")
                            ) {
                                Text("STOP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    Text(
                        text = state.lanStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.testTag("lan_status_text")
                    )

                    if (state.discoveredHosts.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "UDP 8888:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriftAmber
                            )
                            state.discoveredHosts.take(2).forEach { host ->
                                Surface(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .clickable { onDiscoveredHostClicked(host.ipAddress) }
                                        .testTag("discovered_host_${host.roomCode}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = SlateSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, HostEmerald)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = "${host.roomCode} (${host.ipAddress})",
                                            color = HostEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Radar Mini-Map when Lobby is collapsed
        if (!state.isLobbyExpanded && !state.useWebGlEngine) {
            DeskTrackMiniMap(state = state)
        }
    }
}

@Composable
private fun TopRightTelemetryHud(
    state: DeskRacerUiState,
    modifier: Modifier = Modifier
) {
    val item = state.localKart.item
    val itemLabel = item?.displayName ?: "NO ITEM"
    val driftProgress = (state.localKart.driftCharge / 36f).coerceIn(0f, 1f)
    val isUltra = state.localKart.driftCharge > 35

    Surface(
        modifier = modifier
            .widthIn(max = 185.dp)
            .testTag("hud_panel"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC0F172A),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (isUltra) RulerYellow else Color(0x38FFFFFF)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Item Roulette Badge
            Box(
                modifier = Modifier
                    .background(
                        color = item?.badgeColor?.copy(alpha = 0.25f) ?: Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = item?.badgeColor ?: Color(0xFF475569),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (item != null) "★ $itemLabel" else "ITEM: NONE",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = item?.badgeColor ?: Color(0xFF94A3B8),
                    modifier = Modifier.testTag("item_display")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Speedometer & Drift Status
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${state.speedKmh}",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = if (isUltra) RulerYellow else Color.White
                )
                Text(
                    text = "KM/H",
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = SkyCyan,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Text(
                text = state.speedDisplayStatus,
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = if (isUltra) RulerYellow else SkyCyan,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.testTag("speed_display")
            )

            if (state.localKart.driftCharge > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { driftProgress },
                    modifier = Modifier
                        .width(120.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isUltra) ArcadeCrimson else RulerYellow,
                    trackColor = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "LAP ${state.localKart.lap} • ${state.cameraMode.label}",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.End,
                modifier = Modifier.testTag("lap_display")
            )
        }
    }
}

@Composable
private fun BottomLeftSteeringCluster(
    isLeftActive: Boolean,
    isRightActive: Boolean,
    isBrakeActive: Boolean,
    onLeftChanged: (Boolean) -> Unit,
    onRightChanged: (Boolean) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        ArcadeTouchButton(
            label = stringResource(R.string.ctrl_left),
            contentDesc = stringResource(R.string.cd_steer_left),
            testTag = "btn_left",
            size = 68.dp,
            fontSizeSp = 22f,
            baseColor = Color(0xB81E293B),
            activeColor = SkyCyan,
            isPressedExternal = isLeftActive,
            onPressChanged = onLeftChanged
        )

        ArcadeTouchButton(
            label = stringResource(R.string.ctrl_right),
            contentDesc = stringResource(R.string.cd_steer_right),
            testTag = "btn_right",
            size = 68.dp,
            fontSizeSp = 22f,
            baseColor = Color(0xB81E293B),
            activeColor = SkyCyan,
            isPressedExternal = isRightActive,
            onPressChanged = onRightChanged
        )

        ArcadeTouchButton(
            label = "REV",
            contentDesc = stringResource(R.string.cd_brake_reverse),
            testTag = "btn_brake",
            size = 54.dp,
            fontSizeSp = 11f,
            baseColor = Color(0xA6334155),
            activeColor = ArcadeCrimson,
            isPressedExternal = isBrakeActive,
            onPressChanged = onBrakeChanged
        )
    }
}

@Composable
private fun BottomRightPedalsCluster(
    hasItem: Boolean,
    isDriftActive: Boolean,
    isGasActive: Boolean,
    onFireClicked: () -> Unit,
    onDriftChanged: (Boolean) -> Unit,
    onGasChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        ArcadeTouchButton(
            label = stringResource(R.string.ctrl_fire),
            contentDesc = stringResource(R.string.cd_fire_item),
            testTag = "btn_item",
            size = 62.dp,
            fontSizeSp = 13f,
            baseColor = if (hasItem) ItemRed.copy(alpha = 0.92f) else ItemRed.copy(alpha = 0.42f),
            activeColor = Color(0xFFFCA5A5),
            isPressedExternal = false,
            onPressChanged = { pressed ->
                if (pressed) onFireClicked()
            }
        )

        ArcadeTouchButton(
            label = stringResource(R.string.ctrl_drift),
            contentDesc = stringResource(R.string.cd_drift_hop),
            testTag = "btn_drift",
            size = 64.dp,
            fontSizeSp = 13f,
            baseColor = DriftAmber.copy(alpha = 0.85f),
            activeColor = RulerYellow,
            isPressedExternal = isDriftActive,
            onPressChanged = onDriftChanged
        )

        ArcadeTouchButton(
            label = stringResource(R.string.ctrl_gas),
            contentDesc = stringResource(R.string.cd_gas_pedal),
            testTag = "btn_gas",
            size = 78.dp,
            fontSizeSp = 16f,
            baseColor = GasGreen.copy(alpha = 0.88f),
            activeColor = Color(0xFF86EFAC),
            isPressedExternal = isGasActive,
            onPressChanged = onGasChanged
        )
    }
}

@Composable
private fun ArcadeTouchButton(
    label: String,
    contentDesc: String,
    testTag: String,
    size: Dp,
    fontSizeSp: Float,
    baseColor: Color,
    activeColor: Color,
    isPressedExternal: Boolean,
    onPressChanged: (Boolean) -> Unit
) {
    var isPressedLocal by remember { mutableStateOf(false) }
    val active = isPressedLocal || isPressedExternal

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(if (active) 0.92f else 1f)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (active) {
                        listOf(Color.White, activeColor)
                    } else {
                        listOf(baseColor, baseColor.copy(alpha = 0.72f))
                    }
                ),
                shape = CircleShape
            )
            .border(
                width = 2.5.dp,
                color = if (active) Color.White else Color(0x75FFFFFF),
                shape = CircleShape
            )
            .semantics { contentDescription = contentDesc }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressedLocal = true
                    onPressChanged(true)
                    waitForUpOrCancellation()
                    isPressedLocal = false
                    onPressChanged(false)
                }
            }
            .clickable {
                onPressChanged(!isPressedExternal)
            }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp,
            color = if (active) SlateAbyss else Color.White,
            textAlign = TextAlign.Center
        )
    }
}
