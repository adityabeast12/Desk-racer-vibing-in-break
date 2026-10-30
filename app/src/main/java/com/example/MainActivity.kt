package com.example

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.audio.SynthAudio
import com.example.data.Prefs
import com.example.game.CcClass
import com.example.game.ControlSchemeType
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
import com.example.net.UdpKartStatePacket
import com.example.render.GameRenderer
import com.example.ui.GarageScreen
import com.example.ui.HostLobbyScreen
import com.example.ui.HowToPlayOverlay
import com.example.ui.JoinScreen
import com.example.ui.MainMenuScreen
import com.example.ui.RaceScreen
import com.example.ui.ResultsScreen
import com.example.ui.SettingsScreen
import com.example.ui.hud.Toasts
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SlateAbyss
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Splash : Screen
    data object MainMenu : Screen
    data object Garage : Screen
    data object HostLobby : Screen
    data object Join : Screen
    data object Race : Screen
    data object Results : Screen
    data class Settings(val returnToRace: Boolean = false) : Screen
}

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var prefs: Prefs
    private lateinit var synthAudio: SynthAudio
    private val inputState = InputState()
    private lateinit var raceSession: RaceSession
    private lateinit var gameLoop: GameLoop
    private lateinit var lanDiscovery: LanDiscovery
    private lateinit var lanHost: LanHost
    private lateinit var lanClient: LanClient

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var glSurfaceViewRef: GLSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Landscape + Immersive Full Screen + Display Cutout Support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        applyImmersiveStickyBars()

        // 2. Initialize Core Systems (100% offline)
        prefs = Prefs(applicationContext)
        val initialProfile = prefs.state.value

        synthAudio = SynthAudio(applicationContext).apply {
            soundEnabled = initialProfile.soundEnabled
            hapticsEnabled = initialProfile.hapticsEnabled
        }

        lanDiscovery = LanDiscovery(applicationContext)

        raceSession = RaceSession(
            synthAudio = synthAudio,
            onNewBestLapRecorded = { trackId, lapMs ->
                prefs.recordLapTime(trackId, lapMs)
            },
            onSaveTimeTrialGhost = { ghost ->
                prefs.saveTimeTrialGhost(ghost)
            },
            onLoadTimeTrialGhost = { trackId ->
                prefs.loadTimeTrialGhost(trackId)
            }
        ).apply {
            onHostRaceResultsReady = { standings ->
                if (::lanHost.isInitialized) {
                    lanHost.broadcastRaceResults(standings)
                }
            }
        }

        raceSession.setupShowroom(
            playerName = initialProfile.playerName,
            colorHex = GameConstants.KART_COLORS[initialProfile.kartColorIndex],
            bodyType = initialProfile.kartBody,
            trackId = initialProfile.selectedTrackId
        )

        lanHost = LanHost(
            discovery = lanDiscovery,
            onClientDisconnectedDuringRace = { slotIdx, playerName ->
                raceSession.convertSlotToCpu(slotIdx, playerName)
            },
            onRemoteKartUdpUpdate = { pkt ->
                raceSession.applyRemoteKartState(
                    slotIndex = pkt.slotIndex,
                    x = pkt.x,
                    y = pkt.y,
                    z = pkt.z,
                    headingRad = pkt.headingRad,
                    speed = pkt.speed,
                    sparkTier = pkt.sparkTier,
                    isBoosting = pkt.isBoosting
                )
            }
        )

        lanClient = LanClient(
            onCountdownStartedFromHost = { _, _, _, _, _, _ -> },
            onHostLeftRoom = { reason ->
                raceSession.showToast(reason, 2.8f)
            },
            onRemoteSnapshotReceived = { slotIdx, sample ->
                raceSession.applyRemoteKartState(
                    slotIndex = slotIdx,
                    x = sample.x,
                    y = sample.y,
                    z = sample.z,
                    headingRad = sample.headingRad,
                    speed = sample.speed,
                    sparkTier = sample.sparkTier,
                    isBoosting = sample.isBoosting
                )
            },
            onRaceResultsReceived = { standings ->
                raceSession.applyHostFinalResults(standings)
            }
        )

        gameLoop = GameLoop(
            inputState = inputState,
            raceSession = raceSession,
            prefs = prefs
        ).apply {
            onSimTickForNetwork = { snap ->
                if (raceSession.isMultiplayer) {
                    if (raceSession.isHostAuthority) {
                        lanHost.broadcastWorldSnapshotUdp(snap)
                    } else {
                        val localKart = snap.karts.firstOrNull { it.slotIndex == snap.localSlotIndex }
                        if (localKart != null) {
                            lanClient.sendOwnKartStateUdp(
                                UdpKartStatePacket(
                                    seq = 0,
                                    slotIndex = localKart.slotIndex,
                                    raceTimeMs = 0,
                                    x = localKart.x,
                                    y = localKart.y,
                                    z = localKart.z,
                                    headingRad = localKart.headingRad,
                                    speed = localKart.speed,
                                    sparkTier = localKart.sparkTier,
                                    isBoosting = localKart.isBoosting,
                                    heldItem = localKart.heldItem
                                )
                            )
                        }
                    }
                }
            }
            start()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            MyApplicationTheme {
                DeskRacerAppRoot(
                    prefs = prefs,
                    synthAudio = synthAudio,
                    inputState = inputState,
                    raceSession = raceSession,
                    gameLoop = gameLoop,
                    lanDiscovery = lanDiscovery,
                    lanHost = lanHost,
                    lanClient = lanClient,
                    onGlSurfaceViewCreated = { glSurfaceViewRef = it }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveStickyBars()
        }
    }

    private fun applyImmersiveStickyBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveStickyBars()
        glSurfaceViewRef?.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gameLoop.start()
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        synthAudio.isEngineActive = false
        glSurfaceViewRef?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        gameLoop.stop()
        lanHost.stopHosting()
        lanClient.disconnect()
        lanDiscovery.shutdown()
        synthAudio.shutdown()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val profile = prefs.state.value
        if (profile.controlScheme != ControlSchemeType.TILT_STEER) return
        if (raceSession.phase != RacePhase.RACING && raceSession.phase != RacePhase.COUNTDOWN) return

        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        val rawTilt = when (rotation) {
            Surface.ROTATION_270 -> -event.values[1]
            else -> event.values[1]
        }
        val normalizedSteer = ((rawTilt / 4.8f) * profile.steerSensitivity).coerceIn(-1f, 1f)
        inputState.updateSteer(normalizedSteer)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
private fun DeskRacerAppRoot(
    prefs: Prefs,
    synthAudio: SynthAudio,
    inputState: InputState,
    raceSession: RaceSession,
    gameLoop: GameLoop,
    lanDiscovery: LanDiscovery,
    lanHost: LanHost,
    lanClient: LanClient,
    onGlSurfaceViewCreated: (GLSurfaceView) -> Unit
) {
    val context = LocalContext.current
    val profile by prefs.state.collectAsState()
    val hudState by gameLoop.hudState.collectAsState()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var showHowToPlayModal by remember { mutableStateOf(false) }
    var selectedSoloTrackId by remember { mutableIntStateOf(profile.selectedTrackId) }
    var lobbyOpenedAtMs by remember { mutableLongStateOf(0L) }
    var canForceStartHost by remember { mutableStateOf(false) }

    // Keep audio & haptics synced with profile
    LaunchedEffect(profile.soundEnabled, profile.hapticsEnabled) {
        synthAudio.soundEnabled = profile.soundEnabled
        synthAudio.hapticsEnabled = profile.hapticsEnabled
    }

    // 0.8s Splash -> MainMenu + First-run HowToPlay check
    LaunchedEffect(Unit) {
        delay(800L)
        if (currentScreen == Screen.Splash) {
            currentScreen = Screen.MainMenu
            if (!profile.hasSeenHowToPlay) {
                showHowToPlayModal = true
            }
        }
    }

    // Automatically navigate from Race -> Results when RacePhase reaches RESULTS
    LaunchedEffect(hudState.phase, currentScreen) {
        if (currentScreen == Screen.Race && hudState.phase == RacePhase.RESULTS) {
            if (raceSession.gameMode == GameMode.GRAND_PRIX && raceSession.grandPrixRaceIndex >= 3) {
                val localRank = hudState.standings.firstOrNull { it.isLocal }?.position ?: 8
                if (localRank in 1..3) {
                    prefs.recordCupTrophy(raceSession.activeCup.id, localRank)
                }
            }
            currentScreen = Screen.Results
        }
    }

    // Allow host force-start after 10 seconds in HostLobby
    LaunchedEffect(currentScreen, lobbyOpenedAtMs) {
        if (currentScreen == Screen.HostLobby) {
            canForceStartHost = false
            delay(10_000L)
            canForceStartHost = true
        }
    }

    // Wire client race start & host disconnect callbacks onto the active LanClient
    DisposableEffect(lanClient) {
        lanClient.onCountdownStartedFromHost = { trackId, laps, cpuFill, itemMode, assignedSlot, slots ->
            startMultiplayerRaceFromLobby(
                raceSession = raceSession,
                gameLoop = gameLoop,
                prefs = prefs,
                trackId = trackId,
                laps = laps,
                cpuFill = cpuFill,
                itemMode = itemMode,
                isHost = false,
                localSlot = assignedSlot,
                lobbySlots = slots
            )
            currentScreen = Screen.Race
        }
        lanClient.onHostLeftRoom = { reason ->
            raceSession.showToast(reason, 2.8f)
            if (currentScreen == Screen.Join || currentScreen == Screen.Race) {
                raceSession.setupShowroom(
                    playerName = prefs.state.value.playerName,
                    colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                    bodyType = prefs.state.value.kartBody,
                    trackId = selectedSoloTrackId
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
            botCount = effectiveBotCount
        )
        gameLoop.isPausedSolo = false
        inputState.reset()
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Single Persistent Native OpenGL ES 3.0 Renderer Surface
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val supportsEs3 = (am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x30000
                    setEGLContextClientVersion(if (supportsEs3) 3 else 2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    setRenderer(GameRenderer(gameLoop.latestRenderSnapshot, ctx.applicationContext))
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    onGlSurfaceViewCreated(this)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("gl_surface_view")
        )

        // 2. Screen State Machine Overlay
        when (val screen = currentScreen) {
            is Screen.Splash -> {
                SplashScreenOverlay()
            }

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
                            trackId = trackId
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onHostRace = {
                        lanClient.disconnect()
                        lanHost.startHosting(
                            hostName = profile.playerName,
                            hostColorIdx = profile.kartColorIndex,
                            hostBody = profile.kartBody
                        )
                        lobbyOpenedAtMs = System.currentTimeMillis()
                        currentScreen = Screen.HostLobby
                    },
                    onJoinRace = {
                        lanHost.stopHosting()
                        lanDiscovery.startClientDiscovery()
                        currentScreen = Screen.Join
                    },
                    onOpenGarage = {
                        currentScreen = Screen.Garage
                    },
                    onOpenSettings = {
                        currentScreen = Screen.Settings(returnToRace = false)
                    },
                    onOpenHowToConnect = {
                        showHowToPlayModal = true
                    }
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
                            trackId = selectedSoloTrackId
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onColorSelected = { colorIdx ->
                        prefs.updateKartColor(colorIdx)
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[colorIdx],
                            bodyType = prefs.state.value.kartBody,
                            trackId = selectedSoloTrackId
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onBodySelected = { body ->
                        prefs.updateKartBody(body)
                        raceSession.setupShowroom(
                            playerName = prefs.state.value.playerName,
                            colorHex = GameConstants.KART_COLORS[prefs.state.value.kartColorIndex],
                            bodyType = body,
                            trackId = selectedSoloTrackId
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onBack = {
                        currentScreen = Screen.MainMenu
                    }
                )
            }

            is Screen.HostLobby -> {
                val slots by lanHost.slots.collectAsState()
                val trackId by lanHost.trackId.collectAsState()
                val laps by lanHost.laps.collectAsState()
                val cpuFill by lanHost.cpuFill.collectAsState()
                val itemMode by lanHost.itemMode.collectAsState()
                val hostIp: String = remember(slots.size) { NetUtils.detectPrimaryLocalIp() }

                HostLobbyScreen(
                    roomCode = lanHost.roomCode,
                    hostIpAddress = hostIp,
                    slots = slots,
                    selectedTrackId = trackId,
                    selectedLaps = laps,
                    cpuFillEnabled = cpuFill,
                    itemMode = itemMode,
                    canForceStart = canForceStartHost,
                    onSelectTrack = { newTrack ->
                        selectedSoloTrackId = newTrack
                        lanHost.updateTrack(newTrack)
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = newTrack
                        )
                        gameLoop.forceHudRefresh()
                    },
                    onSelectLaps = { lanHost.updateLaps(it) },
                    onToggleCpuFill = { lanHost.updateCpuFill(!cpuFill) },
                    onCycleItemMode = {
                        val entries = ItemMode.entries
                        val next = entries[(itemMode.ordinal + 1) % entries.size]
                        lanHost.updateItemMode(next)
                    },
                    onKickSlot = { lanHost.kickSlot(it) },
                    onStartRace = {
                        lanHost.notifyCountdownStart()
                        startMultiplayerRaceFromLobby(
                            raceSession = raceSession,
                            gameLoop = gameLoop,
                            prefs = prefs,
                            trackId = trackId,
                            laps = laps,
                            cpuFill = cpuFill,
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
                    slots = slots,
                    isClientReady = isReady,
                    onConnectIp = { targetIp ->
                        lanClient.connectToHost(
                            hostIpRaw = targetIp,
                            tcpPort = GameConstants.TCP_CONTROL_PORT,
                            playerName = profile.playerName,
                            colorIndex = profile.kartColorIndex,
                            bodyType = profile.kartBody
                        )
                    },
                    onToggleReady = {
                        lanClient.toggleReady()
                    },
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
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = selectedSoloTrackId
                        )
                        gameLoop.forceHudRefresh()
                        currentScreen = Screen.MainMenu
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
                    onRematch = {
                        prefs.markRocketStartTipSeen()
                        inputState.reset()
                        if (raceSession.isMultiplayer && raceSession.isHostAuthority) {
                            lanHost.notifyCountdownStart()
                            startMultiplayerRaceFromLobby(
                                raceSession = raceSession,
                                gameLoop = gameLoop,
                                prefs = prefs,
                                trackId = lanHost.trackId.value,
                                laps = lanHost.laps.value,
                                cpuFill = lanHost.cpuFill.value,
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
                        val currentProfile = prefs.state.value
                        val racers = buildSoloRacers(
                            playerName = currentProfile.playerName,
                            colorIndex = currentProfile.kartColorIndex,
                            bodyType = currentProfile.kartBody,
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
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = selectedSoloTrackId
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
                        raceSession.setupShowroom(
                            playerName = profile.playerName,
                            colorHex = GameConstants.KART_COLORS[profile.kartColorIndex],
                            bodyType = profile.kartBody,
                            trackId = selectedSoloTrackId
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

        // 3. How To Play Modal Overlay
        AnimatedVisibility(
            visible = showHowToPlayModal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HowToPlayOverlay(
                onDismiss = {
                    prefs.markHowToPlaySeen()
                    showHowToPlayModal = false
                }
            )
        }

        // 4. Global Toast Notifications outside RaceScreen (e.g. Host Disconnected)
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
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E293B), SlateAbyss)
                )
            )
            .testTag("splash_screen")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DESK RACER",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                color = RulerYellow,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "LAN HOTSPOT EDITION",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = DriftAmber,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun buildSoloRacers(
    playerName: String,
    colorIndex: Int,
    bodyType: KartBodyType,
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
            bodyType = bodyType
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
                bodyType = cpuBody
            )
        )
    }
    return list
}

private fun startMultiplayerRaceFromLobby(
    raceSession: RaceSession,
    gameLoop: GameLoop,
    prefs: Prefs,
    trackId: Int,
    laps: Int,
    cpuFill: Boolean,
    itemMode: ItemMode,
    isHost: Boolean,
    localSlot: Int,
    lobbySlots: List<LobbySlotInfo>
) {
    val cpuNames = listOf("NIBBLES", "GRAPHITE", "VOLT", "SKETCH", "TURBO", "APEX", "PIXEL", "BLITZ")
    val racers = ArrayList<KartRuntime>()
    val humanMap = lobbySlots.associateBy { it.slotIndex }

    val maxSlots = if (cpuFill) GameConstants.MAX_RACERS else lobbySlots.size.coerceAtLeast(1)
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
                    bodyType = human.bodyType
                )
            )
        } else if (cpuFill) {
            racers.add(
                KartRuntime(
                    slotIndex = slot,
                    playerName = cpuNames[slot % cpuNames.size],
                    isHuman = false,
                    isLocalPlayer = false,
                    colorHex = GameConstants.KART_COLORS[slot % GameConstants.KART_COLORS.size],
                    bodyType = KartBodyType.fromId(slot % KartBodyType.entries.size)
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
        difficulty = prefs.state.value.cpuDifficulty,
        cc = prefs.state.value.selectedCcClass,
        gameMode = GameMode.MULTIPLAYER,
        multiplayer = true,
        hostAuthority = isHost,
        localSlot = localSlot,
        racerConfigs = racers,
        skipFlyby = true
    )
    gameLoop.forceHudRefresh()
}
