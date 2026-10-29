package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Gamepad
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.game.ControlScheme
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
import kotlin.math.abs

@Composable
fun DeskRacerScreen(
    viewModel: DeskRacerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Full-Bleed Portrait 3D OpenGL ES 2.0 Desk Arena (or Three.js WebGL Sandbox)
        if (state.useWebGlEngine) {
            WebGlDeskRacerView(modifier = Modifier.fillMaxSize())
        } else {
            DeskArena3DCanvas(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Portrait Ergonomic Racing Cockpit Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP ZONE: Portrait Arcade Racing Header + Tappable Center Item Slot + Expandable LAN Drawer
            PortraitTopRacingHud(
                state = state,
                onFireItem = viewModel::fireHeldItem,
                onRoomInputChanged = viewModel::updateRoomInput,
                onHostClicked = viewModel::hostHotspotSession,
                onJoinClicked = { viewModel.joinHotspotSession() },
                onDiscoveredHostClicked = { hostIp -> viewModel.joinHotspotSession(hostIp) },
                onDisconnectClicked = viewModel::disconnectLanSession,
                onToggleLobby = viewModel::toggleLobbyExpanded,
                onOpenBlueprint = { viewModel.toggleBlueprintSheet(true) },
                onRespawnKart = viewModel::respawnKart,
                onCycleCamera = viewModel::cycleCameraMode,
                onToggleControlScheme = viewModel::toggleControlScheme
            )

            // BOTTOM ZONE: Portrait Thumb Cockpit (Digital Tachometer + One-Thumb Drift Wheel or Classic Pedals)
            PortraitBottomThumbCockpit(
                state = state,
                onAnalogSteer = viewModel::setAnalogSteeringWheel,
                onFireClicked = viewModel::fireHeldItem,
                onBrakeChanged = viewModel::setBrakePressed,
                onToggleAutoGas = viewModel::toggleAutoGas,
                onLeftChanged = viewModel::setSteeringLeft,
                onRightChanged = viewModel::setSteeringRight,
                onDriftChanged = viewModel::setDriftPressed,
                onGasChanged = viewModel::setGasPressed
            )
        }

        // 3. Complete Blueprint Archive & Engine Switcher Modal
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
private fun PortraitTopRacingHud(
    state: DeskRacerUiState,
    onFireItem: () -> Unit,
    onRoomInputChanged: (String) -> Unit,
    onHostClicked: () -> Unit,
    onJoinClicked: () -> Unit,
    onDiscoveredHostClicked: (String) -> Unit,
    onDisconnectClicked: () -> Unit,
    onToggleLobby: () -> Unit,
    onOpenBlueprint: () -> Unit,
    onRespawnKart: () -> Unit,
    onCycleCamera: () -> Unit,
    onToggleControlScheme: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Position & Lap Pill | Center Mario-Kart Item Roulette Box | Quick Action Icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Position (1st / 2nd) & Lap Badge + Hotspot LAN Pill
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD90F172A),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, RulerYellow)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (state.currentPlace == 1) "1ST" else "${state.currentPlace}ND",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (state.currentPlace == 1) RulerYellow else SkyCyan
                        )
                        Column {
                            Text(
                                text = "LAP ${state.localKart.lap}",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.testTag("lap_display")
                            )
                            Text(
                                text = state.cameraMode.label,
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Hotspot LAN Pill
                Surface(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable(onClick = onToggleLobby)
                        .testTag("toggle_lobby_button"),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xCC0F172A),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
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
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = stringResource(R.string.cd_open_lobby),
                            tint = when (state.lanRole) {
                                LanRole.HOSTING_HOTSPOT -> HostEmerald
                                LanRole.JOINED_CLIENT -> SkyCyan
                                LanRole.STANDALONE -> DriftAmber
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when (state.lanRole) {
                                LanRole.HOSTING_HOTSPOT -> "HOST: ${state.roomInput}"
                                LanRole.JOINED_CLIENT -> "LAN (${state.remoteKarts.size})"
                                LanRole.STANDALONE -> "HOTSPOT LAN"
                            },
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Center: Mario Kart Tour Style Top-Center Item Roulette Slot (Tap directly to FIRE!)
            val item = state.localKart.item
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 112.dp, height = 74.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (item != null) {
                                listOf(item.badgeColor, Color(0xFF0F172A))
                            } else {
                                listOf(Color(0xCC1E293B), Color(0xCC0F172A))
                            }
                        )
                    )
                    .border(
                        width = 2.5.dp,
                        color = item?.badgeColor ?: Color(0x55FFFFFF),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onFireItem() }
                    .testTag("item_display")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(6.dp)
                ) {
                    Text(
                        text = if (item != null) "TAP TO FIRE" else "ITEM BOX",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = if (item != null) RulerYellow else Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item?.displayName ?: "EMPTY",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }

            // Right: Radar Mini-Map + Quick Action Buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onToggleControlScheme,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                            .testTag("control_scheme_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = "Switch Steering Control Mode",
                            tint = RulerYellow,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onCycleCamera,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                            .testTag("camera_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = stringResource(R.string.cd_switch_camera),
                            tint = SkyCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onRespawnKart,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                            .testTag("respawn_kart_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_respawn_kart),
                            tint = DriftAmber,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenBlueprint,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                            .testTag("open_blueprint_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = stringResource(R.string.cd_open_blueprint),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (!state.isLobbyExpanded && !state.useWebGlEngine) {
                    DeskTrackMiniMap(state = state)
                }
            }
        }

        // Expandable Hotspot Room Code / IP Drawer
        AnimatedVisibility(visible = state.isLobbyExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lobby_panel"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xEE0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, SkyCyan.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ZERO-INTERNET WI-FI HOTSPOT LAN LOBBY",
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
                                        .height(44.dp)
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
    }
}

