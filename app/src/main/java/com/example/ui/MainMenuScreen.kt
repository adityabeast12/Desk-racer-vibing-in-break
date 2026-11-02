package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.render.AssetRepository
import com.example.ui.components.DRBadge
import com.example.ui.components.DRBadgeKind
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRKerbStrip
import com.example.ui.components.DRLabel
import com.example.ui.components.DRMarkerTag
import com.example.ui.components.DRMenuRow
import com.example.ui.components.DRPanel
import com.example.ui.components.DRScreenHeader
import com.example.ui.components.DRSegmented
import com.example.ui.components.DRStepper
import com.example.ui.components.DRTabs
import com.example.ui.components.DRWallet
import com.example.ui.hud.formatRaceTime
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType

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
    var showRaceSetup by remember { mutableStateOf(false) }
    var setupMode by remember { mutableStateOf(GameMode.QUICK_RACE) }
    val selectedTrack = Tracks.byId(profile.selectedTrackId)
    val bots = if (profile.raceWithBots) "${profile.botCount} bots" else "Solo"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_menu_screen")
    ) {
        // Kart showroom is on the left (3D scene behind); darken the right for the menu column.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to DR.Bg.copy(alpha = 0.35f),
                        0.45f to DR.Bg.copy(alpha = 0.10f),
                        0.62f to DR.Bg.copy(alpha = 0.78f),
                        1f to DR.Bg.copy(alpha = 0.96f)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
                .padding(start = 22.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
        ) {
            // Logo
            Column(Modifier.align(Alignment.TopStart)) {
                Text("DESK RACER", style = DRType.Display.copy(fontSize = 34.sp, lineHeight = 34.sp, shadow = com.example.ui.hud.HudTextShadow), color = DR.TextHi)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DRBadge("LAN", DRBadgeKind.AMBER)
                    DRLabel("Season 1 · Paper Cup", color = DR.TextMid)
                }
            }

            // Wallet + settings
            Row(
                Modifier.align(Alignment.TopEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DRWallet(coins = profile.coinsBalance, gems = profile.gemsBalance, onClick = onOpenShop)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(DRShape.Card)
                        .background(DR.S1)
                        .border(1.dp, DR.Line, DRShape.Card)
                        .clickable(role = Role.Button, onClick = onOpenSettings)
                        .testTag("btn_menu_settings")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = DR.TextHi, modifier = Modifier.size(22.dp))
                }
            }

            // Player chip + help, bottom-left over the showroom
            Row(
                Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(DRShape.Card)
                        .background(DR.S1.copy(alpha = 0.92f))
                        .border(1.dp, DR.Line, DRShape.Card)
                        .clickable(role = Role.Button, onClick = onOpenDeskPass)
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(profile.playerName.uppercase(), style = DRType.Label.copy(fontSize = 11.sp), color = DR.TextMid, maxLines = 1)
                    DRBadge("LV ${profile.deskPassLevel}", DRBadgeKind.YOU)
                }
                DRButton(
                    text = "How to play",
                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                    variant = DRButtonVariant.GHOST,
                    onClick = onOpenHowToConnect,
                    testTag = "btn_how_to_connect"
                )
            }

            // Bottom-aligned menu column
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .width(316.dp)
            ) {
                DRLabel("${selectedTrack.name} · $bots · ${profile.selectedCcClass.label}", color = DR.TextMid)
                Spacer(Modifier.height(6.dp))
                DRButton(
                    text = "Quick race",
                    icon = Icons.Rounded.Flag,
                    onClick = onQuickRace,
                    size = DRButtonSize.L,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "btn_menu_quick_race"
                )
                Spacer(Modifier.height(6.dp))
                DRMenuRow(2, Icons.Rounded.Tune, "Race setup", "Track · Grand Prix · Time Trial · Battle", onClick = {
                    setupMode = GameMode.QUICK_RACE
                    showRaceSetup = true
                }, testTag = "btn_open_race_setup")
                DRMenuRow(3, Icons.Rounded.WifiTethering, "Host LAN race", "Start a room on your hotspot", onClick = onHostRace, testTag = "btn_menu_host_race")
                DRMenuRow(4, Icons.Rounded.Wifi, "Join LAN race", "Find rooms on this Wi-Fi", onClick = onJoinRace, testTag = "btn_menu_join_race")
                DRMenuRow(5, Icons.Rounded.DirectionsCar, "Garage", "Body · Paint · Livery · Driver", onClick = onOpenGarage, testTag = "btn_menu_garage")
                DRMenuRow(6, Icons.Rounded.Storefront, "Shop & Desk Pass", "Cosmetics only · Tier ${profile.deskPassLevel}", onClick = onOpenShop, testTag = "btn_menu_shop")
            }
        }
        DRKerbStrip(Modifier.align(Alignment.BottomCenter))

        if (showRaceSetup) {
            RaceSetupScreen(
                profile = profile,
                initialMode = setupMode,
                onUpdateSetup = onUpdateRaceSetup,
                onStart = { mode, cup, trackId ->
                    showRaceSetup = false
                    onStartConfiguredRace(mode, cup, trackId)
                },
                onDismiss = { showRaceSetup = false }
            )
        }
    }
}

