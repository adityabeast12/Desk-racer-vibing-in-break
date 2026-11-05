package com.example

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.audio.SynthAudio
import com.example.data.FakeAdGateway
import com.example.data.FakeBillingGateway
import com.example.data.Prefs
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.CupType
import com.example.game.GameConstants
import com.example.game.GameLoop
import com.example.game.GameMode
import com.example.game.InputState
import com.example.game.ItemMode
import com.example.game.KartBodyType
import com.example.game.KartRuntime
import com.example.game.RacePhase
import com.example.game.RaceSession
import com.example.game.Tracks
import com.example.net.LanClient
import com.example.net.LanDiscovery
import com.example.net.LanHost
import com.example.net.LobbySlotInfo
import com.example.net.NetUtils
import com.example.render.GameRenderer
import com.example.ui.DeskPassScreen
import com.example.ui.GarageScreen
import com.example.ui.HostLobbyScreen
import com.example.ui.HowToPlayOverlay
import com.example.ui.JoinScreen
import com.example.ui.MainMenuScreen
import com.example.ui.RaceScreen
import com.example.ui.ResultsScreen
import com.example.ui.SettingsScreen
import com.example.ui.ShopScreen
import com.example.ui.hud.HudCoverageMetrics
import com.example.ui.hud.Toasts
import com.example.ui.theme.DriftAmber
import com.example.ui.bodyCoinPrice
import com.example.ui.theme.Poppins
import com.example.ui.theme.RulerYellow
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Splash : Screen
    data object MainMenu : Screen
    data object Garage : Screen
    data object Shop : Screen
    data object DeskPass : Screen
    data object HostLobby : Screen
    data object Join : Screen
    data object Race : Screen
    data object Results : Screen
    data class Settings(val returnToRace: Boolean = false) : Screen
}