@Composable
private fun PortraitBottomThumbCockpit(
    state: DeskRacerUiState,
    onAnalogSteer: (Float, Boolean) -> Unit,
    onFireClicked: () -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onToggleAutoGas: () -> Unit,
    onLeftChanged: (Boolean) -> Unit,
    onRightChanged: (Boolean) -> Unit,
    onDriftChanged: (Boolean) -> Unit,
    onGasChanged: (Boolean) -> Unit
) {
    val isUltra = state.localKart.driftCharge > 35
    val isMini = state.localKart.driftCharge in 16..35
    val driftProgress = (state.localKart.driftCharge / 36f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hud_panel"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Sleek Digital Tachometer & Drift Charge Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xD90F172A),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = when {
                    isUltra -> ArcadeCrimson
                    isMini -> RulerYellow
                    else -> Color(0x40FFFFFF)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed Readout
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${state.speedKmh}",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = if (isUltra) RulerYellow else Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KM/H",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = SkyCyan,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    // Live Drift / Turbo Status
                    Text(
                        text = state.speedDisplayStatus,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = when {
                            isUltra -> RulerYellow
                            isMini -> DriftAmber
                            else -> SkyCyan
                        },
                        maxLines = 1,
                        modifier = Modifier.testTag("speed_display")
                    )

                    // Auto-Gas Toggle Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.autoGasEnabled) GasGreen.copy(alpha = 0.25f) else SlateSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.autoGasEnabled) GasGreen else SlateBorder
                        ),
                        modifier = Modifier
                            .clickable(onClick = onToggleAutoGas)
                            .testTag("auto_gas_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Toggle Auto Gas",
                                tint = if (state.autoGasEnabled) GasGreen else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (state.autoGasEnabled) "AUTO-GAS ON" else "MANUAL GAS",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = if (state.autoGasEnabled) GasGreen else Color.LightGray
                            )
                        }
                    }
                }

                // Live RPM & Drift Spark Charge Progress Bar
                Spacer(modifier = Modifier.height(5.dp))
                val rpmFraction = (state.speedKmh / 145f).coerceIn(0.05f, 1f)
                LinearProgressIndicator(
                    progress = { if (state.localKart.driftCharge > 0) driftProgress else rpmFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        isUltra -> ArcadeCrimson
                        isMini -> RulerYellow
                        state.localKart.driftCharge > 0 -> SkyCyan
                        else -> GasGreen
                    },
                    trackColor = Color(0xFF1E293B)
                )
            }
        }

        // 2. Ergonomic Portrait Control Deck: One-Thumb Analog Drift Wheel OR Classic Twin Pedals
        if (state.controlScheme == ControlScheme.ONE_THUMB_WHEEL) {
            PortraitOneThumbSteeringDeck(
                state = state,
                onAnalogSteer = onAnalogSteer,
                onFireClicked = onFireClicked,
                onBrakeChanged = onBrakeChanged,
                onDriftChanged = onDriftChanged
            )
        } else {
            ClassicTwinPedalsRow(
                state = state,
                onLeftChanged = onLeftChanged,
                onRightChanged = onRightChanged,
                onBrakeChanged = onBrakeChanged,
                onFireClicked = onFireClicked,
                onDriftChanged = onDriftChanged,
                onGasChanged = onGasChanged
            )
        }
    }
}

