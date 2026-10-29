package com.example.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.HotspotLanManager
import com.example.network.RemoteMarbleEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class ControlScheme(val label: String) {
    ONE_THUMB_WHEEL("ONE-THUMB WHEEL (AUTO-GAS)"),
    CLASSIC_PEDALS("CLASSIC TWIN PEDALS")
}

data class DeskRacerUiState(
    val localKart: KartState = KartState(
        id = "racer-${Random.nextInt(100, 999)}",
        name = "YOU",
        x = 0f,
        y = 0f,
        z = 18f,
        colorHex = 0xFFFF2A5F
    ),
    val aiRivalKart: KartState = KartState(
        id = "ai-rival",
        name = "RIVAL BOT",
        x = 3.2f,
        y = 0f,
        z = 18f,
        colorHex = 0xFF00F5D4
    ),
    val aiRivalEnabled: Boolean = true,
    val remoteKarts: Map<String, KartState> = emptyMap(),
    val controls: KartControlInput = KartControlInput(),
    val steerAxis: Float = 0f, // -1f (full right) to +1f (full left)
    val autoGasEnabled: Boolean = true,
    val controlScheme: ControlScheme = ControlScheme.ONE_THUMB_WHEEL,
    val books: List<BookObstacle> = BlueprintArchiveData.defaultBooks,
    val rulerRamp: RulerRamp = RulerRamp(),
    val itemBoxes: List<ItemBoxEntity> = (0 until 6).map { i ->
        val angle = (i / 6f) * (PI.toFloat() * 2f)
        ItemBoxEntity(
            index = i,
            x = cos(angle) * 20f,
            y = 1.2f,
            z = sin(angle) * 20f
        )
    },
    val marbles: List<MarbleProjectile> = emptyList(),
    val sparks: List<SparkParticle> = emptyList(),
    val cameraMode: CameraMode = CameraMode.CHASE,
    val cameraX: Float = 0f,
    val cameraY: Float = 5.4f,
    val cameraZ: Float = 27.5f,
    val cameraLookX: Float = 0f,
    val cameraLookY: Float = 0.6f,
    val cameraLookZ: Float = 14f,
    val roomInput: String = "KART1",
    val hostIpInput: String = "192.168.43.1",
    val lanRole: LanRole = LanRole.STANDALONE,
    val lanStatus: String = "Hotspot Ready • Zero-Internet LAN",
    val localDeviceIp: String = "192.168.43.1",
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val isLobbyExpanded: Boolean = false,
    val isBlueprintSheetOpen: Boolean = false,
    val useWebGlEngine: Boolean = false,
    val hudBannerMessage: String? = null
) {
    val speedKmh: Int
        get() = (abs(localKart.speed) * 155f).roundToInt()

    val currentPlace: Int
        get() {
            if (!aiRivalEnabled && remoteKarts.isEmpty()) return 1
            var place = 1
            if (aiRivalEnabled) {
                if (aiRivalKart.lap > localKart.lap ||
                    (aiRivalKart.lap == localKart.lap && aiRivalKart.z < localKart.z)
                ) {
                    place++
                }
            }
            for ((_, peer) in remoteKarts) {
                if (peer.lap > localKart.lap || (peer.lap == localKart.lap && peer.z < localKart.z)) {
                    place++
                }
            }
            return place
        }

    val speedDisplayStatus: String
        get() = when {
            localKart.spinOutFrames > 0 -> "💥 MARBLE HIT! SPINNING..."
            localKart.boostFrames > 0 -> "🚀 TURBO BOOST! $speedKmh KM/H"
            controls.drift && (abs(steerAxis) > 0.12f || controls.left || controls.right) -> {
                if (localKart.driftCharge > 35) "🔥 ULTRA SPARK READY! RELEASE!"
                else if (localKart.driftCharge > 16) "⚡ MINI-TURBO READY! (${(localKart.driftCharge * 100 / 36).coerceAtMost(99)}%)"
                else "⚡ CHARGING DRIFT..."
            }
            localKart.y > 0.25f -> "✈️ RULER JUMP AIRTIME!"
            autoGasEnabled -> "AUTO-GAS • SLIDE WHEEL TO DRIFT"
            else -> "SPEED: $speedKmh KM/H"
        }
}

class DeskRacerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DeskRacerUiState())
    val uiState: StateFlow<DeskRacerUiState> = _uiState.asStateFlow()

    private val lanManager = HotspotLanManager(
        onRemoteMarbleFired = { remoteMarble ->
            spawnRemoteMarble(remoteMarble)
        }
    )

    private val aiWaypoints = listOf(
        Pair(-8f, -12f),  // Hit yellow ruler jump ramp
        Pair(0f, -24f),   // Behind north blue book
        Pair(22f, -8f),   // Outside east red book
        Pair(18f, 18f),   // Southeast corner
        Pair(0f, 18f),    // Start/Finish line
        Pair(-22f, 14f)   // Outside west green book
    )
    private var aiWaypointIndex = 0
    private var frameCounter = 0L

    init {
        viewModelScope.launch {
            lanManager.role.collect { role ->
                _uiState.update { it.copy(lanRole = role) }
            }
        }
        viewModelScope.launch {
            lanManager.statusText.collect { status ->
                _uiState.update { it.copy(lanStatus = status) }
            }
        }
        viewModelScope.launch {
            lanManager.localIpAddress.collect { ip ->
                _uiState.update { it.copy(localDeviceIp = ip) }
            }
        }
        viewModelScope.launch {
            lanManager.discoveredHosts.collect { hosts ->
                _uiState.update { it.copy(discoveredHosts = hosts) }
            }
        }
        viewModelScope.launch {
            lanManager.remotePlayers.collect { peers ->
                _uiState.update { it.copy(remoteKarts = peers) }
            }
        }

        // 60 FPS Portrait Arcade Physics Loop
        viewModelScope.launch {
            while (isActive) {
                stepPhysicsFrame()
                delay(16L)
            }
        }
    }

    /**
     * Analog steering wheel input for Portrait One-Thumb mode:
     * @param normalizedSteer -1.0f (full Left) to +1.0f (full Right)
     * @param isDrifting true when user slides thumb hard or engages drift zone
     */
    fun setAnalogSteeringWheel(normalizedSteer: Float, isDrifting: Boolean) {
        val clamped = normalizedSteer.coerceIn(-1f, 1f)
        // Internal physics rot: positive rot turns Left, negative rot turns Right
        val internalAxis = -clamped
        _uiState.update { state ->
            val wasDrifting = state.controls.drift
            val kart = state.localKart

            if (isDrifting && !wasDrifting) {
                // Trigger hop when entering drift
                val hopVy = if (kart.y <= 0.05f) 0.22f else kart.vy
                state.copy(
                    steerAxis = internalAxis,
                    controls = state.controls.copy(
                        left = clamped < -0.15f,
                        right = clamped > 0.15f,
                        drift = true
                    ),
                    localKart = kart.copy(vy = hopVy)
                )
            } else if (!isDrifting && wasDrifting) {
                // Released drift -> trigger Ultra Spark or Mini-Turbo boost!
                val ultraCharged = kart.driftCharge > 35
                val miniCharged = kart.driftCharge in 16..35
                val boostedSpeed = when {
                    ultraCharged -> 0.96f
                    miniCharged -> 0.80f
                    else -> kart.speed
                }
                val boostFrames = when {
                    ultraCharged -> 48
                    miniCharged -> 25
                    else -> kart.boostFrames
                }
                state.copy(
                    steerAxis = internalAxis,
                    controls = state.controls.copy(
                        left = clamped < -0.15f,
                        right = clamped > 0.15f,
                        drift = false
                    ),
                    localKart = kart.copy(
                        speed = boostedSpeed,
                        driftCharge = 0,
                        driftDir = 0,
                        boostFrames = boostFrames
                    ),
                    hudBannerMessage = if (ultraCharged) "🔥 ULTRA SPARK BOOST!" else state.hudBannerMessage
                )
            } else {
                state.copy(
                    steerAxis = internalAxis,
                    controls = state.controls.copy(
                        left = clamped < -0.15f,
                        right = clamped > 0.15f,
                        drift = isDrifting
                    )
                )
            }
        }
    }

    fun toggleAutoGas() {
        _uiState.update { it.copy(autoGasEnabled = !it.autoGasEnabled) }
    }

    fun toggleControlScheme() {
        _uiState.update { state ->
            val next = if (state.controlScheme == ControlScheme.ONE_THUMB_WHEEL) {
                ControlScheme.CLASSIC_PEDALS
            } else {
                ControlScheme.ONE_THUMB_WHEEL
            }
            state.copy(controlScheme = next)
        }
    }

    fun setSteeringLeft(pressed: Boolean) {
        _uiState.update { state ->
            state.copy(
                steerAxis = if (pressed) 1f else if (state.controls.right) -1f else 0f,
                controls = state.controls.copy(left = pressed)
            )
        }
    }

    fun setSteeringRight(pressed: Boolean) {
        _uiState.update { state ->
            state.copy(
                steerAxis = if (pressed) -1f else if (state.controls.left) 1f else 0f,
                controls = state.controls.copy(right = pressed)
            )
        }
    }

    fun setGasPressed(pressed: Boolean) {
        _uiState.update { state ->
            state.copy(controls = state.controls.copy(gas = pressed))
        }
    }

    fun setBrakePressed(pressed: Boolean) {
        _uiState.update { state ->
            state.copy(controls = state.controls.copy(brake = pressed))
        }
    }

    fun setDriftPressed(pressed: Boolean) {
        _uiState.update { state ->
            val kart = state.localKart
            if (pressed) {
                val hopVy = if (kart.y <= 0.05f) 0.22f else kart.vy
                state.copy(
                    controls = state.controls.copy(drift = true),
                    localKart = kart.copy(vy = hopVy)
                )
            } else {
                val ultraCharged = kart.driftCharge > 35
                val miniCharged = kart.driftCharge in 16..35
                val boostedSpeed = when {
                    ultraCharged -> 0.96f
                    miniCharged -> 0.80f
                    else -> kart.speed
                }
                val boostFrames = when {
                    ultraCharged -> 48
                    miniCharged -> 25
                    else -> kart.boostFrames
                }
                state.copy(
                    controls = state.controls.copy(drift = false),
                    localKart = kart.copy(
                        speed = boostedSpeed,
                        driftCharge = 0,
                        driftDir = 0,
                        boostFrames = boostFrames
                    ),
                    hudBannerMessage = if (ultraCharged) "🔥 ULTRA SPARK BOOST!" else state.hudBannerMessage
                )
            }
        }
    }

    fun fireHeldItem() {
        val currentState = _uiState.value
        val kart = currentState.localKart
        val heldItem = kart.item ?: return

        when (heldItem) {
            PowerUpItem.BATTERY_TURBO -> {
                val burstSparks = (0 until 16).map {
                    SparkParticle(
                        x = kart.x,
                        y = kart.y + 0.4f,
                        z = kart.z,
                        vx = (Random.nextFloat() - 0.5f) * 0.45f,
                        vy = Random.nextFloat() * 0.25f,
                        vz = (Random.nextFloat() - 0.5f) * 0.45f,
                        colorHex = 0xFF38BDF8,
                        life = 26
                    )
                }
                _uiState.update {
                    it.copy(
                        localKart = kart.copy(
                            speed = 0.98f,
                            boostFrames = 55,
                            item = null
                        ),
                        sparks = it.sparks + burstSparks,
                        hudBannerMessage = "⚡ BATTERY TURBO ACTIVATED!"
                    )
                }
            }
            PowerUpItem.MARBLE_SHELL -> {
                val dirX = -sin(kart.rot)
                val dirZ = -cos(kart.rot)
                val spawnX = kart.x + dirX * 2.2f
                val spawnZ = kart.z + dirZ * 2.2f
                val marbleVx = dirX * 1.15f
                val marbleVz = dirZ * 1.15f
                val marbleId = UUID.randomUUID().toString().take(8)
                val newMarble = MarbleProjectile(
                    id = marbleId,
                    ownerId = kart.id,
                    x = spawnX,
                    z = spawnZ,
                    vx = marbleVx,
                    vz = marbleVz,
                    life = 180,
                    colorHex = 0xFF38BDF8
                )
                lanManager.broadcastMarbleFired(
                    RemoteMarbleEvent(
                        id = marbleId,
                        ownerId = kart.id,
                        x = spawnX,
                        z = spawnZ,
                        vx = marbleVx,
                        vz = marbleVz
                    )
                )
                _uiState.update {
                    it.copy(
                        localKart = kart.copy(item = null),
                        marbles = it.marbles + newMarble,
                        hudBannerMessage = "🟢 MARBLE SHELL FIRED!"
                    )
                }
            }
        }
    }

    private fun spawnRemoteMarble(event: RemoteMarbleEvent) {
        _uiState.update { state ->
            state.copy(
                marbles = state.marbles + MarbleProjectile(
                    id = event.id,
                    ownerId = event.ownerId,
                    x = event.x,
                    z = event.z,
                    vx = event.vx,
                    vz = event.vz,
                    life = 180,
                    colorHex = 0xFFEF4444
                )
            )
        }
    }

    fun updateRoomInput(value: String) {
        _uiState.update { it.copy(roomInput = value.take(22)) }
    }

    fun hostHotspotSession() {
        val state = _uiState.value
        lanManager.hostHotspotGame(state.roomInput, state.localKart.id)
        _uiState.update { it.copy(isLobbyExpanded = false) }
    }

    fun joinHotspotSession(targetOverride: String? = null) {
        val state = _uiState.value
        val target = targetOverride ?: state.roomInput
        lanManager.joinHotspotGame(target, state.localKart.id)
        _uiState.update { it.copy(isLobbyExpanded = false) }
    }

    fun disconnectLanSession() {
        lanManager.disconnect()
    }

    fun toggleLobbyExpanded() {
        lanManager.refreshLocalIp()
        _uiState.update { it.copy(isLobbyExpanded = !it.isLobbyExpanded) }
    }

    fun toggleBlueprintSheet(open: Boolean? = null) {
        _uiState.update {
            it.copy(isBlueprintSheetOpen = open ?: !it.isBlueprintSheetOpen)
        }
    }

    fun setEngineMode(useWebGl: Boolean) {
        _uiState.update { it.copy(useWebGlEngine = useWebGl) }
    }

    fun toggleAiRival() {
        _uiState.update { it.copy(aiRivalEnabled = !it.aiRivalEnabled) }
    }

    fun cycleCameraMode() {
        _uiState.update { state ->
            val modes = CameraMode.entries
            val next = modes[(modes.indexOf(state.cameraMode) + 1) % modes.size]
            state.copy(cameraMode = next)
        }
    }

    fun respawnKart() {
        _uiState.update { state ->
            state.copy(
                localKart = state.localKart.copy(
                    x = 0f,
                    y = 0.6f,
                    z = 18f,
                    speed = 0f,
                    vy = 0f,
                    rot = 0f,
                    driftCharge = 0,
                    spinOutFrames = 0
                ),
                hudBannerMessage = "RESPAWNED AT START LINE"
            )
        }
    }

    fun stepPhysicsFrame() {
        frameCounter++
        val nowMs = System.currentTimeMillis()

        _uiState.update { state ->
            val ctrl = state.controls
            var kart = state.localKart

            // 1. Analog + Button Steering & Drift Charge
            val maxTurnRate = if (ctrl.drift) 0.058f else 0.040f
            val effectiveAxis = when {
                abs(state.steerAxis) > 0.04f -> state.steerAxis
                ctrl.left -> 1f
                ctrl.right -> -1f
                else -> 0f
            }

            var newRot = kart.rot
            if (kart.spinOutFrames > 0) {
                newRot += 0.24f
            } else {
                newRot += effectiveAxis * maxTurnRate
            }

            var newDriftCharge = kart.driftCharge
            val newDriftDir = when {
                ctrl.drift && effectiveAxis > 0.15f -> 1
                ctrl.drift && effectiveAxis < -0.15f -> -1
                else -> 0
            }
            val isAccelerating = ctrl.gas || (state.autoGasEnabled && !ctrl.brake)
            if (ctrl.drift && isAccelerating && abs(effectiveAxis) > 0.15f && kart.spinOutFrames == 0) {
                newDriftCharge++
            } else if (!ctrl.drift) {
                newDriftCharge = 0
            }

            // 2. Speed & Acceleration
            var newSpeed = kart.speed
            val newBoostFrames = (kart.boostFrames - 1).coerceAtLeast(0)
            val newSpinFrames = (kart.spinOutFrames - 1).coerceAtLeast(0)

            val maxSpeed = if (newBoostFrames > 0) 0.96f else 0.58f
            if (newSpinFrames > 0) {
                newSpeed *= 0.88f
            } else if (ctrl.brake) {
                newSpeed = (newSpeed - 0.024f).coerceAtLeast(-0.30f)
            } else if (isAccelerating) {
                newSpeed = (newSpeed + 0.017f).coerceAtMost(maxSpeed)
            } else {
                newSpeed *= 0.96f
                if (abs(newSpeed) < 0.003f) newSpeed = 0f
            }

            // 3. Position update
            var newX = kart.x - sin(newRot) * newSpeed
            var newZ = kart.z - cos(newRot) * newSpeed
            var newY = kart.y
            var newVy = kart.vy

            // 4. Yellow Ruler Jump Ramp at (-8, 0.9, -12) check
            if (newX > -11f && newX < -5f && newZ > -18f && newZ < -6f && newSpeed > 0.28f && newY <= 0.95f) {
                newVy = 0.29f
            }

            newY += newVy
            if (newY > 0f) {
                newVy -= 0.015f
            } else {
                newY = 0f
                newVy = 0f
            }

            // 5. Soft Desk Bumper Guardrails (Bounce back instead of harsh reset!)
            if (newX > 37.5f) {
                newX = 37.2f
                newRot = -newRot
                newSpeed *= 0.65f
            } else if (newX < -37.5f) {
                newX = -37.2f
                newRot = -newRot
                newSpeed *= 0.65f
            }
            if (newZ > 37.5f) {
                newZ = 37.2f
                newRot = PI.toFloat() - newRot
                newSpeed *= 0.65f
            } else if (newZ < -37.5f) {
                newZ = -37.2f
                newRot = PI.toFloat() - newRot
                newSpeed *= 0.65f
            }

            // 6. Book Obstacles Sliding Collision
            for (ob in state.books) {
                if (abs(newX - ob.x) < ob.rx && abs(newZ - ob.z) < ob.rz && newY < ob.height) {
                    val overlapX = ob.rx - abs(newX - ob.x)
                    val overlapZ = ob.rz - abs(newZ - ob.z)
                    if (overlapX < overlapZ) {
                        newX += if (newX > ob.x) overlapX + 0.1f else -(overlapX + 0.1f)
                    } else {
                        newZ += if (newZ > ob.z) overlapZ + 0.1f else -(overlapZ + 0.1f)
                    }
                    newSpeed *= 0.55f
                }
            }

            // 7. Lap & Checkpoint Tracking
            var passedNorth = kart.passedNorthCheckpoint
            var lap = kart.lap
            var bestLap = kart.bestLapMs
            var lapStart = kart.currentLapStartMs
            if (newZ < -10f) {
                passedNorth = true
            }
            if (passedNorth && newZ in 15f..22f && abs(newX) < 16f && kart.z < 15f) {
                val lapDuration = nowMs - lapStart
                if (lapDuration > 3500L) {
                    bestLap = if (bestLap == null) lapDuration else minOf(bestLap, lapDuration)
                    lap += 1
                    lapStart = nowMs
                    passedNorth = false
                }
            }

            // 8. Item Boxes Rotation & Pickup
            var pickedItem = kart.item
            val updatedBoxes = state.itemBoxes.map { box ->
                val isNowVisible = box.visible || (box.respawnAtMs in 1..nowMs)
                val rotY = box.rotY + 0.045f
                val rotX = box.rotX + 0.025f
                if (isNowVisible && hypot(newX - box.x, newZ - box.z) < 2.3f) {
                    if (pickedItem == null) {
                        pickedItem = if (Random.nextFloat() > 0.4f) {
                            PowerUpItem.MARBLE_SHELL
                        } else {
                            PowerUpItem.BATTERY_TURBO
                        }
                    }
                    box.copy(
                        visible = false,
                        respawnAtMs = nowMs + 5000L,
                        rotX = rotX,
                        rotY = rotY
                    )
                } else {
                    box.copy(
                        visible = isNowVisible,
                        respawnAtMs = if (isNowVisible) 0L else box.respawnAtMs,
                        rotX = rotX,
                        rotY = rotY
                    )
                }
            }

            // 9. Update AI Rival Kart
            var rival = state.aiRivalKart
            if (state.aiRivalEnabled) {
                val targetWp = aiWaypoints[aiWaypointIndex]
                val dx = targetWp.first - rival.x
                val dz = targetWp.second - rival.z
                if (hypot(dx, dz) < 5.5f) {
                    aiWaypointIndex = (aiWaypointIndex + 1) % aiWaypoints.size
                }
                val desiredRot = atan2(-dx, -dz)
                var angleDiff = desiredRot - rival.rot
                while (angleDiff > PI) angleDiff -= (PI * 2).toFloat()
                while (angleDiff < -PI) angleDiff += (PI * 2).toFloat()
                val rivalRot = rival.rot + angleDiff.coerceIn(-0.044f, 0.044f)
                val rivalSpeed = if (rival.spinOutFrames > 0) 0f else (rival.speed + 0.015f).coerceAtMost(0.49f)
                var rx = rival.x - sin(rivalRot) * rivalSpeed
                var rz = rival.z - cos(rivalRot) * rivalSpeed
                var ry = rival.y
                var rvy = rival.vy
                if (rx > -11f && rx < -5f && rz > -18f && rz < -6f && rivalSpeed > 0.3f && ry <= 0.9f) {
                    rvy = 0.26f
                }
                ry += rvy
                if (ry > 0f) rvy -= 0.015f else { ry = 0f; rvy = 0f }
                for (ob in state.books) {
                    if (abs(rx - ob.x) < ob.rx && abs(rz - ob.z) < ob.rz) {
                        rx += sin(rivalRot) * 0.5f
                        rz += cos(rivalRot) * 0.5f
                    }
                }
                rival = rival.copy(
                    x = rx,
                    y = ry,
                    z = rz,
                    rot = rivalRot,
                    speed = rivalSpeed,
                    vy = rvy,
                    spinOutFrames = (rival.spinOutFrames - 1).coerceAtLeast(0)
                )
            }

            // 10. Bouncy Marbles Physics & Obstacle Ricochet
            val nextMarbles = mutableListOf<MarbleProjectile>()
            var hitLocalByMarble = false
            for (m in state.marbles) {
                var mx = m.x + m.vx
                var mz = m.z + m.vz
                var mvx = m.vx
                var mvz = m.vz
                var mLife = m.life - 1

                if (abs(mx) > 37f) mvx = -mvx
                if (abs(mz) > 37f) mvz = -mvz

                for (ob in state.books) {
                    if (abs(mx - ob.x) < ob.rx && abs(mz - ob.z) < ob.rz) {
                        if (abs(mx - ob.x) / ob.rx > abs(mz - ob.z) / ob.rz) {
                            mvx = -mvx
                        } else {
                            mvz = -mvz
                        }
                    }
                }

                if (mLife < 165 && hypot(newX - mx, newZ - mz) < 1.55f) {
                    hitLocalByMarble = true
                    mLife = 0
                }

                if (state.aiRivalEnabled && mLife < 165 && hypot(rival.x - mx, rival.z - mz) < 1.55f) {
                    rival = rival.copy(speed = 0f, vy = 0.32f, spinOutFrames = 35)
                    mLife = 0
                }

                if (mLife > 0) {
                    nextMarbles.add(m.copy(x = mx, z = mz, vx = mvx, vz = mvz, life = mLife))
                }
            }

            if (hitLocalByMarble) {
                newSpeed = 0f
                newVy = 0.35f
            }

            // 11. Drift & Boost Spark Particles
            val updatedSparks = state.sparks.mapNotNull { sp ->
                if (sp.life <= 1) null
                else sp.copy(
                    x = sp.x + sp.vx,
                    y = (sp.y + sp.vy).coerceAtLeast(0.05f),
                    z = sp.z + sp.vz,
                    vy = sp.vy - 0.01f,
                    life = sp.life - 1
                )
            }.toMutableList()

            if (newDriftCharge > 4 && updatedSparks.size < 45) {
                val sparkColor = when {
                    newDriftCharge > 35 -> 0xFFF97316L // Ultra Orange/Gold
                    newDriftCharge > 16 -> 0xFFFACC15L // Super Yellow
                    else -> 0xFF38BDF8L                // Mini Cyan
                }
                updatedSparks.add(
                    SparkParticle(
                        x = newX + sin(newRot) * 1.1f + (Random.nextFloat() - 0.5f) * 0.7f,
                        y = newY + 0.16f,
                        z = newZ + cos(newRot) * 1.1f + (Random.nextFloat() - 0.5f) * 0.7f,
                        vx = (Random.nextFloat() - 0.5f) * 0.18f,
                        vy = Random.nextFloat() * 0.14f,
                        vz = (Random.nextFloat() - 0.5f) * 0.18f,
                        colorHex = sparkColor,
                        life = 15
                    )
                )
            }

            kart = kart.copy(
                x = newX,
                y = newY,
                z = newZ,
                rot = newRot,
                speed = newSpeed,
                vy = newVy,
                driftCharge = newDriftCharge,
                driftDir = newDriftDir,
                item = pickedItem,
                lap = lap,
                passedNorthCheckpoint = passedNorth,
                bestLapMs = bestLap,
                currentLapStartMs = lapStart,
                boostFrames = newBoostFrames,
                spinOutFrames = if (hitLocalByMarble) 32 else newSpinFrames
            )

            // 12. Portrait-Optimized Third-Person Camera:
            // Places the kart slightly higher in the vertical frame so the bottom 28% thumb deck never covers the car!
            val forwardX = -sin(newRot)
            val forwardZ = -cos(newRot)
            val (targetCamX, targetCamY, targetCamZ) = when (state.cameraMode) {
                CameraMode.CHASE -> Triple(
                    newX - forwardX * 9.2f,
                    newY + 5.2f,
                    newZ - forwardZ * 9.2f
                )
                CameraMode.CLOSE_DRIFT -> Triple(
                    newX - forwardX * 6.4f,
                    newY + 3.4f,
                    newZ - forwardZ * 6.4f
                )
                CameraMode.TOP_DESK -> Triple(
                    newX * 0.4f,
                    42f,
                    newZ * 0.4f + 24f
                )
            }

            // Look slightly ahead & below the kart so the kart sits in the upper-middle of the Portrait screen
            val targetLookX = newX + forwardX * 4.5f
            val targetLookY = newY + 0.1f
            val targetLookZ = newZ + forwardZ * 4.5f

            val lerpFactor = 0.18f
            val camX = state.cameraX + (targetCamX - state.cameraX) * lerpFactor
            val camY = state.cameraY + (targetCamY - state.cameraY) * lerpFactor
            val camZ = state.cameraZ + (targetCamZ - state.cameraZ) * lerpFactor

            val lookX = state.cameraLookX + (targetLookX - state.cameraLookX) * 0.25f
            val lookY = state.cameraLookY + (targetLookY - state.cameraLookY) * 0.25f
            val lookZ = state.cameraLookZ + (targetLookZ - state.cameraLookZ) * 0.25f

            // 13. Broadcast position to Hotspot peers every 2nd frame (~30Hz)
            if (frameCounter % 2L == 0L) {
                lanManager.broadcastLocalKartState(kart)
            }

            state.copy(
                localKart = kart,
                aiRivalKart = rival,
                itemBoxes = updatedBoxes,
                marbles = nextMarbles,
                sparks = updatedSparks,
                cameraX = camX,
                cameraY = camY,
                cameraZ = camZ,
                cameraLookX = lookX,
                cameraLookY = lookY,
                cameraLookZ = lookZ
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        lanManager.shutdownAll()
    }
}