@Composable
fun DeskRacerAppRoot(
    prefs: Prefs,
    synthAudio: SynthAudio,
    inputState: InputState,
    raceSession: RaceSession,
    gameLoop: GameLoop,
    lanDiscovery: LanDiscovery,
    lanHost: LanHost,
    lanClient: LanClient,
    billingGateway: FakeBillingGateway,
    adGateway: FakeAdGateway,
    onCalibrateTiltBaseline: () -> Unit,
    onUnloadRaceAssets: () -> Unit,
    onGlSurfaceViewCreated: (GLSurfaceView, GameRenderer) -> Unit,
    autoplayTrackId: Int = -1
) {
    val context = LocalContext.current
    val profile by prefs.state.collectAsState()
    val hudState by gameLoop.hudState.collectAsState()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var showHowToPlayModal by remember { mutableStateOf(false) }
    var selectedSoloTrackId by remember { mutableIntStateOf(profile.selectedTrackId) }
    var lobbyOpenedAtMs by remember { mutableLongStateOf(0L) }
    var canForceStartHost by remember { mutableStateOf(false) }
    var lastEarnedRaceCoins by remember { mutableIntStateOf(60) }
    var lastEarnedRaceXp by remember { mutableIntStateOf(100) }
    var latestHudCoverage by remember { mutableStateOf<HudCoverageMetrics?>(null) }

    LaunchedEffect(currentScreen) {
        raceSession.isGarageStageActive = (currentScreen == Screen.Garage)
        if (currentScreen == Screen.Garage) {
            raceSession.updateGarageStageControls(resetView = true)
            gameLoop.forceHudRefresh()
        }
    }

    LaunchedEffect(profile.soundEnabled, profile.hapticsEnabled) {
        synthAudio.soundEnabled = profile.soundEnabled
        synthAudio.hapticsEnabled = profile.hapticsEnabled
    }

    LaunchedEffect(Unit) {
        delay(800L)
        if (currentScreen == Screen.Splash) {
            currentScreen = Screen.MainMenu
            if (!profile.hasSeenHowToPlay && autoplayTrackId < 0) {
                showHowToPlayModal = true
            }
        }
    }

    LaunchedEffect(hudState.phase, currentScreen) {
        if (currentScreen == Screen.Race && hudState.phase == RacePhase.RESULTS) {
            val localStanding = hudState.standings.firstOrNull { it.isLocal }
            val localRank = localStanding?.position ?: 8
            val coinsWon = when (localRank) {
                1 -> 120
                2 -> 90
                3 -> 75
                else -> 50
            } + (hudState.coinsHeld * 3)
            val xpWon = when (localRank) {
                1 -> 160
                2 -> 130
                3 -> 110
                else -> 80
            }
            lastEarnedRaceCoins = coinsWon
            lastEarnedRaceXp = xpWon
            prefs.addCoins(coinsWon)
            prefs.addDeskPassXp(xpWon)
            prefs.incrementCompletedRaces()

            if (raceSession.gameMode == GameMode.GRAND_PRIX && raceSession.grandPrixRaceIndex >= 3) {
                if (localRank in 1..3) {
                    prefs.recordCupTrophy(raceSession.activeCup.id, localRank)
                }
            }
            currentScreen = Screen.Results
        }
    }

    LaunchedEffect(currentScreen, lobbyOpenedAtMs) {
        if (currentScreen == Screen.HostLobby) {
            canForceStartHost = false
            delay(10_000L)
            canForceStartHost = true
        }
    }

    DisposableEffect(lanClient) {
        lanClient.onCountdownStartedFromHost = {
                raceId,
                startDelayMs,
                trackId,
                laps,
                cpuFill,
                botCount,
                botDifficulty,
                ccClass,
                itemMode,
                assignedSlot,
                slots ->
            onCalibrateTiltBaseline()
            startMultiplayerRaceFromLobby(
                raceSession = raceSession,
                gameLoop = gameLoop,
                prefs = prefs,
                raceId = raceId,
                initialCountdownSec = (startDelayMs / 1000f).coerceIn(0.5f, 3.6f),
                trackId = trackId,
                laps = laps,
                cpuFill = cpuFill,
                botCount = botCount,
                botDifficulty = botDifficulty,
                ccClass = ccClass,
                itemMode = itemMode,
                isHost = false,
                localSlot = assignedSlot,
                lobbySlots = slots
            )
            currentScreen = Screen.Race
        }
        lanClient.onRematchReturnToLobby = {
            onUnloadRaceAssets()
            raceSession.setupShowroom(
                playerName = prefs.state.value.playerName,
                colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                bodyType = prefs.state.value.kartBody,
                trackId = lanClient.trackId.value,
                liveryIndex = prefs.state.value.kartLiveryIndex,
                paintFinishIndex = prefs.state.value.paintFinishIndex
            )
            gameLoop.forceHudRefresh()
            currentScreen = Screen.Join
        }
        lanClient.onHostLeftRoom = { reason ->
            raceSession.showToast(reason, 1.6f)
            if (currentScreen == Screen.Join || currentScreen == Screen.Race || currentScreen == Screen.Results) {
                onUnloadRaceAssets()
                raceSession.setupShowroom(
                    playerName = prefs.state.value.playerName,
                    colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                    bodyType = prefs.state.value.kartBody,
                    trackId = selectedSoloTrackId,
                    liveryIndex = prefs.state.value.kartLiveryIndex,
                    paintFinishIndex = prefs.state.value.paintFinishIndex
                )
                gameLoop.forceHudRefresh()
                currentScreen = Screen.MainMenu
            }
        }
        onDispose { }
    }

    fun launchSoloRace(
        mode: GameMode,
        cup: CupType,
        trackId: Int,
        startNewCup: Boolean = false
    ) {
        val currentProfile = prefs.state.value
        selectedSoloTrackId = trackId
        val effectiveBotCount = when {
            mode == GameMode.TIME_TRIAL -> 0
            currentProfile.raceWithBots -> currentProfile.botCount
            else -> 0
        }
        val racers = buildSoloRacers(
            playerName = currentProfile.playerName,
            colorIndex = currentProfile.kartColorIndex,
            bodyType = currentProfile.kartBody,
            liveryIndex = currentProfile.kartLiveryIndex,
            botCount = effectiveBotCount
        )
        gameLoop.isPausedSolo = false
        inputState.reset()
        onCalibrateTiltBaseline()
        if (mode == GameMode.GRAND_PRIX && startNewCup) {
            raceSession.startGrandPrixCup(
                cup = cup,
                laps = currentProfile.selectedLaps,
                mode = currentProfile.selectedItemMode,
                difficulty = currentProfile.cpuDifficulty,
                cc = currentProfile.selectedCcClass,
                localSlot = 0,
                racerConfigs = racers
            )
        } else {
            raceSession.startNewRace(
                trackId = trackId,
                laps = if (mode == GameMode.TIME_TRIAL) 3 else currentProfile.selectedLaps,
                mode = if (mode == GameMode.TIME_TRIAL) ItemMode.OFF else currentProfile.selectedItemMode,
                difficulty = currentProfile.cpuDifficulty,
                cc = currentProfile.selectedCcClass,
                gameMode = mode,
                multiplayer = false,
                hostAuthority = true,
                localSlot = 0,
                racerConfigs = racers,
                skipFlyby = true
            )
        }
        gameLoop.forceHudRefresh()
        currentScreen = Screen.Race
    }

    // QA playtest capture: start a full 8-kart race on the requested track straight after the splash
    LaunchedEffect(autoplayTrackId) {
        if (autoplayTrackId >= 0) {
            delay(2500L)
            launchSoloRace(mode = GameMode.QUICK_RACE, cup = profile.selectedCup, trackId = autoplayTrackId, startNewCup = false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val supportsEs3 = (am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x30000
                    setEGLContextClientVersion(if (supportsEs3) 3 else 2)
                    setEGLConfigChooser(
                        com.example.render.MsaaConfigChooser(
                            es3 = supportsEs3,
                            allowMsaa = prefs.state.value.graphicsQuality != com.example.game.GraphicsQuality.LOW
                        )
                    )
                    val renderer = GameRenderer(gameLoop.latestRenderSnapshot, ctx.applicationContext)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    onGlSurfaceViewCreated(this, renderer)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("gl_surface_view")
        )

        when (val screen = currentScreen) {
            is Screen.Splash -> SplashScreenOverlay()

            is Screen.MainMenu -> {
                MainMenuScreen(
                    profile = profile,
                    onQuickRace = {
                        launchSoloRace(
                            mode = GameMode.QUICK_RACE,
                            cup = profile.selectedCup,
                            trackId = profile.selectedTrackId,
                            startNewCup = false
                        )
                    },
                    onStartConfiguredRace = { mode, cup, trackId ->
                        val startTrack = if (mode == GameMode.GRAND_PRIX) cup.trackIds.first() else trackId
                        launchSoloRace(
                            mode = mode,
                            cup = cup,
                            trackId = startTrack,
                            startNewCup = (mode == GameMode.GRAND_PRIX)
                        )
                    },
                    onUpdateRaceSetup = { raceWithBots, botCount, difficulty, laps, itemMode, ccClass, trackId, cup ->
                        selectedSoloTrackId = trackId
                        prefs.updateRaceWithBots(raceWithBots)
                        prefs.updateBotCount(botCount)
                        prefs.updateCpuDifficulty(difficulty)
                        prefs.updateSelectedLaps(laps)
                        prefs.updateSelectedItemMode(itemMode)
                        prefs.updateSelectedCcClass(ccClass)
                        prefs.updateSelectedTrackId(trackId)
                        prefs.updateSelectedCup(cup)
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = trackId,
                            liveryIndex = profile.kartLiveryIndex,
                            paintFinishIndex = profile.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onHostRace = {
                        lanClient.disconnect()
                        lanHost.startHosting(
                            hostName = profile.playerName,
                            hostColorIdx = profile.kartColorIndex,
                            hostBody = profile.kartBody,
                            hostLiveryIdx = profile.kartLiveryIndex,
                            hostPlayerId = profile.playerId
                        )
                        lobbyOpenedAtMs = System.currentTimeMillis()
                        currentScreen = Screen.HostLobby
                    },
                    onJoinRace = {
                        lanHost.stopHosting()
                        lanDiscovery.startClientDiscovery()
                        currentScreen = Screen.Join
                    },
                    onOpenGarage = { currentScreen = Screen.Garage },
                    onOpenShop = { currentScreen = Screen.Shop },
                    onOpenDeskPass = { currentScreen = Screen.DeskPass },
                    onOpenSettings = { currentScreen = Screen.Settings(returnToRace = false) },
                    onOpenHowToConnect = { showHowToPlayModal = true }
                )
            }

            is Screen.Garage -> {
                GarageScreen(
                    profile = profile,
                    onNameChanged = { newName ->
                        prefs.updatePlayerName(newName)
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                            bodyType = prefs.state.value.kartBody,
                            trackId = selectedSoloTrackId,
                            liveryIndex = prefs.state.value.kartLiveryIndex,
                            paintFinishIndex = prefs.state.value.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onColorSelected = { colorIdx ->
                        prefs.updateKartColor(colorIdx)
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[colorIdx],
                            bodyType = prefs.state.value.kartBody,
                            trackId = selectedSoloTrackId,
                            liveryIndex = prefs.state.value.kartLiveryIndex,
                            paintFinishIndex = prefs.state.value.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onPaintFinishSelected = { finishIdx ->
                        prefs.updatePaintFinish(finishIdx)
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                            bodyType = prefs.state.value.kartBody,
                            trackId = selectedSoloTrackId,
                            liveryIndex = prefs.state.value.kartLiveryIndex,
                            paintFinishIndex = finishIdx
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onBodySelected = { body ->
                        prefs.updateKartBody(body)
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                            bodyType = body,
                            trackId = selectedSoloTrackId,
                            liveryIndex = prefs.state.value.kartLiveryIndex,
                            paintFinishIndex = prefs.state.value.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onLiverySelected = { liveryIdx ->
                        prefs.updateKartLivery(liveryIdx)
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                            bodyType = prefs.state.value.kartBody,
                            trackId = selectedSoloTrackId,
                            liveryIndex = liveryIdx,
                            paintFinishIndex = prefs.state.value.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onRotateTurntable = { yawDeltaRad, flingVelRadPerSec ->
                        raceSession.updateGarageStageControls(
                            yawDeltaRad = yawDeltaRad,
                            flingVelocityRadPerSec = flingVelRadPerSec
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onPinchZoomTurntable = { zoomMult ->
                        raceSession.updateGarageStageControls(zoomScaleMultiplier = zoomMult)
                        gameLoop.forceHudRefresh()
                    },
                    onResetTurntable = {
                        raceSession.updateGarageStageControls(resetView = true)
                        gameLoop.forceHudRefresh()
                    },
                    onPreviewLoadout = { body, colorIdx, liveryIdx, finishIdx ->
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[colorIdx],
                            bodyType = body,
                            trackId = selectedSoloTrackId,
                            liveryIndex = liveryIdx,
                            paintFinishIndex = finishIdx
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onBuyBody = { body ->
                        prefs.unlockShopItem("body_${body.id}", bodyCoinPrice(body), 0)
                    },
                    onBack = { currentScreen = Screen.MainMenu }
                )
            }

            is Screen.Shop -> {
                ShopScreen(
                    profile = profile,
                    prefs = prefs,
                    billingGateway = billingGateway,
                    adGateway = adGateway,
                    onBack = { currentScreen = Screen.MainMenu }
                )
            }

            is Screen.DeskPass -> {
                DeskPassScreen(
                    profile = profile,
                    prefs = prefs,
                    billingGateway = billingGateway,
                    onBack = { currentScreen = Screen.MainMenu }
                )
            }

            is Screen.HostLobby -> {
                val slots by lanHost.slots.collectAsState()
                val trackId by lanHost.trackId.collectAsState()
                val laps by lanHost.laps.collectAsState()
                val cpuFill by lanHost.cpuFill.collectAsState()
                val botCount by lanHost.botCount.collectAsState()
                val botDifficulty by lanHost.botDifficulty.collectAsState()
                val ccClass by lanHost.ccClass.collectAsState()
                val itemMode by lanHost.itemMode.collectAsState()
                val hostIp: String = remember(slots.size) { NetUtils.detectPrimaryLocalIp() }

                HostLobbyScreen(
                    roomCode = lanHost.roomCode,
                    hostIpAddress = hostIp,
                    slots = slots,
                    selectedTrackId = trackId,
                    selectedLaps = laps,
                    cpuFillEnabled = cpuFill,
                    botCount = botCount,
                    botDifficulty = botDifficulty,
                    ccClass = ccClass,
                    itemMode = itemMode,
                    canForceStart = canForceStartHost,
                    onSelectTrack = { newTrack ->
                        selectedSoloTrackId = newTrack
                        lanHost.updateTrack(newTrack)
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = newTrack,
                            liveryIndex = profile.kartLiveryIndex,
                            paintFinishIndex = profile.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onSelectLaps = { lanHost.updateLaps(it) },
                    onToggleCpuFill = { lanHost.updateCpuFill(!cpuFill) },
                    onCycleBotCount = {
                        val nextCount = if (botCount >= 7) 1 else botCount + 1
                        lanHost.updateBotCount(nextCount)
                    },
                    onCycleBotDifficulty = {
                        val entries = CpuDifficulty.entries
                        val next = entries[(botDifficulty.ordinal + 1) % entries.size]
                        lanHost.updateBotDifficulty(next)
                    },
                    onCycleCcClass = {
                        val entries = CcClass.entries
                        val next = entries[(ccClass.ordinal + 1) % entries.size]
                        lanHost.updateCcClass(next)
                    },
                    onCycleItemMode = {
                        val entries = ItemMode.entries
                        val next = entries[(itemMode.ordinal + 1) % entries.size]
                        lanHost.updateItemMode(next)
                    },
                    onKickSlot = { lanHost.kickSlot(it) },
                    onStartRace = {
                        onCalibrateTiltBaseline()
                        val raceId = lanHost.notifyCountdownStart()
                        startMultiplayerRaceFromLobby(
                            raceSession = raceSession,
                            gameLoop = gameLoop,
                            prefs = prefs,
                            raceId = raceId,
                            initialCountdownSec = 3.2f,
                            trackId = trackId,
                            laps = laps,
                            cpuFill = cpuFill,
                            botCount = botCount,
                            botDifficulty = botDifficulty,
                            ccClass = ccClass,
                            itemMode = itemMode,
                            isHost = true,
                            localSlot = 0,
                            lobbySlots = slots
                        )
                        currentScreen = Screen.Race
                    },
                    onBack = {
                        lanHost.stopHosting()
                        currentScreen = Screen.MainMenu
                    }
                )
            }

            is Screen.Join -> {
                val discoveredRooms by lanDiscovery.discoveredRooms.collectAsState()
                val statusMsg by lanClient.statusMessage.collectAsState()
                val isConnected by lanClient.isConnected.collectAsState()
                val roomCode by lanClient.roomCode.collectAsState()
                val trackId by lanClient.trackId.collectAsState()
                val laps by lanClient.laps.collectAsState()
                val cpuFill by lanClient.cpuFill.collectAsState()
                val botCount by lanClient.botCount.collectAsState()
                val botDifficulty by lanClient.botDifficulty.collectAsState()
                val ccClass by lanClient.ccClass.collectAsState()
                val itemMode by lanClient.itemMode.collectAsState()
                val slots by lanClient.slots.collectAsState()
                val isReady by lanClient.isReady.collectAsState()
                val defaultGateway: String = remember { NetUtils.detectWifiGatewayIp(context) }

                JoinScreen(
                    discoveredRooms = discoveredRooms,
                    defaultGatewayIp = defaultGateway,
                    statusMessage = statusMsg,
                    isConnectedToLobby = isConnected,
                    roomCode = roomCode,
                    trackId = trackId,
                    laps = laps,
                    itemMode = itemMode,
                    ccClass = ccClass,
                    cpuFill = cpuFill,
                    botCount = botCount,
                    botDifficulty = botDifficulty,
                    slots = slots,
                    isClientReady = isReady,
                    onConnectIp = { targetIp ->
                        lanClient.connectToHost(
                            hostIpRaw = targetIp,
                            tcpPort = GameConstants.TCP_CONTROL_PORT,
                            playerName = profile.playerName,
                            colorIndex = profile.kartColorIndex,
                            bodyType = profile.kartBody,
                            liveryIndex = profile.kartLiveryIndex,
                            playerId = profile.playerId
                        )
                    },
                    onToggleReady = { lanClient.toggleReady() },
                    onBack = {
                        lanDiscovery.stopClientDiscovery()
                        lanClient.disconnect()
                        currentScreen = Screen.MainMenu
                    }
                )
            }

            is Screen.Race -> {
                RaceScreen(
                    hudState = hudState,
                    profile = profile,
                    inputState = inputState,
                    isMultiplayer = raceSession.isMultiplayer,
                    isHost = raceSession.isHostAuthority,
                    onSkipFlyby = {
                        raceSession.skipFlybyIntro()
                        gameLoop.forceHudRefresh()
                    },
                    onPauseChanged = { paused ->
                        if (!raceSession.isMultiplayer) {
                            gameLoop.isPausedSolo = paused
                            synthAudio.isEngineActive = !paused
                        }
                        if (!paused) {
                            onCalibrateTiltBaseline()
                        }
                    },
                    onRestartRace = {
                        prefs.markRocketStartTipSeen()
                        launchSoloRace(
                            mode = raceSession.gameMode,
                            cup = raceSession.activeCup,
                            trackId = raceSession.currentTrack.id,
                            startNewCup = false
                        )
                    },
                    onOpenSettings = {
                        currentScreen = Screen.Settings(returnToRace = true)
                    },
                    onQuitToMenu = {
                        prefs.markRocketStartTipSeen()
                        gameLoop.isPausedSolo = false
                        inputState.reset()
                        lanHost.stopHosting()
                        lanClient.disconnect()
                        onUnloadRaceAssets()
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = selectedSoloTrackId,
                            liveryIndex = profile.kartLiveryIndex,
                            paintFinishIndex = profile.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                        currentScreen = Screen.MainMenu
                    },
                    onHudCoverageMeasured = { metrics ->
                        latestHudCoverage = metrics
                    }
                )
            }

            is Screen.Results -> {
                ResultsScreen(
                    trackName = hudState.trackName,
                    standings = hudState.standings,
                    isMultiplayer = raceSession.isMultiplayer,
                    isHost = raceSession.isHostAuthority,
                    gameMode = raceSession.gameMode,
                    cupRaceIndex = raceSession.grandPrixRaceIndex,
                    coinsEarnedThisRace = lastEarnedRaceCoins,
                    xpEarnedThisRace = lastEarnedRaceXp,
                    onWatchAdDoubleCoins = if (!raceSession.isMultiplayer) {
                        {
                            adGateway.showRewardedAd(
                                placement = "results_double_coins",
                                onRewardEarned = { prefs.addCoins(lastEarnedRaceCoins) },
                                onDismissed = { msg -> raceSession.showToast(msg, 1.6f) }
                            )
                        }
                    } else null,
                    onRematch = {
                        prefs.markRocketStartTipSeen()
                        inputState.reset()
                        onCalibrateTiltBaseline()
                        if (raceSession.isMultiplayer && raceSession.isHostAuthority) {
                            val raceId = lanHost.notifyCountdownStart()
                            startMultiplayerRaceFromLobby(
                                raceSession = raceSession,
                                gameLoop = gameLoop,
                                prefs = prefs,
                                raceId = raceId,
                                initialCountdownSec = 3.2f,
                                trackId = lanHost.trackId.value,
                                laps = lanHost.laps.value,
                                cpuFill = lanHost.cpuFill.value,
                                botCount = lanHost.botCount.value,
                                botDifficulty = lanHost.botDifficulty.value,
                                ccClass = lanHost.ccClass.value,
                                itemMode = lanHost.itemMode.value,
                                isHost = true,
                                localSlot = 0,
                                lobbySlots = lanHost.slots.value
                            )
                            currentScreen = Screen.Race
                        } else {
                            launchSoloRace(
                                mode = raceSession.gameMode,
                                cup = raceSession.activeCup,
                                trackId = raceSession.currentTrack.id,
                                startNewCup = false
                            )
                        }
                    },
                    onNextRace = {
                        prefs.markRocketStartTipSeen()
                        inputState.reset()
                        onCalibrateTiltBaseline()
                        val currentProfile = prefs.state.value
                        val racers = buildSoloRacers(
                            playerName = currentProfile.playerName,
                            colorIndex = currentProfile.kartColorIndex,
                            bodyType = currentProfile.kartBody,
                            liveryIndex = currentProfile.kartLiveryIndex,
                            botCount = if (currentProfile.raceWithBots) currentProfile.botCount else 0
                        )
                        if (raceSession.gameMode == GameMode.GRAND_PRIX) {
                            val advanced = raceSession.advanceGrandPrixNextRace(racers, localSlot = 0)
                            if (!advanced) {
                                raceSession.startGrandPrixCup(
                                    cup = raceSession.activeCup,
                                    laps = currentProfile.selectedLaps,
                                    mode = currentProfile.selectedItemMode,
                                    difficulty = currentProfile.cpuDifficulty,
                                    cc = currentProfile.selectedCcClass,
                                    localSlot = 0,
                                    racerConfigs = racers
                                )
                            }
                            gameLoop.forceHudRefresh()
                            currentScreen = Screen.Race
                        } else {
                            val nextTrack = (raceSession.currentTrack.id + 1) % Tracks.all.size
                            prefs.updateSelectedTrackId(nextTrack)
                            launchSoloRace(
                                mode = raceSession.gameMode,
                                cup = raceSession.activeCup,
                                trackId = nextTrack,
                                startNewCup = false
                            )
                        }
                    },
                    onBackToLobby = {
                        onUnloadRaceAssets()
                        if (raceSession.isMultiplayer && raceSession.isHostAuthority) {
                            lanHost.notifyRematchToLobby()
                        }
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = selectedSoloTrackId,
                            liveryIndex = profile.kartLiveryIndex,
                            paintFinishIndex = profile.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                        currentScreen = if (raceSession.isHostAuthority) {
                            Screen.HostLobby
                        } else {
                            Screen.Join
                        }
                    },
                    onMainMenu = {
                        prefs.markRocketStartTipSeen()
                        lanHost.stopHosting()
                        lanClient.disconnect()
                        onUnloadRaceAssets()
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = selectedSoloTrackId,
                            liveryIndex = profile.kartLiveryIndex,
                            paintFinishIndex = profile.paintFinishIndex
                        )
                        gameLoop.forceHudRefresh()
                        currentScreen = Screen.MainMenu
                    }
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    settings = profile,
                    onControlSchemeChanged = { prefs.updateControlScheme(it) },
                    onSensitivityChanged = { prefs.updateSteerSensitivity(it) },
                    onAutoAccelerateChanged = { prefs.updateAutoAccelerate(it) },
                    onSmartSteeringChanged = { prefs.updateSmartSteering(it) },
                    onShowMinimapChanged = { prefs.updateShowMinimap(it) },
                    onSoundChanged = { prefs.updateSoundEnabled(it) },
                    onHapticsChanged = { prefs.updateHapticsEnabled(it) },
                    onGraphicsChanged = { prefs.updateGraphicsQuality(it) },
                    onDifficultyChanged = { prefs.updateCpuDifficulty(it) },
                    onSwapHandsChanged = { prefs.updateSwapHands(it) },
                    onButtonScaleChanged = { prefs.updateButtonScale(it) },
                    onHudScaleChanged = { prefs.updateHudScale(it) },
                    onHudOpacityChanged = { prefs.updateHudOpacity(it) },
                    onPostFxChanged = { prefs.updatePostFxEnabled(it) },
                    onContactShadowsChanged = { prefs.updateContactShadowsEnabled(it) },
                    onShowDebugHudZonesChanged = { prefs.updateShowDebugHudZones(it) },
                    onFpsModeChanged = { prefs.updateFpsMode(it) },
                    onLaunchTestTrackLoop = {
                        selectedSoloTrackId = 0
                        val soloKart = buildSoloRacers(
                            playerName = profile.playerName,
                            colorIndex = profile.kartColorIndex,
                            bodyType = profile.kartBody,
                            liveryIndex = profile.kartLiveryIndex,
                            botCount = 0
                        )
                        gameLoop.isPausedSolo = false
                        inputState.reset()
                        onCalibrateTiltBaseline()
                        raceSession.startNewRace(
                            trackId = 0,
                            laps = 3,
                            mode = ItemMode.NORMAL,
                            difficulty = profile.cpuDifficulty,
                            cc = profile.selectedCcClass,
                            gameMode = GameMode.TIME_TRIAL,
                            multiplayer = false,
                            hostAuthority = true,
                            localSlot = 0,
                            racerConfigs = soloKart,
                            skipFlyby = true
                        )
                        gameLoop.forceHudRefresh()
                        currentScreen = Screen.Race
                    },
                    latestHudCoverage = latestHudCoverage,
                    onBack = {
                        currentScreen = if (screen.returnToRace) {
                            Screen.Race
                        } else {
                            Screen.MainMenu
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showHowToPlayModal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HowToPlayOverlay(
                selectedScheme = profile.controlScheme,
                onSelectScheme = { prefs.updateControlScheme(it) },
                onDismiss = {
                    prefs.markHowToPlaySeen()
                    showHowToPlayModal = false
                }
            )
        }

        val toastMsg = hudState.toastText
        if (currentScreen != Screen.Race && toastMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Toasts(message = toastMsg)
            }
        }
    }
}

@Composable
private fun SplashScreenOverlay() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.DR.Bg)
            .testTag("splash_screen")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.splash_title).uppercase(),
                style = com.example.ui.theme.DRType.Display.copy(fontSize = 44.sp, lineHeight = 44.sp),
                color = com.example.ui.theme.DR.TextHi
            )
            Spacer(modifier = Modifier.height(8.dp))
            com.example.ui.components.DRKerbStrip(Modifier.width(160.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_subtitle).uppercase(),
                style = com.example.ui.theme.DRType.Label,
                color = com.example.ui.theme.DR.TextMid
            )
        }
    }
}

private fun buildSoloRacers(
    playerName: String,
    colorIndex: Int,
    bodyType: KartBodyType,
    liveryIndex: Int = 0,
    botCount: Int = 7
): List<KartRuntime> {
    val cpuNames = listOf("NIBBLES", "GRAPHITE", "VOLT", "SKETCH", "TURBO", "APEX", "PIXEL")
    val totalCount = (1 + botCount).coerceIn(1, GameConstants.MAX_RACERS)
    val list = ArrayList<KartRuntime>(totalCount)
    list.add(
        KartRuntime(
            slotIndex = 0,
            playerName = playerName,
            isHuman = true,
            isLocalPlayer = true,
            colorHex = GameConstants.KART_COLORS[colorIndex % GameConstants.KART_COLORS.size],
            bodyType = bodyType,
            liveryIndex = liveryIndex
        )
    )
    for (slot in 1 until totalCount) {
        val cpuColorIdx = (colorIndex + slot) % GameConstants.KART_COLORS.size
        val cpuBody = KartBodyType.fromId(slot % KartBodyType.entries.size)
        list.add(
            KartRuntime(
                slotIndex = slot,
                playerName = cpuNames[(slot - 1) % cpuNames.size],
                isHuman = false,
                isLocalPlayer = false,
                colorHex = GameConstants.KART_COLORS[cpuColorIdx],
                bodyType = cpuBody,
                liveryIndex = slot % 4
            )
        )
    }
    return list
}

private fun startMultiplayerRaceFromLobby(
    raceSession: RaceSession,
    gameLoop: GameLoop,
    prefs: Prefs,
    raceId: Int,
    initialCountdownSec: Float,
    trackId: Int,
    laps: Int,
    cpuFill: Boolean,
    botCount: Int,
    botDifficulty: CpuDifficulty,
    ccClass: CcClass,
    itemMode: ItemMode,
    isHost: Boolean,
    localSlot: Int,
    lobbySlots: List<LobbySlotInfo>
) {
    val cpuNames = listOf("NIBBLES", "GRAPHITE", "VOLT", "SKETCH", "TURBO", "APEX", "PIXEL", "BLITZ")
    val racers = ArrayList<KartRuntime>()
    val humanMap = lobbySlots.associateBy { it.slotIndex }

    val targetTotalRacers = if (cpuFill) {
        (lobbySlots.size + botCount).coerceIn(lobbySlots.size.coerceAtLeast(1), GameConstants.MAX_RACERS)
    } else {
        lobbySlots.size.coerceAtLeast(1)
    }
    val highestHumanSlot = lobbySlots.maxOfOrNull { it.slotIndex } ?: 0
    val maxSlots = maxOf(targetTotalRacers, highestHumanSlot + 1).coerceAtMost(GameConstants.MAX_RACERS)

    var botsAdded = 0
    for (slot in 0 until maxSlots) {
        val human = humanMap[slot]
        if (human != null) {
            racers.add(
                KartRuntime(
                    slotIndex = slot,
                    playerName = human.playerName,
                    isHuman = true,
                    isLocalPlayer = (slot == localSlot),
                    colorHex = GameConstants.KART_COLORS[human.colorIndex % GameConstants.KART_COLORS.size],
                    bodyType = human.bodyType,
                    liveryIndex = human.liveryIndex
                )
            )
        } else if (cpuFill && botsAdded < botCount) {
            botsAdded++
            racers.add(
                KartRuntime(
                    slotIndex = slot,
                    playerName = cpuNames[slot % cpuNames.size],
                    isHuman = false,
                    isLocalPlayer = false,
                    colorHex = GameConstants.KART_COLORS[slot % GameConstants.KART_COLORS.size],
                    bodyType = KartBodyType.fromId(slot % KartBodyType.entries.size),
                    liveryIndex = slot % 4
                )
            )
        }
    }

    gameLoop.isPausedSolo = false
    gameLoop.inputState.reset()
    raceSession.startNewRace(
        trackId = trackId,
        laps = laps,
        mode = itemMode,
        difficulty = botDifficulty,
        cc = ccClass,
        gameMode = GameMode.MULTIPLAYER,
        multiplayer = true,
        hostAuthority = isHost,
        localSlot = localSlot,
        racerConfigs = racers,
        skipFlyby = true,
        raceId = raceId,
        initialCountdownSec = initialCountdownSec
    )
    gameLoop.forceHudRefresh()
}