@Composable
private fun PortraitOneThumbSteeringDeck(
    state: DeskRacerUiState,
    onAnalogSteer: (Float, Boolean) -> Unit,
    onFireClicked: () -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onDriftChanged: (Boolean) -> Unit
) {
    var thumbXFraction by remember { mutableFloatStateOf(0f) } // -1f (Left) to +1f (Right)
    var isDraggingWheel by remember { mutableStateOf(false) }
    var boxWidthPx by remember { mutableFloatStateOf(1f) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Quick Hop/Drift Lock or Brake Button
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArcadeTouchButton(
                label = stringResource(R.string.ctrl_drift),
                contentDesc = stringResource(R.string.cd_drift_hop),
                testTag = "btn_drift",
                size = 64.dp,
                fontSizeSp = 12f,
                baseColor = DriftAmber.copy(alpha = 0.88f),
                activeColor = RulerYellow,
                isPressedExternal = state.controls.drift,
                onPressChanged = onDriftChanged
            )
            ArcadeTouchButton(
                label = "BRAKE",
                contentDesc = stringResource(R.string.cd_brake_reverse),
                testTag = "btn_brake",
                size = 54.dp,
                fontSizeSp = 10f,
                baseColor = Color(0xCC334155),
                activeColor = ArcadeCrimson,
                isPressedExternal = state.controls.brake,
                onPressChanged = onBrakeChanged
            )
        }

        // Center: Mario Kart Tour Style One-Thumb Analog Steering & Hop-Drift Arc
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(124.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xD90F172A))
                .border(
                    width = 2.dp,
                    color = if (state.controls.drift) RulerYellow else SkyCyan.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(28.dp)
                )
                .onSizeChanged { boxWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDraggingWheel = true
                        fun updateFromOffset(pos: Offset) {
                            val normX = ((pos.x / boxWidthPx) * 2f - 1f).coerceIn(-1f, 1f)
                            thumbXFraction = normX
                            // Pulling past 52% or dragging slightly upward engages automatic Hop-Drift!
                            val autoDrift = abs(normX) > 0.52f || pos.y < 36f || state.controls.drift
                            onAnalogSteer(normX, autoDrift)
                        }
                        updateFromOffset(down.position)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                updateFromOffset(change.position)
                            }
                        } while (event.changes.any { it.pressed })

                        isDraggingWheel = false
                        thumbXFraction = 0f
                        onAnalogSteer(0f, false)
                    }
                }
                .testTag("one_thumb_steering_wheel")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w * 0.5f
                val cy = h * 0.55f

                // Outer Steering Arc
                drawArc(
                    color = Color(0x3338BDF8),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 1.1f),
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )

                // Extreme Left & Right Orange Drift Zones
                drawArc(
                    color = DriftAmber.copy(alpha = 0.55f),
                    startAngle = 195f,
                    sweepAngle = 32f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 1.1f),
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = DriftAmber.copy(alpha = 0.55f),
                    startAngle = 313f,
                    sweepAngle = 32f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 1.1f),
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )

                // Center Crosshair
                drawCircle(
                    color = Color(0x44FFFFFF),
                    radius = 24f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )

                // Active Thumb Puck
                val puckX = cx + thumbXFraction * (w * 0.36f)
                val puckColor = when {
                    state.localKart.driftCharge > 35 -> ArcadeCrimson
                    state.controls.drift -> RulerYellow
                    isDraggingWheel -> SkyCyan
                    else -> Color.White
                }
                drawCircle(
                    color = puckColor.copy(alpha = 0.30f),
                    radius = 42f,
                    center = Offset(puckX, cy)
                )
                drawCircle(
                    color = puckColor,
                    radius = 26f,
                    center = Offset(puckX, cy)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "◀ DRIFT ZONE",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = DriftAmber
                    )
                    Text(
                        text = "DRIFT ZONE ▶",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = DriftAmber
                    )
                }
                Text(
                    text = if (isDraggingWheel) {
                        if (state.controls.drift) "🔥 HOLDING HOP-DRIFT!" else "STEERING..."
                    } else {
                        "SLIDE THUMB TO STEER & DRIFT"
                    },
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }

        // Right Side: Item Fire & Manual Turbo Gas Button
        val hasItem = state.localKart.item != null
        ArcadeTouchButton(
            label = if (hasItem) "FIRE" else "TURBO",
            contentDesc = stringResource(R.string.cd_fire_item),
            testTag = "btn_item",
            size = 72.dp,
            fontSizeSp = 13f,
            baseColor = if (hasItem) ItemRed.copy(alpha = 0.95f) else GasGreen.copy(alpha = 0.85f),
            activeColor = RulerYellow,
            isPressedExternal = false,
            onPressChanged = { pressed ->
                if (pressed) onFireClicked()
            }
        )
    }
}