private val SETUP_MODES = listOf(GameMode.QUICK_RACE, GameMode.GRAND_PRIX, GameMode.TIME_TRIAL, GameMode.BATTLE_BALLOONS)
private const val BATTLE_TRACK_ID = 6

@Composable
private fun RaceSetupScreen(
    profile: UserProfileAndSettings,
    initialMode: GameMode,
    onUpdateSetup: (Boolean, Int, CpuDifficulty, Int, ItemMode, CcClass, Int, CupType) -> Unit,
    onStart: (GameMode, CupType, Int) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var modeIdx by remember { mutableIntStateOf(SETUP_MODES.indexOf(initialMode).coerceAtLeast(0)) }
    var cup by remember { mutableStateOf(profile.selectedCup) }
    var trackId by remember { mutableIntStateOf(profile.selectedTrackId) }
    var botCount by remember { mutableIntStateOf(if (profile.raceWithBots) profile.botCount else 0) }
    var difficulty by remember { mutableStateOf(profile.cpuDifficulty) }
    var laps by remember { mutableIntStateOf(profile.selectedLaps) }
    var itemMode by remember { mutableStateOf(profile.selectedItemMode) }
    var ccClass by remember { mutableStateOf(profile.selectedCcClass) }
    val mode = SETUP_MODES[modeIdx]

    fun push() = onUpdateSetup(botCount > 0, botCount.coerceAtLeast(1), difficulty, laps, itemMode, ccClass, trackId, cup)

    val shownTrackId = when (mode) {
        GameMode.BATTLE_BALLOONS -> BATTLE_TRACK_ID
        GameMode.GRAND_PRIX -> cup.trackIds.first()
        else -> trackId
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Bg.copy(alpha = 0.96f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("race_setup_dialog")
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                DRScreenHeader(kicker = "Play", title = "Race setup", onBack = onDismiss, backTestTag = "btn_race_setup_back")
                Spacer(Modifier.height(6.dp))
                DRTabs(
                    items = listOf("Quick", "Grand Prix", "Time Trial", "Battle"),
                    selected = modeIdx,
                    onSelect = { modeIdx = it },
                    modifier = Modifier.fillMaxWidth(),
                    testTagPrefix = "setup_mode"
                )
                Spacer(Modifier.height(10.dp))
                if (mode == GameMode.GRAND_PRIX) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CupType.entries.forEach { c ->
                            CupCard(cup = c, selected = c == cup, trophy = profile.cupTrophies[c.id], modifier = Modifier.weight(1f)) {
                                cup = c
                                push()
                            }
                        }
                    }
                } else {
                    TrackHero(
                        track = Tracks.byId(shownTrackId),
                        bestLapMs = profile.bestLapMsByTrack[shownTrackId] ?: 0L,
                        canBrowse = mode != GameMode.BATTLE_BALLOONS,
                        onPrev = {
                            trackId = (trackId - 1 + Tracks.all.size) % Tracks.all.size
                            push()
                        },
                        onNext = {
                            trackId = (trackId + 1) % Tracks.all.size
                            push()
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    if (mode != GameMode.BATTLE_BALLOONS) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Tracks.all.forEach { t ->
                                TrackThumb(t, selected = t.id == trackId, modifier = Modifier.size(width = 58.dp, height = 40.dp)) {
                                    trackId = t.id
                                    push()
                                }
                            }
                        }
                    }
                }
            }

            Column(Modifier.width(326.dp).fillMaxHeight()) {
                DRPanel(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    DRLabel("Race rules", modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    if (mode != GameMode.BATTLE_BALLOONS) {
                        RuleRow("Laps") {
                            val opts = listOf(1, 3, 5)
                            DRSegmented(opts.map { "$it" }, opts.indexOf(laps).coerceAtLeast(1), { laps = opts[it]; push() }, Modifier.width(150.dp), height = 34.dp, testTagPrefix = "setup_laps")
                        }
                    }
                    if (mode != GameMode.TIME_TRIAL) {
                        DRStepper(
                            label = "Bots",
                            valueText = if (botCount == 0) "0" else "$botCount",
                            onDecrement = { botCount = (botCount - 1).coerceAtLeast(0); push() },
                            onIncrement = { botCount = (botCount + 1).coerceAtMost(7); push() },
                            canDecrement = botCount > 0,
                            canIncrement = botCount < 7,
                            testTagPrefix = "setup_bots"
                        )
                        RuleRow("Bot skill") {
                            val opts = CpuDifficulty.entries
                            DRSegmented(listOf("Rookie", "Pro", "Champ", "Mixed"), opts.indexOf(difficulty), { difficulty = opts[it]; push() }, Modifier.width(190.dp), height = 34.dp, testTagPrefix = "setup_skill")
                        }
                        RuleRow("Items") {
                            val opts = ItemMode.entries
                            DRSegmented(listOf("Normal", "Frantic", "Skill", "Off"), opts.indexOf(itemMode), { itemMode = opts[it]; push() }, Modifier.width(190.dp), height = 34.dp, testTagPrefix = "setup_items")
                        }
                    } else {
                        Text("Solo · 3 laps · race your ghost", style = DRType.Body, color = DR.TextMid, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    RuleRow("Engine") {
                        val opts = CcClass.entries
                        DRSegmented(opts.map { it.label }, opts.indexOf(ccClass), { ccClass = opts[it]; push() }, Modifier.width(190.dp), height = 34.dp, testTagPrefix = "setup_cc")
                    }
                }
                Spacer(Modifier.height(10.dp))
                DRButton(
                    text = if (mode == GameMode.GRAND_PRIX) "Start cup" else "Start race",
                    icon = Icons.Rounded.Flag,
                    onClick = {
                        push()
                        onStart(mode, cup, shownTrackId)
                    },
                    size = DRButtonSize.L,
                    modifier = Modifier.align(Alignment.End).width(210.dp),
                    testTag = "btn_race_setup_start"
                )
            }
        }
    }
}

