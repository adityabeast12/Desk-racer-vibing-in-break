package com.example.ios

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.ComposeUIViewController
import com.example.DeskRacerAppRoot
import com.example.audio.SynthAudio
import com.example.data.FakeAdGateway
import com.example.data.FakeBillingGateway
import com.example.data.Prefs
import com.example.drshimInstallResources
import com.example.game.ControlSchemeType
import com.example.game.GameConstants
import com.example.game.GameLoop
import com.example.game.GraphicsQuality
import com.example.game.HardwareInput
import com.example.game.InputSource
import com.example.game.InputState
import com.example.game.RacePhase
import com.example.game.RaceSession
import com.example.net.LanClient
import com.example.net.LanDiscovery
import com.example.net.LanHost
import com.example.net.UdpKartStatePacket
import com.example.render.AssetRepository
import com.example.render.DeviceTier
import com.example.render.GameRenderer
import com.example.render.PerfGovernor
import com.example.render.DeviceProfiler
import com.example.render.RenderBudget
import com.example.ui.theme.MyApplicationTheme
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.abs
import kotlin.math.atan2

/**
 * iOS counterpart of MainActivity: builds the same game objects and wires them the same way,
 * then hosts DeskRacerAppRoot (the shared Compose UI) over the GL view.
 */
object IosGame {
    lateinit var prefs: Prefs
    lateinit var synthAudio: SynthAudio
    val inputState = InputState()
    lateinit var raceSession: RaceSession
    lateinit var gameLoop: GameLoop
    lateinit var lanDiscovery: LanDiscovery
    lateinit var lanHost: LanHost
    lateinit var lanClient: LanClient
    lateinit var hardwareInput: HardwareInput
    val billingGateway = FakeBillingGateway()
    val adGateway = FakeAdGateway()
    var renderer: GameRenderer? = null

    var autoplayTrack = -1
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var deviceTier = DeviceTier.MID
    private var governorCapHz = 0f