@Composable
private fun ClassicTwinPedalsRow(
    state: DeskRacerUiState,
    onLeftChanged: (Boolean) -> Unit,
    onRightChanged: (Boolean) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
    onFireClicked: () -> Unit,
    onDriftChanged: (Boolean) -> Unit,
    onGasChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ArcadeTouchButton(
                label = stringResource(R.string.ctrl_left),
                contentDesc = stringResource(R.string.cd_steer_left),
                testTag = "btn_left",
                size = 64.dp,
                fontSizeSp = 22f,
                baseColor = Color(0xCC1E293B),
                activeColor = SkyCyan,
                isPressedExternal = state.controls.left,
                onPressChanged = onLeftChanged
            )
            ArcadeTouchButton(
                label = stringResource(R.string.ctrl_right),
                contentDesc = stringResource(R.string.cd_steer_right),
                testTag = "btn_right",
                size = 64.dp,
                fontSizeSp = 22f,
                baseColor = Color(0xCC1E293B),
                activeColor = SkyCyan,
                isPressedExternal = state.controls.right,
                onPressChanged = onRightChanged
            )
            ArcadeTouchButton(
                label = "REV",
                contentDesc = stringResource(R.string.cd_brake_reverse),
                testTag = "btn_brake",
                size = 50.dp,
                fontSizeSp = 10f,
                baseColor = Color(0xB3334155),
                activeColor = ArcadeCrimson,
                isPressedExternal = state.controls.brake,
                onPressChanged = onBrakeChanged
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val hasItem = state.localKart.item != null
            ArcadeTouchButton(
                label = stringResource(R.string.ctrl_fire),
                contentDesc = stringResource(R.string.cd_fire_item),
                testTag = "btn_item",
                size = 58.dp,
                fontSizeSp = 12f,
                baseColor = if (hasItem) ItemRed.copy(alpha = 0.92f) else ItemRed.copy(alpha = 0.45f),
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
                size = 62.dp,
                fontSizeSp = 12f,
                baseColor = DriftAmber.copy(alpha = 0.88f),
                activeColor = RulerYellow,
                isPressedExternal = state.controls.drift,
                onPressChanged = onDriftChanged
            )
            ArcadeTouchButton(
                label = stringResource(R.string.ctrl_gas),
                contentDesc = stringResource(R.string.cd_gas_pedal),
                testTag = "btn_gas",
                size = 72.dp,
                fontSizeSp = 15f,
                baseColor = GasGreen.copy(alpha = 0.90f),
                activeColor = Color(0xFF86EFAC),
                isPressedExternal = state.controls.gas,
                onPressChanged = onGasChanged
            )
        }
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
            .pointerInput(testTag) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressedLocal = true
                    onPressChanged(true)
                    waitForUpOrCancellation()
                    isPressedLocal = false
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
            color = if (active) SlateAbyss else Color.White,
            textAlign = TextAlign.Center
        )
    }
}
