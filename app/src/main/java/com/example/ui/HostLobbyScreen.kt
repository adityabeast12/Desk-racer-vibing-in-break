package com.example.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NetworkWifi
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.WifiTethering
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.KartBodyType
import com.example.game.Tracks
import com.example.net.LobbySlotInfo
import com.example.ui.components.DRBadge
import com.example.ui.components.DRBadgeKind
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRChip
import com.example.ui.components.DRDialog
import com.example.ui.components.DRLabel
import com.example.ui.components.DRPanel
import com.example.ui.components.DRScreenHeader
import com.example.ui.components.DRSegmented
import com.example.ui.components.KartThumb
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType

const val LOBBY_MAX_SLOTS = 8

@Composable
fun HostLobbyScreen(
    roomCode: String,
    hostIpAddress: String,
    slots: List<LobbySlotInfo>,
    selectedTrackId: Int,
    selectedLaps: Int,
    cpuFillEnabled: Boolean,
    botCount: Int = 7,
    botDifficulty: CpuDifficulty = CpuDifficulty.PRO,
    ccClass: CcClass = CcClass.CC_150,
    itemMode: ItemMode,
    canForceStart: Boolean,
    onSelectTrack: (Int) -> Unit,
    onSelectLaps: (Int) -> Unit,
    onToggleCpuFill: () -> Unit,
    onCycleBotCount: () -> Unit = {},
    onCycleBotDifficulty: () -> Unit = {},
    onCycleCcClass: () -> Unit = {},
    onCycleItemMode: () -> Unit,
    onKickSlot: (Int) -> Unit,
    onStartRace: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var confirmClose by remember { mutableStateOf(false) }
    var confirmForce by remember { mutableStateOf(false) }
    val humans = slots.filter { it.isHuman }
    val notReady = humans.filter { !it.isReady && it.slotIndex != 0 }
    val allReady = notReady.isEmpty()
    val readyCount = humans.count { it.isReady || it.slotIndex == 0 }
    val track = Tracks.byId(selectedTrackId)

    BackHandler {
        if (confirmClose) confirmClose = false else confirmClose = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Bg.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("host_lobby_screen")
    ) {
        Column(Modifier.fillMaxSize()) {
            DRScreenHeader(kicker = "Host LAN", title = "Room $roomCode", onBack = { confirmClose = true }, backTestTag = "btn_lobby_back")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Left: connection + race settings
                DRPanel(Modifier.width(292.dp).fillMaxHeight()) {
                    DRLabel("Friends join this room")
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(roomCode, style = DRType.NumXL.copy(fontSize = 34.sp, letterSpacing = 4.sp), color = DR.TextHi, modifier = Modifier.testTag("lobby_room_code"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.WifiTethering, contentDescription = null, tint = DR.Ok, modifier = Modifier.size(16.dp))
                        Text("$hostIpAddress:${GameConstants.TCP_CONTROL_PORT}", style = DRType.NumS.copy(fontSize = 12.5.sp), color = DR.TextMid, maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    DRButton(
                        text = "Hotspot settings",
                        icon = Icons.Rounded.WifiTethering,
                        variant = DRButtonVariant.SECONDARY,
                        size = DRButtonSize.S,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "btn_open_hotspot_settings"
                    )
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(DR.Line))
                    DRLabel("Race")
                    Row(
                        Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LobbyArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous track") {
                            onSelectTrack((selectedTrackId - 1 + Tracks.all.size) % Tracks.all.size)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(track.name, style = DRType.Title.copy(fontSize = 17.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.difficultyLabel, style = DRType.Caption.copy(fontSize = 11.sp), color = DR.TextMid, maxLines = 1)
                        }
                        LobbyArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next track") {
                            onSelectTrack((selectedTrackId + 1) % Tracks.all.size)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val lapOpts = listOf(1, 3, 5)
                    DRSegmented(
                        options = lapOpts.map { "$it laps" },
                        selected = lapOpts.indexOf(selectedLaps).let { if (it < 0) 1 else it },
                        onSelect = { onSelectLaps(lapOpts[it]) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 34.dp,
                        testTagPrefix = "lobby_laps"
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DRChip("Items · ${itemMode.label}", onClick = onCycleItemMode, testTag = "lobby_items")
                        DRChip(ccClass.label, onClick = onCycleCcClass, testTag = "lobby_cc")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DRChip(if (cpuFillEnabled) "Bots on" else "Bots off", selected = cpuFillEnabled, onClick = onToggleCpuFill, testTag = "lobby_cpu_fill")
                        if (cpuFillEnabled) {
                            DRChip("$botCount", onClick = onCycleBotCount, testTag = "lobby_bot_count")
                            DRChip(botDifficulty.label, onClick = onCycleBotDifficulty, testTag = "lobby_bot_skill")
                        }
                    }
                }

                // Right: players + start
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    DRPanel(Modifier.fillMaxWidth().weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DRLabel("Players")
                                Text("${humans.size}/$LOBBY_MAX_SLOTS", style = DRType.NumS, color = DR.TextHi)
                            }
                            DRLabel("$readyCount ready", color = DR.Ok)
                        }
                        for (row in 0 until LOBBY_MAX_SLOTS / 2) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (col in 0 until 2) {
                                    val idx = row * 2 + col
                                    LobbySlotRow(
                                        slotIdx = idx,
                                        info = slots.firstOrNull { it.slotIndex == idx },
                                        cpuFill = cpuFillEnabled,
                                        onKick = { onKickSlot(idx) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!allReady) {
                                Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = DR.TextLo, modifier = Modifier.size(16.dp))
                                Text(
                                    "Waiting for ${notReady.joinToString { it.playerName }.take(40)}…",
                                    style = DRType.Caption,
                                    color = DR.TextLo,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (humans.size <= 1) {
                                Text("Friends join from Join LAN race on the same hotspot.", style = DRType.Caption, color = DR.TextLo, maxLines = 2)
                            }
                        }
                        DRButton(
                            text = "Start race",
                            icon = Icons.Rounded.Flag,
                            size = DRButtonSize.L,
                            enabled = allReady || canForceStart,
                            onClick = { if (allReady) onStartRace() else confirmForce = true },
                            modifier = Modifier.width(200.dp),
                            testTag = "btn_host_start_race"
                        )
                    }
                }
            }
        }

        if (confirmForce) {
            DRDialog(
                kicker = "Not everyone is ready",
                title = "Start without them?",
                body = "${notReady.joinToString { it.playerName }} will be swapped for bots if they don't ready up.",
                confirmText = "Start",
                confirmIcon = Icons.Rounded.Flag,
                onConfirm = {
                    confirmForce = false
                    onStartRace()
                },
                dismissText = "Wait",
                onDismiss = { confirmForce = false },
                testTag = "dialog_force_start"
            )
        }
        if (confirmClose) {
            DRDialog(
                kicker = "Close room",
                title = "Close this room?",
                body = "Everyone in the lobby will be disconnected.",
                confirmText = "Close room",
                onConfirm = {
                    confirmClose = false
                    onBack()
                },
                dismissText = "Stay",
                onDismiss = { confirmClose = false },
                destructive = true,
                testTag = "dialog_close_room"
            )
        }
    }
}

@Composable
private fun LobbyArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 40.dp, height = 44.dp)
            .clip(DRShape.Control)
            .background(DR.S2)
            .border(1.dp, DR.Line, DRShape.Control)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Icon(icon, contentDescription = desc, tint = DR.TextHi, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun LobbySlotRow(
    slotIdx: Int,
    info: LobbySlotInfo?,
    cpuFill: Boolean,
    onKick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    localSlot: Int = 0
) {
    val isYou = info != null && info.slotIndex == localSlot
    Row(
        modifier = modifier
            .height(46.dp)
            .background(if (isYou) DR.YouWash else Color.Transparent)
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                if (isYou) drawRect(DR.You, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = 8.dp)
            .testTag("lobby_slot_$slotIdx"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${slotIdx + 1}", style = DRType.NumS, color = DR.TextDis, modifier = Modifier.width(12.dp))
        if (info != null) {
            KartThumb(info.bodyType, Modifier.size(width = 40.dp, height = 26.dp))
            Column(Modifier.weight(1f)) {
                Text(info.playerName, style = DRType.BodyStrong.copy(fontSize = 12.5.sp, lineHeight = 15.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (slotIdx != 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Rounded.NetworkWifi, contentDescription = null, tint = if (info.pingMs < 80) DR.Ok else DR.Amber, modifier = Modifier.size(11.dp))
                        Text("${info.pingMs}ms", style = DRType.NumS.copy(fontSize = 10.5.sp), color = DR.TextLo)
                    }
                }
            }
            when {
                slotIdx == 0 -> DRBadge("Host", DRBadgeKind.AMBER)
                info.isAway -> DRBadge("Away", DRBadgeKind.MUTED)
                info.isReady -> DRBadge("Ready", DRBadgeKind.OK)
                else -> DRBadge("Picking", DRBadgeKind.MUTED)
            }
            if (onKick != null && slotIdx != 0 && info.isHuman) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(role = Role.Button, onClick = onKick)
                        .testTag("lobby_kick_$slotIdx")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${info.playerName}", tint = DR.TextLo, modifier = Modifier.size(18.dp))
                }
            } else {
                Spacer(Modifier.width(6.dp))
            }
        } else {
            Icon(if (cpuFill) Icons.Rounded.SmartToy else Icons.Rounded.PersonAdd, contentDescription = null, tint = DR.TextDis, modifier = Modifier.size(18.dp))
            Text(
                if (cpuFill) "Bot fills this slot" else "Open slot",
                style = DRType.Caption.copy(fontSize = 11.sp),
                color = DR.TextDis,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Suppress("unused")
private val unusedBody = KartBodyType.CLASSIC
