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
    val cameraY: Float = 3.8f,
    val cameraZ: Float = 25.5f,
    val cameraLookX: Float = 0f,
    val cameraLookY: Float = 0.8f,
    val cameraLookZ: Float = 18f,
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
        get() = (abs(localKart.speed) * 145f).roundToInt()

    val speedDisplayStatus: String
        get() = when {
            localKart.spinOutFrames > 0 -> "💥 MARBLE HIT! SPINNING..."
            localKart.boostFrames > 0 -> "🚀 TURBO BOOSTING! ${speedKmh} KM/H"
            controls.drift && controls.gas && (controls.left || controls.right) -> {
                if (localKart.driftCharge > 35) "🔥 ULTRA SPARK READY!"
                else "⚡ CHARGING DRIFT... (${(localKart.driftCharge * 100 / 36).coerceAtMost(99)}%)"
            }
            localKart.speed == 0f -> "DRIFT BOOST READY"
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
        // Observe LAN manager states
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

        // Start 60 FPS Arcade Physics & Multiplayer Sync Loop
        viewModelScope.launch {
            while (isActive) {
                stepPhysicsFrame()
                delay(16L)
            }
        }
    }

    fun setSteeringLeft(pressed: Boolean) {
        _uiState.update { state ->
            state.copy(controls = state.controls.copy(left = pressed))
        }
    }

    fun setSteeringRight(pressed: Boolean) {
        _uiState.update { state ->
            state.copy(controls = state.controls.copy(right = pressed))
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
                // Hop-drift initiation when on the desk surface
                val hopVy = if (kart.y <= 0.05f) 0.22f else kart.vy
                state.copy(
                    controls = state.controls.copy(drift = true),
                    localKart = kart.copy(vy = hopVy)
                )
            } else {
                // Release drift: check if Ultra Spark (> 35 frames) or Mini Turbo (> 16 frames) is charged
                val ultraCharged = kart.driftCharge > 35
                val miniCharged = kart.driftCharge in 16..35
                val boostedSpeed = when {
                    ultraCharged -> 0.95f
                    miniCharged -> 0.78f
                    else -> kart.speed
                }
                val boostFrames = when {
                    ultraCharged -> 45
                    miniCharged -> 22
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
                val burstSparks = (0 until 14).map {
                    SparkParticle(
                        x = kart.x,
                        y = kart.y + 0.4f,
                        z = kart.z,
                        vx = (Random.nextFloat() - 0.5f) * 0.45f,
                        vy = Random.nextFloat() * 0.25f,
                        vz = (Random.nextFloat() - 0.5f) * 0.45f,
                        colorHex = 0xFF38BDF8,
                        life = 24
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

            // 1. Steering & Drift Charge (exact blueprint parameters: 0.055 drift vs 0.038 normal)
            val turnRate = if (ctrl.drift) 0.055f else 0.038f
            var newRot = kart.rot
            if (kart.spinOutFrames > 0) {
                newRot += 0.24f
            } else {
                if (ctrl.left) newRot += turnRate
                if (ctrl.right) newRot -= turnRate
            }

            var newDriftCharge = kart.driftCharge
            val newDriftDir = when {
                ctrl.drift && ctrl.left -> 1
                ctrl.drift && ctrl.right -> -1
                else -> 0
            }
            if (ctrl.drift && ctrl.gas && (ctrl.left || ctrl.right) && kart.spinOutFrames == 0) {
                newDriftCharge++
            } else if (!ctrl.drift) {
                newDriftCharge = 0
            }

            // 2. Speed & Acceleration (exact blueprint parameters: +0.018 up to 0.62, 0.96 damping)
            var newSpeed = kart.speed
            val newBoostFrames = (kart.boostFrames - 1).coerceAtLeast(0)
            val newSpinFrames = (kart.spinOutFrames - 1).coerceAtLeast(0)

            val maxSpeed = if (newBoostFrames > 0) 0.95f else 0.62f
            if (newSpinFrames > 0) {
                newSpeed *= 0.88f
            } else if (ctrl.gas) {
                newSpeed = (newSpeed + 0.018f).coerceAtMost(maxSpeed)
            } else if (ctrl.brake) {
                newSpeed = (newSpeed - 0.022f).coerceAtLeast(-0.32f)
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
            if (newX > -11f && newX < -5f && newZ > -18f && newZ < -6f && newSpeed > 0.3f && newY <= 0.95f) {
                newVy = 0.28f
            }

            newY += newVy
            if (newY > 0f) {
                newVy -= 0.015f
            } else {
                newY = 0f
                newVy = 0f
            }

            // 5. Desk boundary check (80x80 wooden desk -> |x| > 40 or |z| > 40)
            if (abs(newX) > 40f || abs(newZ) > 40f) {
                newX = 0f
                newZ = 18f
                newY = 0.5f
                newVy = 0f
                newSpeed = 0f
                newRot = 0f
            }

            // 6. Book Obstacles Collision
            for (ob in state.books) {
                if (abs(newX - ob.x) < ob.rx && abs(newZ - ob.z) < ob.rz && newY < ob.height) {
                    newSpeed = -0.25f
                    newX += sin(newRot) * 0.65f
                    newZ += cos(newRot) * 0.65f
                }
            }

            // 7. Lap & Checkpoint Tracking
            var passedNorth = kart.passedNorthCheckpoint
            var lap = kart.lap
            var bestLap = kart.bestLapMs
            var lapStart = kart.currentLapStartMs
            if (newZ < -12f) {
                passedNorth = true
            }
            if (passedNorth && newZ in 16f..21f && abs(newX) < 14f && kart.z < 16f) {
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
                val rotY = box.rotY + 0.04f
                val rotX = box.rotX + 0.02f
                if (isNowVisible && hypot(newX - box.x, newZ - box.z) < 2.1f) {
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

            // 9. Update AI Rival Kart if enabled
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
                val rivalRot = rival.rot + angleDiff.coerceIn(-0.042f, 0.042f)
                val rivalSpeed = if (rival.spinOutFrames > 0) 0f else (rival.speed + 0.014f).coerceAtMost(0.50f)
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

                // Bounce off desk edges
                if (abs(mx) > 38f) mvx = -mvx
                if (abs(mz) > 38f) mvz = -mvz

                // Bounce off textbook obstacles
                for (ob in state.books) {
                    if (abs(mx - ob.x) < ob.rx && abs(mz - ob.z) < ob.rz) {
                        if (abs(mx - ob.x) / ob.rx > abs(mz - ob.z) / ob.rz) {
                            mvx = -mvx
                        } else {
                            mvz = -mvz
                        }
                    }
                }

                // Check collision with local kart (after initial grace period m.life < 165)
                if (mLife < 165 && hypot(newX - mx, newZ - mz) < 1.55f) {
                    hitLocalByMarble = true
                    mLife = 0
                }

                // Check collision with AI rival kart
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

            if (newDriftCharge > 4 && updatedSparks.size < 40) {
                val sparkColor = if (newDriftCharge > 35) 0xFFF97316L else 0xFF38BDF8L
                updatedSparks.add(
                    SparkParticle(
                        x = newX + sin(newRot) * 1.0f + (Random.nextFloat() - 0.5f) * 0.6f,
                        y = newY + 0.15f,
                        z = newZ + cos(newRot) * 1.0f + (Random.nextFloat() - 0.5f) * 0.6f,
                        vx = (Random.nextFloat() - 0.5f) * 0.18f,
                        vy = Random.nextFloat() * 0.12f,
                        vz = (Random.nextFloat() - 0.5f) * 0.18f,
                        colorHex = sparkColor,
                        life = 14
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

            // 12. Third-Person Camera Lerp (exact blueprint offset: sin(rot)*7.5, y+3.8, cos(rot)*7.5, lerp 0.15)
            val (targetCamX, targetCamY, targetCamZ) = when (state.cameraMode) {
                CameraMode.CHASE -> Triple(
                    newX + sin(newRot) * 7.5f,
                    newY + 3.8f,
                    newZ + cos(newRot) * 7.5f
                )
                CameraMode.CLOSE_DRIFT -> Triple(
                    newX + sin(newRot) * 5.2f,
                    newY + 2.3f,
                    newZ + cos(newRot) * 5.2f
                )
                CameraMode.TOP_DESK -> Triple(
                    newX * 0.35f,
                    38f,
                    newZ * 0.35f + 22f
                )
            }
            val lerpFactor = 0.16f
            val camX = state.cameraX + (targetCamX - state.cameraX) * lerpFactor
            val camY = state.cameraY + (targetCamY - state.cameraY) * lerpFactor
            val camZ = state.cameraZ + (targetCamZ - state.cameraZ) * lerpFactor

            val lookX = state.cameraLookX + (newX - state.cameraLookX) * 0.25f
            val lookY = state.cameraLookY + ((newY + 0.8f) - state.cameraLookY) * 0.25f
            val lookZ = state.cameraLookZ + (newZ - state.cameraLookZ) * 0.25f

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
