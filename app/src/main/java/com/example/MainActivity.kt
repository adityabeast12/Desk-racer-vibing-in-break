package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.audio.SynthAudio
import com.example.data.FakeAdGateway
import com.example.data.FakeBillingGateway
import com.example.data.Prefs
import com.example.game.ControlSchemeType
import com.example.game.GameConstants
import com.example.game.GameLoop
import com.example.game.GraphicsQuality
import com.example.game.InputState
import com.example.game.RacePhase
import com.example.game.RaceSession
import com.example.net.LanClient
import com.example.net.LanDiscovery
import com.example.net.LanHost
import com.example.net.UdpKartStatePacket
import com.example.render.AssetRepository
import com.example.render.GameRenderer
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var prefs: Prefs
    private lateinit var synthAudio: SynthAudio
    private val inputState = InputState()
    private lateinit var raceSession: RaceSession
    private lateinit var gameLoop: GameLoop
    private lateinit var lanDiscovery: LanDiscovery
    private lateinit var lanHost: LanHost
    private lateinit var lanClient: LanClient
    private val billingGateway = FakeBillingGateway()
    private val adGateway = FakeAdGateway()

    private var sensorManager: SensorManager? = null
    private var gameRotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var glSurfaceViewRef: GLSurfaceView? = null
    private var gameRendererRef: GameRenderer? = null

    // Part G4: Rotation-vector tilt steering with baseline calibration, ±22° full lock, 3° deadzone, 12 Hz low-pass
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var filteredTiltSteer = 0f
    @Volatile
    private var latestRawTiltDeg = 0f
    @Volatile
    private var tiltBaselineDeg = 0f
    @Volatile
    private var needsTiltBaselineCalibration = true

    // Part H2: Thermal & Battery Saver tracking
    private var thermalListener: Any? = null
    private var thermalStepDownApplied = false

    // Part H3: Ring buffer of the last 200 log events for local crash reporting
    private val logRingBuffer = ArrayDeque<String>(200)

    private fun recordDiagnosticLog(msg: String) {
        synchronized(logRingBuffer) {
            if (logRingBuffer.size >= 200) {
                logRingBuffer.removeFirst()
            }
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            logRingBuffer.addLast("[$ts] $msg")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installCrashReportHandler()
        recordDiagnosticLog("MainActivity.onCreate started")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        applyImmersiveStickyBars()

        AssetRepository(applicationContext).manifest

        prefs = Prefs(applicationContext)
        val initialProfile = prefs.state.value
        // v6.2: re-apply the display refresh rate whenever the Frame rate setting changes
        lifecycleScope.launch {
            prefs.state.map { it.fpsMode }.distinctUntilChanged().collect { applyDisplayRefreshRate() }
        }

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
            onHostEmitReliableEvent = { event ->
                if (::lanHost.isInitialized) {
                    lanHost.broadcastReliableEvent(event)
                }
            }
            onLocalClientItemAction = { item, fireBackward ->
                if (::lanClient.isInitialized) {
                    lanClient.notifyLocalItemActivated(item, fireBackward)
                }
            }
            onHostRaceResultsReady = { standings ->
                if (::lanHost.isInitialized) {
                    lanHost.broadcastRaceResults(standings)
                }
            }
            onLocalPlayerHitHaptic = {
                synthAudio.vibrateHit()
                vibrateConnectedGamepads()
            }
        }

        raceSession.setupShowroom(
            playerName = initialProfile.playerName,
            colorHex = GameConstants.KART_COLORS[initialProfile.kartColorIndex],
            bodyType = initialProfile.kartBody,
            trackId = initialProfile.selectedTrackId,
            liveryIndex = initialProfile.kartLiveryIndex,
            paintFinishIndex = initialProfile.paintFinishIndex
        )

        lanHost = LanHost(
            discovery = lanDiscovery,
            onClientDisconnectedDuringRace = { slotIdx, playerName ->
                recordDiagnosticLog("Client slot $slotIdx ($playerName) disconnected during race")
                raceSession.convertSlotToCpu(slotIdx, playerName)
            },
            onRemoteKartUdpUpdate = { pkt, pingMs ->
                raceSession.onHostReceiveClientKartState(pkt, pingMs)
            },
            onClientRejoinedDuringRace = { slotIdx, playerName ->
                recordDiagnosticLog("Client slot $slotIdx ($playerName) rejoined during race")
                raceSession.reclaimSlotForHuman(slotIdx, playerName)
            },
            onClientReliableItemAction = { slotIdx, item, backward, pingMs ->
                raceSession.onHostReceiveClientItemAction(slotIdx, item, backward, pingMs)
            }
        )

        lanClient = LanClient(
            onCountdownStartedFromHost = { _, _, _, _, _, _, _, _, _, _, _ -> },
            onHostLeftRoom = { reason ->
                recordDiagnosticLog("Host left room: $reason")
                raceSession.showToast(reason, 1.6f)
            },
            onWorldSnapshotReceived = { snap, receiptMs ->
                raceSession.onClientReceiveWorldSnapshot(snap, receiptMs)
            },
            onReliableEventReceived = { event ->
                raceSession.onClientReceiveReliableEvent(event)
            },
            onRaceResultsReceived = { standings ->
                raceSession.applyHostFinalResults(standings)
            },
            onRematchReturnToLobby = {}
        )

        gameLoop = GameLoop(
            inputState = inputState,
            raceSession = raceSession,
            prefs = prefs
        ).apply {
            onSimTickForNetwork = { snap ->
                if (raceSession.isMultiplayer) {
                    if (raceSession.isHostAuthority) {
                        val worldSnap = raceSession.buildWorldSnapshotV4(
                            raceId = lanHost.currentRaceId,
                            seq = 0
                        )
                        lanHost.broadcastWorldSnapshotV4(worldSnap)
                    } else {
                        val localKart = snap.karts.firstOrNull { it.slotIndex == snap.localSlotIndex }
                        if (localKart != null) {
                            lanClient.updateItemHeldState(inputState.itemHeldForShield)
                            val edges = UdpKartStatePacket.buildInputEdges(
                                itemPressed = false,
                                itemBackward = false,
                                itemHeld = inputState.itemHeldForShield
                            )
                            lanClient.sendOwnKartStateUdp(
                                UdpKartStatePacket(
                                    seq = 0,
                                    raceId = lanClient.activeRaceId,
                                    slotIndex = localKart.slotIndex,
                                    raceTimeMs = (snap.elapsedTimeSec * 1000f).toInt(),
                                    x = localKart.x,
                                    y = localKart.y,
                                    z = localKart.z,
                                    headingRad = localKart.headingRad,
                                    speed = localKart.speed,
                                    steer = inputState.steer,
                                    sparkTier = localKart.sparkTier,
                                    isBoosting = localKart.isBoosting,
                                    isDrifting = localKart.isDrifting,
                                    heldItem = localKart.heldItem,
                                    inputEdges = edges
                                )
                            )
                        }
                    }
                }
            }
            start()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gameRotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupThermalAndBatteryMonitoring()

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
                    billingGateway = billingGateway,
                    adGateway = adGateway,
                    onCalibrateTiltBaseline = { calibrateTiltBaseline() },
                    onUnloadRaceAssets = { gameRendererRef?.requestUnloadRaceAssets() },
                    onGlSurfaceViewCreated = { glView, renderer ->
                        glSurfaceViewRef = glView
                        gameRendererRef = renderer
                        glView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) { applyDisplayRefreshRate() }
                            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
                        })
                        applyDisplayRefreshRate()
                    }
                )
            }
        }
    }

    private fun installCrashReportHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val reportFile = File(filesDir, "last_crash_report.txt")
                val sb = StringBuilder()
                sb.appendLine("=== DESK RACER LAN v4 CRASH REPORT ===")
                sb.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                sb.appendLine("Thread: ${thread.name}")
                sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                sb.appendLine("\n--- Recent Diagnostics (Last 200 Lines) ---")
                synchronized(logRingBuffer) {
                    for (line in logRingBuffer) {
                        sb.appendLine(line)
                    }
                }
                sb.appendLine("\n--- Stack Trace ---")
                sb.appendLine(sw.toString())
                reportFile.writeText(sb.toString())
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun setupThermalAndBatteryMonitoring() {
        checkBatterySaverStatus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                val isHot = status >= PowerManager.THERMAL_STATUS_MODERATE
                if (isHot) {
                    applyThermalOrBatteryStepDown(showCoolingToast = true)
                    thermalCapHz = 60f
                    applyDisplayRefreshRate()
                } else if (!isBatteryLowAndUnplugged()) {
                    gameLoop.thermalThrottle30Fps = false
                    thermalStepDownApplied = false
                    thermalCapHz = Float.MAX_VALUE
                    applyDisplayRefreshRate()
                }
            }
            thermalListener = listener
            runCatching {
                pm.addThermalStatusListener(mainExecutor, listener)
            }
        }
    }

    private fun isBatteryLowAndUnplugged(): Boolean {
        val intent = runCatching {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        if (level < 0 || scale <= 0) return false
        val pct = (level * 100) / scale
        return pct <= 15 && !isCharging
    }

    private fun checkBatterySaverStatus() {
        if (isBatteryLowAndUnplugged()) {
            applyThermalOrBatteryStepDown(showCoolingToast = false)
        }
    }

    private fun applyThermalOrBatteryStepDown(showCoolingToast: Boolean) {
        gameLoop.thermalThrottle30Fps = true
        if (!thermalStepDownApplied) {
            thermalStepDownApplied = true
            val cur = prefs.state.value
            if (cur.postFxEnabled) {
                prefs.updatePostFxEnabled(false)
            }
            val nextQual = when (cur.graphicsQuality) {
                GraphicsQuality.HIGH -> GraphicsQuality.MEDIUM
                GraphicsQuality.MEDIUM -> GraphicsQuality.LOW
                GraphicsQuality.LOW -> GraphicsQuality.LOW
            }
            if (nextQual != cur.graphicsQuality) {
                prefs.updateGraphicsQuality(nextQual)
            }
            if (showCoolingToast) {
                raceSession.showToast(getString(R.string.toast_cooling_down), 1.6f)
            }
        }
    }

    fun calibrateTiltBaseline() {
        needsTiltBaselineCalibration = true
        filteredTiltSteer = 0f
    }

    private fun vibrateConnectedGamepads() {
        if (!prefs.state.value.hapticsEnabled) return
        runCatching {
            val deviceIds = InputDevice.getDeviceIds()
            for (id in deviceIds) {
                val dev = InputDevice.getDevice(id) ?: continue
                val sources = dev.sources
                val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (!isGamepad) continue
                @Suppress("DEPRECATION")
                val vib = dev.vibrator
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(45L, 180))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(45L)
                    }
                }
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

    /**
     * v6.2: run at the panel's highest refresh rate (90/120/144 Hz) unless the player picked 60/30,
     * or the phone is hot (temporary 60 Hz cap). Android defaults apps to 60 Hz otherwise.
     */
    private fun applyDisplayRefreshRate() {
        if (!::prefs.isInitialized) return
        val mode = prefs.state.value.fpsMode
        val userCap = when (mode) {
            1 -> 60f
            2 -> 60f // 30 fps is paced in the renderer on a 60 Hz display mode
            else -> Float.MAX_VALUE
        }
        val cap = minOf(userCap, thermalCapHz)
        gameRendererRef?.minFrameIntervalNs = if (mode == 2) 33_333_333L else 0L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            @Suppress("DEPRECATION")
            val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) this.display else windowManager.defaultDisplay)
                ?: return
            val cur = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight && it.refreshRate <= cap + 0.5f }
                .maxByOrNull { it.refreshRate }
                ?: return
            targetRefreshHz = best.refreshRate
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = best.modeId
                preferredRefreshRate = best.refreshRate
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                glSurfaceViewRef?.holder?.surface?.takeIf { it.isValid }?.setFrameRate(
                    best.refreshRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                )
            }
            recordDiagnosticLog("Display mode ${best.modeId} @ ${best.refreshRate}Hz (fpsMode=$mode, thermalCap=$thermalCapHz)")
        }
    }

    @Volatile
    private var thermalCapHz: Float = Float.MAX_VALUE

    @Volatile
    var targetRefreshHz: Float = 60f
        private set

    override fun onResume() {
        super.onResume()
        applyImmersiveStickyBars()
        applyDisplayRefreshRate()
        glSurfaceViewRef?.onResume()
        val sensorToUse = gameRotationSensor ?: accelerometer
        sensorToUse?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        calibrateTiltBaseline()
        checkBatterySaverStatus()
        synthAudio.onLifecycleResume()
        if (::lanClient.isInitialized && lanClient.isConnected.value) {
            lanClient.setAwayStatus(false)
        }
        gameLoop.start()
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        synthAudio.onLifecyclePause()
        if (::lanClient.isInitialized && lanClient.isConnected.value) {
            lanClient.setAwayStatus(true)
        }
        if (!raceSession.isMultiplayer &&
            (raceSession.phase == RacePhase.RACING || raceSession.phase == RacePhase.COUNTDOWN)
        ) {
            gameLoop.isPausedSolo = true
        }
        glSurfaceViewRef?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener
            if (pm != null && listener != null) {
                runCatching { pm.removeThermalStatusListener(listener) }
            }
        }
        gameLoop.stop()
        lanHost.stopHosting()
        lanClient.disconnect()
        lanDiscovery.shutdown()
        synthAudio.shutdown()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val profile = prefs.state.value
        if (profile.controlScheme != ControlSchemeType.TILT_STEER) return
        if (raceSession.phase != RacePhase.RACING && raceSession.phase != RacePhase.COUNTDOWN) return

        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        val sign = if (rotation == Surface.ROTATION_270) -1f else 1f

        val rawTiltDeg = if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            Math.toDegrees(orientationAngles[1].toDouble()).toFloat() * -sign
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            (event.values[1] * sign) * 6.0f
        } else {
            return
        }

        latestRawTiltDeg = rawTiltDeg
        if (needsTiltBaselineCalibration) {
            tiltBaselineDeg = rawTiltDeg
            needsTiltBaselineCalibration = false
        }

        val tiltDeg = rawTiltDeg - tiltBaselineDeg
        val absDeg = abs(tiltDeg)
        val rawTarget = if (absDeg <= GameConstants.TILT_DEADZONE_DEG) {
            0f
        } else {
            val span = (GameConstants.TILT_FULL_LOCK_DEG - GameConstants.TILT_DEADZONE_DEG).coerceAtLeast(1f)
            val mapped = ((absDeg - GameConstants.TILT_DEADZONE_DEG) / span).coerceIn(0f, 1f)
            (if (tiltDeg < 0f) -mapped else mapped) * profile.steerSensitivity
        }.coerceIn(-1f, 1f)

        filteredTiltSteer += (rawTarget - filteredTiltSteer) * 0.55f
        inputState.updateSteer(filteredTiltSteer.coerceIn(-1f, 1f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            val steerX = event.getAxisValue(MotionEvent.AXIS_X)
            val steerVal = if (abs(steerX) > 0.12f) steerX.coerceIn(-1f, 1f) else 0f
            inputState.updateSteer(steerVal)

            val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
                .coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_GAS))
            val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
                .coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_BRAKE))
            if (rt > 0.2f || lt > 0.2f) {
                inputState.gasPressed = (rt > 0.25f)
                inputState.updateDrift(lt > 0.25f)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_B -> {
                inputState.updateDrift(true)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_L1 -> {
                inputState.triggerItem(false)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                inputState.updateSteer(-1f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                inputState.updateSteer(1f)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_B -> {
                inputState.updateDrift(false)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                inputState.updateSteer(0f)
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
