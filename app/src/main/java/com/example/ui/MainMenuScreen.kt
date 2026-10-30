package com.example.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileAndSettings
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.CupType
import com.example.game.GameMode
import com.example.game.ItemMode
import com.example.game.Track
import com.example.game.Tracks
import com.example.ui.hud.formatRaceTime
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
import kotlin.math.roundToInt

@Composable
fun MainMenuScreen(
    profile: UserProfileAndSettings,
    onQuickRace: () -> Unit,
    onStartConfiguredRace: (GameMode, CupType, Int) -> Unit = { _, _, _ -> onQuickRace() },
    onUpdateRaceSetup: (
        raceWithBots: Boolean,
        botCount: Int,
        difficulty: CpuDifficulty,
        laps: Int,
        itemMode: ItemMode,
        ccClass: CcClass,
        trackId: Int,
        cup: CupType
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHowToConnect: () -> Unit
) {
    var showRaceSetupModal by remember { mutableStateOf(false) }
    var activeSetupMode by remember { mutableStateOf(GameMode.QUICK_RACE) }

    val selectedTrack = Tracks.byId(profile.selectedTrackId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x88090D16),
                        Color(0xCC0F172A),
                        Color(0xF20F172A)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .testTag("main_menu_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Half: Animated Title, Active Setup Summary Chip, Mode Pills, Profile & Hotspot Help
            Column(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "DESK RACER",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ArcadeCrimson
                        ) {
                            Text(
                                text = "LAN v2",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "8-KART OFFLINE WI-FI HOTSPOT CHAMPIONSHIP • 8 TRACKS",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SkyCyan
                    )

                    // Mode Quick-Launch Row: Grand Prix, Time Trial, Battle, Setup
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModeChip(
                            label = "🏆 GRAND PRIX",
                            accent = RulerYellow,
                            testTag = "btn_mode_grand_prix",
                            onClick = {
                                activeSetupMode = GameMode.GRAND_PRIX
                                showRaceSetupModal = true
                            }
                        )
                        ModeChip(
                            label = "⏱ TIME TRIAL",
                            accent = SkyCyan,
                            testTag = "btn_mode_time_trial",
                            onClick = {
                                activeSetupMode = GameMode.TIME_TRIAL
                                showRaceSetupModal = true
                            }
                        )
                        ModeChip(
                            label = "🎈 BATTLE",
                            accent = ArcadeCrimson,
                            testTag = "btn_mode_battle",
                            onClick = {
                                onStartConfiguredRace(GameMode.BATTLE_BALLOONS, profile.selectedCup, 6)
                            }
                        )
                    }

                    // Active Track & Rules Summary Pill (tappable to open Race Setup)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xD91E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SkyCyan.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable {
                                activeSetupMode = GameMode.QUICK_RACE
                                showRaceSetupModal = true
                            }
                            .testTag("btn_open_race_setup")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = SkyCyan, modifier = Modifier.size(18.dp))
                            Column {
                                Text(
                                    text = "${selectedTrack.name} • ${profile.selectedCcClass.label} • ${profile.selectedLaps} LAPS",
                                    fontFamily = OrbitronFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                val botsDesc = if (profile.raceWithBots) "${profile.botCount} Bots (${profile.cpuDifficulty.label})" else "Solo (0 Bots)"
                                Text(
                                    text = "$botsDesc • Items: ${profile.selectedItemMode.label} • Tap to customize",
                                    fontSize = 10.sp,
                                    color = RulerYellow
                                )
                            }
                        }
                    }
                }

                // Bottom-left Player Profile & "How to connect" link
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xCC1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(profile.kartColorHex))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(profile.kartColorHex), RoundedCornerShape(8.dp))
                            )
                            Text(
                                text = "${profile.playerName} • ${profile.kartBody.title.uppercase()}",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clickable(onClick = onOpenHowToConnect)
                            .padding(horizontal = 8.dp)
                            .testTag("btn_how_to_connect"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = RulerYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "How to connect over Wi-Fi Hotspot (Zero Internet)",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RulerYellow
                        )
                    }
                }
            }

            // Right Half: Vertical Stack of 5 Large Action Buttons
            Column(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.End
            ) {
                MenuActionButton(
                    title = "QUICK RACE",
                    subtitle = "${selectedTrack.name} • ${if (profile.raceWithBots) "${profile.botCount} Bots" else "Solo"} • ${profile.selectedCcClass.label}",
                    icon = Icons.Default.Flag,
                    accentColor = ArcadeCrimson,
                    testTag = "btn_menu_quick_race",
                    onClick = onQuickRace
                )
                MenuActionButton(
                    title = "HOST RACE",
                    subtitle = "Create 2–8 Player Room on Hotspot / Wi-Fi",
                    icon = Icons.Default.WifiTethering,
                    accentColor = HostEmerald,
                    testTag = "btn_menu_host_race",
                    onClick = onHostRace
                )
                MenuActionButton(
                    title = "JOIN RACE",
                    subtitle = "Auto-Discover or Enter Host IP",
                    icon = Icons.Default.Wifi,
                    accentColor = JoinBlue,
                    testTag = "btn_menu_join_race",
                    onClick = onJoinRace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactMenuButton(
                        title = "GARAGE",
                        icon = Icons.Default.Build,
                        accentColor = DriftAmber,
                        testTag = "btn_menu_garage",
                        onClick = onOpenGarage,
                        modifier = Modifier.weight(1f)
                    )
                    CompactMenuButton(
                        title = "SETTINGS",
                        icon = Icons.Default.Settings,
                        accentColor = SkyCyan,
                        testTag = "btn_menu_settings",
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Race Setup & Track Picker Modal (Part C1 & C2)
        if (showRaceSetupModal) {
            RaceSetupDialog(
                profile = profile,
                initialMode = activeSetupMode,
                onUpdateSetup = onUpdateRaceSetup,
                onStart = { mode, cup, trackId ->
                    showRaceSetupModal = false
                    onStartConfiguredRace(mode, cup, trackId)
                },
                onDismiss = { showRaceSetupModal = false }
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SlateSurface,
        border = androidx.compose.foundation.BorderStroke(1.2.dp, accent),
        modifier = Modifier
            .height(38.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun RaceSetupDialog(
    profile: UserProfileAndSettings,
    initialMode: GameMode,
    onUpdateSetup: (
        raceWithBots: Boolean,
        botCount: Int,
        difficulty: CpuDifficulty,
        laps: Int,
        itemMode: ItemMode,
        ccClass: CcClass,
        trackId: Int,
        cup: CupType
    ) -> Unit,
    onStart: (GameMode, CupType, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(initialMode) }
    var cup by remember { mutableStateOf(profile.selectedCup) }
    var trackId by remember { mutableStateOf(profile.selectedTrackId) }
    var raceWithBots by remember { mutableStateOf(profile.raceWithBots) }
    var botCount by remember { mutableStateOf(profile.botCount) }
    var difficulty by remember { mutableStateOf(profile.cpuDifficulty) }
    var laps by remember { mutableStateOf(profile.selectedLaps) }
    var itemMode by remember { mutableStateOf(profile.selectedItemMode) }
    var ccClass by remember { mutableStateOf(profile.selectedCcClass) }

    fun pushChanges() {
        onUpdateSetup(raceWithBots, botCount, difficulty, laps, itemMode, ccClass, trackId, cup)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6090D16))
            .testTag("race_setup_dialog")
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SlateAbyss,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SkyCyan),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header + Mode Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(GameMode.QUICK_RACE, GameMode.GRAND_PRIX, GameMode.TIME_TRIAL, GameMode.BATTLE_BALLOONS).forEach { m ->
                            val active = mode == m
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) SkyCyan else SlateSurface,
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable { mode = m }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = m.title,
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (active) SlateAbyss else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("CLOSE", fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                // Track or Cup Picker Row
                if (mode == GameMode.GRAND_PRIX) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CupType.entries.forEach { c ->
                            val selected = cup == c
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) RulerYellow.copy(alpha = 0.22f) else SlateSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) RulerYellow else SlateBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(92.dp)
                                    .clickable {
                                        cup = c
                                        trackId = c.trackIds.first()
                                        pushChanges()
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "🏆 ${c.title}",
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = RulerYellow
                                    )
                                    Text(
                                        text = c.subtitle,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Tracks.all.forEach { t ->
                            val selected = trackId == t.id
                            val bestMs = profile.bestLapMsByTrack[t.id] ?: 0L
                            TrackCardMini(
                                track = t,
                                selected = selected,
                                bestLapMs = bestMs,
                                onClick = {
                                    trackId = t.id
                                    pushChanges()
                                }
                            )
                        }
                    }
                }

                // Race Rules Controls (Bots, Difficulty, Laps, Items, CC)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mode != GameMode.TIME_TRIAL) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "BOTS:",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = SkyCyan
                            )
                            Switch(
                                checked = raceWithBots,
                                onCheckedChange = {
                                    raceWithBots = it
                                    pushChanges()
                                }
                            )
                            if (raceWithBots) {
                                listOf(3, 5, 7).forEach { count ->
                                    val active = botCount == count
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (active) HostEmerald else SlateSurface,
                                        modifier = Modifier
                                            .height(32.dp)
                                            .clickable {
                                                botCount = count
                                                pushChanges()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(
                                                text = "$count BOTS",
                                                fontFamily = OrbitronFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = if (active) SlateAbyss else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bot Difficulty
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CpuDifficulty.entries.forEach { d ->
                                val active = difficulty == d
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (active) DriftAmber else SlateSurface,
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clickable {
                                            difficulty = d
                                            pushChanges()
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(
                                            text = d.label.uppercase(),
                                            fontFamily = OrbitronFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = if (active) SlateAbyss else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "TIME TRIAL: SOLO 3 LAPS • RACE AGAINST YOUR 20 HZ GHOST",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RulerYellow
                        )
                    }
                }

                // Bottom Row: Laps, Item Mode, CC Class, and START RACE button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 5).forEach { l ->
                            val active = laps == l
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) SkyCyan else SlateSurface,
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable {
                                        laps = l
                                        pushChanges()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
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

                        CcClass.entries.forEach { cc ->
                            val active = ccClass == cc
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) RulerYellow else SlateSurface,
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable {
                                        ccClass = cc
                                        pushChanges()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = cc.label,
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (active) SlateAbyss else Color.White
                                    )
                                }
                            }
                        }

                        if (mode != GameMode.TIME_TRIAL) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlateSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, HostEmerald),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable {
                                        val entries = ItemMode.entries
                                        itemMode = entries[(itemMode.ordinal + 1) % entries.size]
                                        pushChanges()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = "ITEMS: ${itemMode.label.uppercase()}",
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = HostEmerald
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onStart(mode, cup, trackId) },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcadeCrimson),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_race_setup_start")
                    ) {
                        Text(
                            text = "LAUNCH RACE ▶",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCardMini(
    track: Track,
    selected: Boolean,
    bestLapMs: Long,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SkyCyan.copy(alpha = 0.22f) else SlateSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SkyCyan else SlateBorder
        ),
        modifier = Modifier
            .width(156.dp)
            .height(98.dp)
            .clickable(onClick = onClick)
            .testTag("setup_track_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Spline Preview
            Canvas(modifier = Modifier.size(46.dp)) {
                val w = size.width
                val h = size.height
                val scaleX = (w - 8f) / (track.deskHalfExtentX * 1.7f)
                val scaleZ = (h - 8f) / (track.deskHalfExtentZ * 1.5f)
                val path = Path()
                if (track.samples.isNotEmpty()) {
                    val p0 = track.samples[0].pos
                    path.moveTo(w * 0.5f + p0.x * scaleX, h * 0.5f + p0.z * scaleZ)
                    for (i in 1 until track.samples.size step 3) {
                        val p = track.samples[i].pos
                        path.lineTo(w * 0.5f + p.x * scaleX, h * 0.5f + p.z * scaleZ)
                    }
                    path.close()
                    drawPath(path, color = if (selected) SkyCyan else Color(0xFF94A3B8), style = Stroke(width = 3f))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = track.name,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = "${"★".repeat(track.difficultyStars)} • ${track.totalLength.roundToInt()}u",
                    fontSize = 9.sp,
                    color = RulerYellow
                )
                Text(
                    text = if (bestLapMs > 0L) "BEST ${formatRaceTime(bestLapMs)}" else "GOLD ${formatRaceTime(track.goldTimeMs)}",
                    fontSize = 8.sp,
                    color = SkyCyan,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MenuActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .border(1.5.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor)
                }
                Column {
                    Text(
                        text = title,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            Text(
                text = "▶",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accentColor
            )
        }
    }
}

@Composable
private fun CompactMenuButton(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
        modifier = modifier
            .height(56.dp)
            .border(1.5.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}