@Composable
private fun RuleRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = DRType.BodyStrong.copy(fontSize = 13.sp), color = DR.TextHi)
        control()
    }
}

@Composable
private fun TrackImage(track: Track, modifier: Modifier, dim: Boolean = false) {
    val context = LocalContext.current
    val bmp = remember(track.previewCard) { AssetRepository.loadUiBitmapStatic(context, track.previewCard)?.asImageBitmap() }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = track.name,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            colorFilter = if (dim) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.5f) }) else null,
            alpha = if (dim) 0.6f else 1f,
            modifier = modifier
        )
    } else {
        Box(modifier.background(DR.S3))
    }
}

@Composable
private fun TrackHero(
    track: Track,
    bestLapMs: Long,
    canBrowse: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(DRShape.Panel)
            .border(1.dp, DR.Line2, DRShape.Panel)
            .testTag("setup_track_${track.id}")
    ) {
        TrackImage(track, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to DR.Bg.copy(alpha = 0.95f))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DRBadge(track.difficultyLabel, DRBadgeKind.AMBER)
                if (bestLapMs > 0L) DRMarkerTag("Your best ${formatRaceTime(bestLapMs)}")
            }
            Text(track.name.uppercase(), style = DRType.Display.copy(fontSize = 34.sp, lineHeight = 34.sp), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(track.subtitle, style = DRType.Caption, color = DR.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = DR.Gold, modifier = Modifier.size(14.dp))
                    Text(formatRaceTime(track.goldTimeMs), style = DRType.NumS, color = DR.TextMid)
                }
            }
        }
        if (canBrowse) {
            HeroArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous track", Modifier.align(Alignment.CenterStart).padding(start = 10.dp), onPrev)
            HeroArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next track", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), onNext)
        }
    }
}

@Composable
private fun HeroArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 40.dp, height = 48.dp)
            .clip(DRShape.Control)
            .background(DR.Bg.copy(alpha = 0.7f))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Icon(icon, contentDescription = desc, tint = DR.TextHi, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun TrackThumb(track: Track, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(DRShape.Control)
            .then(if (selected) Modifier.border(2.dp, DR.Amber, DRShape.Control) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .testTag("setup_thumb_${track.id}")
    ) {
        TrackImage(track, Modifier.fillMaxSize(), dim = !selected)
    }
}

@Composable
private fun CupCard(cup: CupType, selected: Boolean, trophy: Int?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DRShape.Panel)
            .background(if (selected) DR.AmberTint else DR.S1)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) DR.Amber else DR.Line, DRShape.Panel)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp)
            .testTag("setup_cup_${cup.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cup.title, style = DRType.Title, color = DR.TextHi)
                if (trophy != null && trophy in 1..3) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = "Best finish $trophy", tint = DR.podium(trophy), modifier = Modifier.size(18.dp))
                }
            }
            Text("4 races · points decide the cup", style = DRType.Caption, color = DR.TextMid)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cup.trackIds.forEach { id ->
                TrackImage(Tracks.byId(id), Modifier.size(width = 52.dp, height = 36.dp).clip(DRShape.Control))
            }
        }
    }
}
