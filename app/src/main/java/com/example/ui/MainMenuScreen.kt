package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShopCatalog
import com.example.data.UserProfileAndSettings
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.CupType
import com.example.game.GameMode
import com.example.game.ItemMode
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.game.Track
import com.example.game.Tracks
import com.example.render.AssetRepository
import com.example.ui.hud.formatRaceTime
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DRColors
import com.example.ui.theme.DRType
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.Poppins
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
    onOpenShop: () -> Unit = {},
    onOpenDeskPass: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenHowToConnect: () -> Unit
) {
    var showRaceSetupModal by remember { mutableStateOf(false) }
    var activeSetupMode by remember { mutableStateOf(GameMode.QUICK_RACE) }

    val selectedTrack = Tracks.byId(profile.selectedTrackId)
    val passTier = ((profile.deskPassXp / 200) + 1).coerceIn(1, 40)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x550B1020),
                        Color(0xB80B1020),
                        Color(0xF00B1020)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("main_menu_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Pane (48%): Title, Active Track & Mode Summary, 3D Showroom Window, Footer Bar with Coins/Gems/Pass Tier
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "DESK RACER",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RulerYellow
                        ) {
                            Text(
                                text = "LAN v4",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = SlateAbyss,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "REAL-WORLD 3D STATIONERY RACER • OFFLINE HOTSPOT",
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = SkyCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Mode Quick-Launch Row: Grand Prix, Time Trial, Battle
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
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

                    // Active Track & Rules Summary Pill (opens two-pane Race Setup)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xD9141C33),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SkyCyan.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .padding(top = 2.dp)
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
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val botsDesc = if (profile.raceWithBots) "${profile.botCount} Bots (${profile.cpuDifficulty.label})" else "Solo (0 Bots)"
                                Text(
                                    text = "$botsDesc • Items: ${profile.selectedItemMode.label} • Tap to configure",
                                    fontFamily = Poppins,
                                    fontSize = 10.sp,
                                    color = RulerYellow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Bottom-left Footer Bar: Player Profile, Coins, Gems, Pass Tier & Hotspot Help
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xD9141C33),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(profile.kartColorHex))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(profile.kartColorHex), RoundedCornerShape(5.dp))
                                )
                                Text(
                                    text = "${profile.playerName} • ${profile.kartBody.title.uppercase()}",
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xD9141C33),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RulerYellow.copy(alpha = 0.7f)),
                            modifier = Modifier.clickable(onClick = onOpenShop)
                        ) {
                            Text(
                                text = "🪙 ${ShopCatalog.formatCount(profile.coinsBalance)}  💎 ${profile.gemsBalance}",
                                style = DRType.TabularTimer,
                                fontSize = 10.sp,
                                color = RulerYellow,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xD9141C33),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HostEmerald.copy(alpha = 0.7f)),
                            modifier = Modifier.clickable(onClick = onOpenDeskPass)
                        ) {
                            Text(
                                text = "PASS T$passTier",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = HostEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clickable(onClick = onOpenHowToConnect)
                            .padding(horizontal = 4.dp)
                            .testTag("btn_how_to_connect"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            tint = RulerYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "How to play & connect over Wi-Fi Hotspot (Zero Internet)",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = RulerYellow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right Pane (52%): 3 Primary Mode Cards + 4 Compact Secondary Pills (GARAGE, SHOP, DESK PASS, SETTINGS)
            Column(
                modifier = Modifier
                    .weight(0.52f)
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

                // 2x2 Compact Secondary Row: GARAGE, SHOP, DESK PASS, SETTINGS
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
                        title = "SHOP",
                        icon = Icons.Default.ShoppingBag,
                        accentColor = RulerYellow,
                        testTag = "btn_menu_shop",
                        onClick = onOpenShop,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactMenuButton(
                        title = "DESK PASS",
                        icon = Icons.Default.EmojiEvents,
                        accentColor = HostEmerald,
                        testTag = "btn_menu_desk_pass",
                        onClick = onOpenDeskPass,
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

        // Two-Pane Race Setup & Track Picker Modal (Part G3)
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
            .height(36.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Color.White,
                maxLines = 1
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
            .background(Color(0xEB0B1020))
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
                                        fontFamily = Poppins,
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
                        Text("CLOSE", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                // Two-Pane Body (Left: Track or Cup List, Right: Selected Track Preview + Rules)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Pane: Track or Cup List
                    Box(modifier = Modifier.weight(1.05f).fillMaxHeight()) {
                        if (mode == GameMode.GRAND_PRIX) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            .fillMaxWidth()
                                            .weight(1f)
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
                                                fontFamily = Poppins,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = RulerYellow
                                            )
                                            Text(
                                                text = c.subtitle,
                                                fontFamily = Poppins,
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(Tracks.all) { t ->
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
                    }

                    // Right Pane: Rules & Launch Button
                    Surface(
                        modifier = Modifier.weight(0.95f).fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        color = DRColors.SlateCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            val activeTrack = Tracks.byId(trackId)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = activeTrack.name.uppercase(),
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = RulerYellow
                                )
                                Text(
                                    text = "${activeTrack.subtitle} • Surface: ${activeTrack.surfaceTexture.substringAfterLast('/').substringBeforeLast('.').uppercase()}",
                                    fontFamily = Poppins,
                                    fontSize = 10.sp,
                                    color = SkyCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (mode != GameMode.TIME_TRIAL) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "BOTS",
                                            fontFamily = Poppins,
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
                                    }
                                    if (raceWithBots) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(3, 5, 7).forEach { count ->
                                                val active = botCount == count
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (active) HostEmerald else SlateSurface,
                                                    modifier = Modifier
                                                        .height(30.dp)
                                                        .clickable {
                                                            botCount = count
                                                            pushChanges()
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                        Text(
                                                            text = "$count",
                                                            fontFamily = Poppins,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = if (active) SlateAbyss else Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bot Difficulty + Items
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        CpuDifficulty.entries.forEach { d ->
                                            val active = difficulty == d
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (active) DriftAmber else SlateSurface,
                                                modifier = Modifier
                                                    .height(30.dp)
                                                    .clickable {
                                                        difficulty = d
                                                        pushChanges()
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                                                    Text(
                                                        text = d.label.uppercase(),
                                                        fontFamily = Poppins,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp,
                                                        color = if (active) SlateAbyss else Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SlateSurface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, HostEmerald),
                                        modifier = Modifier
                                            .height(30.dp)
                                            .clickable {
                                                val entries = ItemMode.entries
                                                itemMode = entries[(itemMode.ordinal + 1) % entries.size]
                                                pushChanges()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(
                                                text = "ITEMS: ${itemMode.label.uppercase()}",
                                                fontFamily = Poppins,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = HostEmerald
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "SOLO 3 LAPS • RACE YOUR 20 HZ PERSONAL GHOST",
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = RulerYellow
                                )
                            }

                            // Laps + CC Class
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(1, 3, 5).forEach { l ->
                                        val active = laps == l
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (active) SkyCyan else SlateSurface,
                                            modifier = Modifier
                                                .height(32.dp)
                                                .clickable {
                                                    laps = l
                                                    pushChanges()
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                Text(
                                                    text = "${l}L",
                                                    fontFamily = Poppins,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = if (active) SlateAbyss else Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CcClass.entries.forEach { cc ->
                                        val active = ccClass == cc
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (active) RulerYellow else SlateSurface,
                                            modifier = Modifier
                                                .height(32.dp)
                                                .clickable {
                                                    ccClass = cc
                                                    pushChanges()
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                                Text(
                                                    text = cc.label,
                                                    fontFamily = Poppins,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = if (active) SlateAbyss else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { onStart(mode, cup, trackId) },
                                colors = ButtonDefaults.buttonColors(containerColor = RulerYellow),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("btn_race_setup_start")
                            ) {
                                Text(
                                    text = "START RACE ▶",
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.ExtraBold,
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
}

@Composable
private fun TrackCardMini(
    track: Track,
    selected: Boolean,
    bestLapMs: Long,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val previewBmp = remember(track.previewCard) {
        AssetRepository.loadUiBitmapStatic(context, track.previewCard)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SkyCyan.copy(alpha = 0.22f) else SlateSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SkyCyan else SlateBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .testTag("setup_track_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (previewBmp != null) {
                Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 64.dp, height = 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected) SkyCyan else SlateBorder, RoundedCornerShape(8.dp))
                )
            } else {
                // Fallback Mini Spline Preview
                Canvas(modifier = Modifier.size(42.dp)) {
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
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = track.name,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${"★".repeat(track.difficultyStars)} • ${track.totalLength.roundToInt()}u • ${track.surfaceTexture.substringAfterLast('/').substringBeforeLast('.')}",
                    fontFamily = Poppins,
                    fontSize = 9.sp,
                    color = RulerYellow,
                    maxLines = 1
                )
            }

            Text(
                text = if (bestLapMs > 0L) formatRaceTime(bestLapMs) else formatRaceTime(track.goldTimeMs),
                style = DRType.TabularTimer,
                fontSize = 10.sp,
                color = SkyCyan,
                maxLines = 1
            )
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
            .height(54.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .background(accentColor.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor)
                }
                Column {
                    Text(
                        text = title,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontFamily = Poppins,
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "▶",
                fontFamily = Poppins,
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
        modifier = modifier
            .height(44.dp)
            .border(1.2.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