    private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue()) { block() }

    fun start() {
        if (started) return
        started = true
        drshimInstallResources()
        val ctx = Context.app
        val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
        fun arg(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        if (arg("-dr_autoplay") == "1") autoplayTrack = arg("-dr_track")?.toIntOrNull() ?: 0

        AssetRepository(ctx).manifest
        prefs = Prefs(ctx)
        if (arg("-dr_skip_intro") == "1") prefs.markHowToPlaySeen()
        val initial = prefs.state.value
        deviceTier = detectTier()

        synthAudio = SynthAudio(ctx).apply {
            soundEnabled = initial.soundEnabled
            hapticsEnabled = initial.hapticsEnabled
        }
        lanDiscovery = LanDiscovery(ctx)

        raceSession = RaceSession(
            synthAudio = synthAudio,
            onNewBestLapRecorded = { trackId, lapMs -> prefs.recordLapTime(trackId, lapMs) },
            onSaveTimeTrialGhost = { ghost -> prefs.saveTimeTrialGhost(ghost) },
            onLoadTimeTrialGhost = { trackId -> prefs.loadTimeTrialGhost(trackId) }
        ).apply {
            onHostEmitReliableEvent = { event -> lanHost.broadcastReliableEvent(event) }
            onLocalClientItemAction = { item, fireBackward -> lanClient.notifyLocalItemActivated(item, fireBackward) }
            onHostRaceResultsReady = { standings -> lanHost.broadcastRaceResults(standings) }
            onLocalPlayerHitHaptic = { synthAudio.vibrateHit() }
        }
        raceSession.setupShowroom(
            playerName = initial.playerName,
            colorHex = GameConstants.KART_COLORS[initial.kartColorIndex],
            bodyType = initial.kartBody,
            trackId = initial.selectedTrackId,
            liveryIndex = initial.kartLiveryIndex,
            paintFinishIndex = initial.paintFinishIndex
        )

        // Network threads hop to the main thread: on iOS the sim, renderer and UI all live there.
        lanHost = LanHost(
            discovery = lanDiscovery,
            onClientDisconnectedDuringRace = { slot, name -> onMain { raceSession.convertSlotToCpu(slot, name) } },
            onRemoteKartUdpUpdate = { pkt, ping -> onMain { raceSession.onHostReceiveClientKartState(pkt, ping) } },
            onClientRejoinedDuringRace = { slot, name -> onMain { raceSession.reclaimSlotForHuman(slot, name) } },
            onClientReliableItemAction = { slot, item, back, ping -> onMain { raceSession.onHostReceiveClientItemAction(slot, item, back, ping) } }
        )
        lanClient = LanClient(
            onCountdownStartedFromHost = { _, _, _, _, _, _, _, _, _, _, _ -> },
            onHostLeftRoom = { reason -> onMain { raceSession.showToast(reason, 1.6f) } },
            onWorldSnapshotReceived = { snap, receiptMs -> onMain { raceSession.onClientReceiveWorldSnapshot(snap, receiptMs) } },
            onReliableEventReceived = { event -> onMain { raceSession.onClientReceiveReliableEvent(event) } },
            onRaceResultsReceived = { standings -> onMain { raceSession.applyHostFinalResults(standings) } },
            onRematchReturnToLobby = {}
        )

        gameLoop = GameLoop(inputState = inputState, raceSession = raceSession, prefs = prefs, dispatcher = Dispatchers.Main).apply {
            onSimTickForNetwork = { snap ->
                if (raceSession.isMultiplayer) {
                    if (raceSession.isHostAuthority) {
                        lanHost.broadcastWorldSnapshotV4(raceSession.buildWorldSnapshotV4(raceId = lanHost.currentRaceId, seq = 0))
                    } else {
                        val k = snap.karts.firstOrNull { it.slotIndex == snap.localSlotIndex }
                        if (k != null) {
                            lanClient.updateItemHeldState(inputState.itemHeldForShield)
                            val edges = UdpKartStatePacket.buildInputEdges(itemPressed = false, itemBackward = false, itemHeld = inputState.itemHeldForShield)
                            lanClient.sendOwnKartStateUdp(
                                UdpKartStatePacket(
                                    seq = 0,
                                    raceId = lanClient.activeRaceId,
                                    slotIndex = k.slotIndex,
                                    raceTimeMs = (snap.elapsedTimeSec * 1000f).toInt(),
                                    x = k.x, y = k.y, z = k.z,
                                    headingRad = k.headingRad,
                                    speed = k.speed,
                                    steer = inputState.steer,
                                    sparkTier = k.sparkTier,
                                    isBoosting = k.isBoosting,
                                    isDrifting = k.isDrifting,
                                    heldItem = k.heldItem,
                                    inputEdges = edges
                                )
                            )
                        }
                    }
                }
            }
            start()
        }

        hardwareInput = HardwareInput(inputState)
        scope.launch {
            while (true) {
                delay(16L)
                hardwareInput.tick(0.016f)
                pollTilt()
            }
        }
        scope.launch { prefs.state.map { it.graphicsQuality }.distinctUntilChanged().collect { configureGraphics() } }
        scope.launch { prefs.state.map { it.fpsMode }.distinctUntilChanged().collect { applyRefreshRate() } }

        if (autoplayTrack >= 0) raceSession.autopilotLocal = true
        startTilt()

        val nc = NSNotificationCenter.defaultCenter
        nc.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ -> onPause() }
        nc.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ -> onResume() }
        UIApplication.sharedApplication.idleTimerDisabled = true
    }

    private fun onPause() {
        synthAudio.onLifecyclePause()
        if (lanClient.isConnected.value) lanClient.setAwayStatus(true)
        if (!raceSession.isMultiplayer && !raceSession.autopilotLocal &&
            (raceSession.phase == RacePhase.RACING || raceSession.phase == RacePhase.COUNTDOWN)
        ) {
            gameLoop.isPausedSolo = true
            gameLoop.lifecyclePauseSignal.value = gameLoop.lifecyclePauseSignal.value + 1
        }
    }

    private fun onResume() {
        synthAudio.onLifecycleResume()
        if (lanClient.isConnected.value) lanClient.setAwayStatus(false)
        calibrateTilt()
        gameLoop.start()
    }

    // ---------------------------------------------------------------- graphics

    private fun detectTier(): DeviceTier {
        val ramGb = NSProcessInfo.processInfo.physicalMemory.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val cores = NSProcessInfo.processInfo.activeProcessorCount.toInt()
        return when {
            ramGb >= 5.5 && cores >= 6 -> DeviceTier.HIGH
            ramGb >= 2.8 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    fun onGlCreated(renderer: GameRenderer) {
        this.renderer = renderer
        renderer.onBudgetChanged = { b, cap ->
            onMain {
                applyRenderBudget(b)
                if (governorCapHz != cap) { governorCapHz = cap; applyRefreshRate() }
                if (prefs.state.value.graphicsQuality == GraphicsQuality.AUTO && renderer.governor != null) {
                    prefs.updateAutoGraphicsLevel(b.level)
                }
            }
        }
        renderer.onGpuDetected = { gpu -> println("DRPERF gpu=$gpu tier=$deviceTier") }
        configureGraphics()
        applyRefreshRate()
    }

    private fun configureGraphics() {
        val r = renderer ?: return
        val q = prefs.state.value.graphicsQuality
        if (q == GraphicsQuality.AUTO) {
            val saved = prefs.state.value.autoGraphicsLevel
            val start = if (saved >= 0) saved else DeviceProfiler.startLevel(deviceTier)
            r.governor = PerfGovernor(start, DeviceProfiler.ceilingLevel(deviceTier))
            r.applyFixedBudget(start)
        } else {
            r.governor = null
            governorCapHz = 0f
            r.applyFixedBudget(q.fixedLevel)
        }
        applyRefreshRate()
    }

    private fun applyRenderBudget(b: RenderBudget) {
        val shortSide = drshim.GlHost.nativeShortSidePx.toFloat().coerceAtLeast(1f)
        drshim.GlHost.renderScale = minOf(b.renderScale, b.maxShortSidePx / shortSide).coerceIn(0.4f, 1f)
    }

    private fun applyRefreshRate() {
        val mode = prefs.state.value.fpsMode
        val panel = UIScreen.mainScreen.maximumFramesPerSecond.toInt().coerceAtLeast(60)
        var hz = when (mode) {
            1 -> 60
            2 -> 30
            else -> panel
        }
        val gov = governorCapHz
        if (gov > 0f) hz = minOf(hz, gov.toInt().coerceAtLeast(30))
        drshim.GlHost.preferredFps = hz
        renderer?.userTargetHz = if (mode == 2) 30f else minOf(panel, if (mode == 1) 60 else panel).toFloat()
        renderer?.minFrameIntervalNs = 0L
    }

    // ---------------------------------------------------------------- tilt

    private val motion = CMMotionManager()
    private var tiltBaseline = 0f
    private var needsTiltBaseline = true
    private var filteredTilt = 0f

    private fun startTilt() {
        if (motion.deviceMotionAvailable) {
            motion.deviceMotionUpdateInterval = 1.0 / 60.0
            motion.startDeviceMotionUpdates()
        }
    }

    fun calibrateTilt() {
        needsTiltBaseline = true
        filteredTilt = 0f
    }

    private fun pollTilt() {
        if (prefs.state.value.controlScheme != ControlSchemeType.TILT_STEER) return
        if (raceSession.phase != RacePhase.RACING && raceSession.phase != RacePhase.COUNTDOWN) return
        if (hardwareInput.source.value != InputSource.TOUCH) return
        val dm = motion.deviceMotion ?: return
        var gx = 0.0
        var gy = 0.0
        dm.gravity.useContents { gx = x; gy = y }
        // steering-wheel roll in the screen plane; device +y points to screen-right in landscape-left
        val s = if (UIApplication.sharedApplication.statusBarOrientation == UIInterfaceOrientationLandscapeRight) -1.0 else 1.0
        val raw = drshim.JMath.toDegrees(atan2(s * gy, abs(gx))).toFloat()
        if (needsTiltBaseline) { tiltBaseline = raw; needsTiltBaseline = false }
        val deg = raw - tiltBaseline
        val a = abs(deg)
        val target = if (a <= GameConstants.TILT_DEADZONE_DEG) 0f else {
            val span = (GameConstants.TILT_FULL_LOCK_DEG - GameConstants.TILT_DEADZONE_DEG).coerceAtLeast(1f)
            val m = ((a - GameConstants.TILT_DEADZONE_DEG) / span).coerceIn(0f, 1f)
            (if (deg < 0f) -m else m) * prefs.state.value.steerSensitivity
        }.coerceIn(-1f, 1f)
        filteredTilt += (target - filteredTilt) * 0.55f
        inputState.updateSteer(filteredTilt.coerceIn(-1f, 1f))
    }
}

@Composable
private fun IosRoot() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        val cfg = remember(w, h) { Configuration().apply { screenWidthDp = w; screenHeightDp = h } }
        SideEffect {
            drshim.Platform.configuration.screenWidthDp = w
            drshim.Platform.configuration.screenHeightDp = h
        }
        val view = remember { View(Context.app) }
        CompositionLocalProvider(
            LocalContext provides Context.app,
            LocalView provides view,
            LocalConfiguration provides cfg
        ) {
            MyApplicationTheme {
                DeskRacerAppRoot(
                    prefs = IosGame.prefs,
                    synthAudio = IosGame.synthAudio,
                    inputState = IosGame.inputState,
                    raceSession = IosGame.raceSession,
                    gameLoop = IosGame.gameLoop,
                    lanDiscovery = IosGame.lanDiscovery,
                    lanHost = IosGame.lanHost,
                    lanClient = IosGame.lanClient,
                    billingGateway = IosGame.billingGateway,
                    adGateway = IosGame.adGateway,
                    onCalibrateTiltBaseline = { IosGame.calibrateTilt() },
                    onUnloadRaceAssets = { IosGame.renderer?.requestUnloadRaceAssets() },
                    onGlSurfaceViewCreated = { _, renderer -> IosGame.onGlCreated(renderer) },
                    autoplayTrackId = IosGame.autoplayTrack,
                    hardwareInput = IosGame.hardwareInput
                )
            }
        }
    }
}

/** Root controller: GL view at the back, the transparent Compose layer on top. */
class DeskRacerRootController(private val compose: UIViewController) : UIViewController(nibName = null, bundle = null) {
    override fun loadView() {
        val v = UIView(frame = UIScreen.mainScreen.bounds)
        v.backgroundColor = UIColor.blackColor
        setView(v)
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(compose)
        val cv = compose.view
        cv.setFrame(view.bounds)
        cv.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        cv.backgroundColor = UIColor.clearColor
        cv.opaque = false
        view.addSubview(cv)
        compose.didMoveToParentViewController(this)
        drshim.GlHost.container = view
    }

    override fun prefersStatusBarHidden(): Boolean = true
}

/** Root view controller for the app shell (exported to Swift by the framework project's Entry.kt). */
fun mainViewController(): UIViewController {
    IosGame.start()
    val compose = ComposeUIViewController(configure = { opaque = false }) { IosRoot() }
    return DeskRacerRootController(compose)
}
