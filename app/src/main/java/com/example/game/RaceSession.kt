package com.example.game

import com.example.audio.SynthAudio
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class RacePhase {
    SHOWROOM_MENU,
    COUNTDOWN,
    RACING,
    FINISHED,
    RESULTS
}

data class RacerStanding(
    val slotIndex: Int,
    val position: Int,
    val name: String,
    val colorHex: Long,
    val isHuman: Boolean,
    val isLocal: Boolean,
    val totalTimeMs: Long,
    val bestLapMs: Long,
    val isNewRecord: Boolean = false
)

data class RenderKartSnapshot(
    val slotIndex: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float,
    val visualYawOffsetRad: Float,
    val visualRollRad: Float,
    val visualPitchRad: Float,
    val speed: Float,
    val colorHex: Long,
    val bodyType: KartBodyType,
    val sparkTier: Int,
    val isBoosting: Boolean,
    val isOffTrack: Boolean,
    val isInvulnerableFlashing: Boolean,
    val orbitingTacksCount: Int,
    val shieldEraserHeld: Boolean,
    val heldItem: ItemType
)

data class RenderProjectileSnapshot(
    val id: Int,
    val kind: ProjectileKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float
)

data class RenderItemBoxSnapshot(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val active: Boolean,
    val rotationRad: Float
)

data class WorldRenderSnapshot(
    val phase: RacePhase = RacePhase.SHOWROOM_MENU,
    val trackId: Int = 0,
    val localSlotIndex: Int = 0,
    val karts: List<RenderKartSnapshot> = emptyList(),
    val projectiles: List<RenderProjectileSnapshot> = emptyList(),
    val itemBoxes: List<RenderItemBoxSnapshot> = emptyList(),
    val landingShakeSignal: Int = 0,
    val showroomCameraAngleRad: Float = 0f
)

data class MinimapDot(
    val slotIndex: Int,
    val x: Float,
    val z: Float,
    val colorHex: Long,
    val isLocal: Boolean
)

data class HudState(
    val phase: RacePhase = RacePhase.SHOWROOM_MENU,
    val trackId: Int = 0,
    val trackName: String = "STUDY DESK",
    val position: Int = 1,
    val totalRacers: Int = 6,
    val lap: Int = 1,
    val totalLaps: Int = 3,
    val raceTimeMs: Long = 0L,
    val bestLapMs: Long = 0L,
    val speedKmh: Int = 0,
    val speedRatio: Float = 0f,
    val isDrifting: Boolean = false,
    val driftChargeRatio: Float = 0f,
    val sparkTier: Int = 0,
    val heldItem: ItemType = ItemType.NONE,
    val isRouletteSpinning: Boolean = false,
    val rouletteDisplayItem: ItemType = ItemType.NONE,
    val countdownNumber: Int = 3, // 3, 2, 1, 0 ("GO!")
    val isWrongWay: Boolean = false,
    val bannerText: String? = null,
    val toastText: String? = null,
    val showRocketStartTip: Boolean = false,
    val minimapDots: List<MinimapDot> = emptyList(),
    val standings: List<RacerStanding> = emptyList()
)

class RaceSession(
    private val synthAudio: SynthAudio,
    private val onNewBestLapRecorded: (trackId: Int, lapMs: Long) -> Boolean
) {
    var phase: RacePhase = RacePhase.SHOWROOM_MENU
        private set

    var currentTrack: Track = Tracks.studyDesk
        private set

    var totalLaps: Int = 3
        private set

    var itemMode: ItemMode = ItemMode.NORMAL
        private set

    var cpuDifficulty: CpuDifficulty = CpuDifficulty.NORMAL
        private set

    var isMultiplayer: Boolean = false
        private set

    var isHostAuthority: Boolean = true
        private set

    var localSlotIndex: Int = 0
        private set

    val karts = ArrayList<KartRuntime>(GameConstants.MAX_RACERS)
    val itemBoxes = ArrayList<ActiveItemBox>()
    val projectiles = ArrayList<WorldProjectile>()

    private var nextProjectileId = 100
    private var countdownRemainingSec = 3.4f
    private var lastCountdownBeepInt = 4
    private var raceElapsedSec = 0f
    private var finishDelaySec = 0f
    private var bannerText: String? = null
    private var bannerTimerSec = 0f
    private var toastText: String? = null
    private var toastTimerSec = 0f
    private var landingShakeCounter = 0
    private var showroomAngleRad = 0f
    private var newRecordAchievedLocal = false

    // Callbacks for multiplayer network sync
    var onLocalClientItemAction: ((ItemType, Boolean) -> Unit)? = null
    var onHostBroadcastEvent: ((String) -> Unit)? = null

    init {
        setupShowroom(
            playerName = "RACER",
            colorHex = GameConstants.KART_COLORS[0],
            bodyType = KartBodyType.CLASSIC,
            trackId = 0
        )
    }

    @Synchronized
    fun setupShowroom(
        playerName: String,
        colorHex: Long,
        bodyType: KartBodyType,
        trackId: Int
    ) {
        phase = RacePhase.SHOWROOM_MENU
        currentTrack = Tracks.byId(trackId)
        karts.clear()
        projectiles.clear()
        itemBoxes.clear()
        val slot0 = currentTrack.startGrid.first()
        val demoKart = KartRuntime(
            slotIndex = 0,
            playerName = playerName,
            isHuman = true,
            isLocalPlayer = true,
            colorHex = colorHex,
            bodyType = bodyType
        ).apply {
            x = slot0.x
            y = slot0.y
            z = slot0.z
            headingRad = slot0.headingRad
        }
        karts.add(demoKart)
        for (b in currentTrack.itemBoxes) {
            itemBoxes.add(ActiveItemBox(b.id, b.x, b.y, b.z, active = true))
        }
        synthAudio.isEngineActive = false
    }

    @Synchronized
    fun startNewRace(
        trackId: Int,
        laps: Int,
        mode: ItemMode,
        difficulty: CpuDifficulty,
        multiplayer: Boolean,
        hostAuthority: Boolean,
        localSlot: Int,
        racerConfigs: List<KartRuntime>
    ) {
        currentTrack = Tracks.byId(trackId)
        totalLaps = laps.coerceIn(1, 5)
        itemMode = mode
        cpuDifficulty = difficulty
        isMultiplayer = multiplayer
        isHostAuthority = hostAuthority
        localSlotIndex = localSlot
        newRecordAchievedLocal = false

        karts.clear()
        for (cfg in racerConfigs) {
            val gridSlot = currentTrack.startGrid.getOrElse(cfg.slotIndex) { currentTrack.startGrid.first() }
            KartPhysics.resetKartToGrid(cfg, gridSlot)
            karts.add(cfg)
        }

        itemBoxes.clear()
        for (b in currentTrack.itemBoxes) {
            itemBoxes.add(
                ActiveItemBox(
                    id = b.id,
                    x = b.x,
                    y = b.y,
                    z = b.z,
                    active = (mode != ItemMode.OFF)
                )
            )
        }
        projectiles.clear()

        phase = RacePhase.COUNTDOWN
        countdownRemainingSec = 3.2f
        lastCountdownBeepInt = 4
        raceElapsedSec = 0f
        finishDelaySec = 0f
        bannerText = "LAP 1/$totalLaps"
        bannerTimerSec = 2.0f
        toastText = null
        toastTimerSec = 0f
        synthAudio.isEngineActive = true
    }

    @Synchronized
    fun showToast(msg: String, durationSec: Float = 2.0f) {
        toastText = msg
        toastTimerSec = durationSec
    }

    @Synchronized
    fun applyRemoteKartState(
        slotIndex: Int,
        x: Float,
        y: Float,
        z: Float,
        headingRad: Float,
        speed: Float,
        sparkTier: Int,
        isBoosting: Boolean
    ) {
        if (slotIndex == localSlotIndex) return
        val kart = karts.firstOrNull { it.slotIndex == slotIndex } ?: return
        // Smooth blend (65% incoming + 35% current) to prevent jitter
        kart.x += (x - kart.x) * 0.65f
        kart.y += (y - kart.y) * 0.65f
        kart.z += (z - kart.z) * 0.65f
        kart.headingRad = headingRad
        kart.forwardSpeed = speed
        kart.isDrifting = sparkTier > 0
        kart.driftChargeUnits = when (sparkTier) {
            3 -> GameConstants.SPARK_PURPLE_UNITS
            2 -> GameConstants.SPARK_ORANGE_UNITS
            1 -> GameConstants.SPARK_BLUE_UNITS
            else -> 0
        }
        if (isBoosting && kart.boostTimerSec <= 0f) {
            kart.boostTimerSec = 0.25f
        }
        val (sample, _) = currentTrack.findNearestSample(kart.x, kart.z)
        kart.splineDistance = sample.distance
    }

    @Synchronized
    fun convertSlotToCpu(slotIndex: Int, playerName: String) {
        val kart = karts.firstOrNull { it.slotIndex == slotIndex } ?: return
        kart.isHuman = false
        kart.playerName = "$playerName (CPU)"
        showToast("$playerName left — CPU took over", 2.5f)
    }

    @Synchronized
    fun stepFixed(
        dt: Float,
        localInput: TouchInputSnapshot,
        autoAccelerate: Boolean,
        smartSteering: Boolean,
        showRocketStartTip: Boolean
    ) {
        if (bannerTimerSec > 0f) {
            bannerTimerSec -= dt
            if (bannerTimerSec <= 0f) bannerText = null
        }
        if (toastTimerSec > 0f) {
            toastTimerSec -= dt
            if (toastTimerSec <= 0f) toastText = null
        }

        when (phase) {
            RacePhase.SHOWROOM_MENU -> {
                showroomAngleRad += dt * 0.55f
                for (box in itemBoxes) {
                    box.rotationRad += dt * 1.8f
                }
                synthAudio.isEngineActive = false
            }

            RacePhase.COUNTDOWN -> {
                countdownRemainingSec -= dt
                val ceilSec = kotlin.math.ceil(countdownRemainingSec.toDouble()).toInt().coerceIn(0, 3)
                if (ceilSec in 1..3 && ceilSec < lastCountdownBeepInt) {
                    lastCountdownBeepInt = ceilSec
                    synthAudio.playCountdownBeep(isGo = false)
                }

                // Rocket Start evaluation on local player kart
                val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex }
                if (localKart != null) {
                    if (localInput.gasPressed) {
                        if (countdownRemainingSec > 1.85f) {
                            // Held gas too early -> engine overheated!
                            localKart.rocketStartOverheated = true
                        } else {
                            localKart.rocketStartHoldSec += dt
                        }
                    }
                    synthAudio.engineSpeedRatio = if (localInput.gasPressed) 0.65f else 0.15f
                }

                if (countdownRemainingSec <= 0f) {
                    phase = RacePhase.RACING
                    synthAudio.playCountdownBeep(isGo = true)
                    val nowRaceMs = 0L
                    for (k in karts) {
                        k.currentLapStartMs = nowRaceMs
                    }
                    if (localKart != null) {
                        if (localKart.rocketStartOverheated) {
                            localKart.spinOutTimerSec = GameConstants.ROCKET_START_FAIL_SPIN_SEC
                            showToast("EARLY GAS! SPIN-OUT!", 1.5f)
                        } else if (localKart.rocketStartHoldSec in 0.15f..1.65f) {
                            KartPhysics.applyBoost(localKart, GameConstants.ROCKET_START_BOOST_SEC)
                            synthAudio.playBoostWhoosh()
                            showToast("🚀 ROCKET START!", 1.6f)
                        }
                    }
                }
            }

            RacePhase.RACING, RacePhase.FINISHED -> {
                raceElapsedSec += dt
                val raceMs = (raceElapsedSec * 1000f).toLong()

                val leadingHumanProgress = karts.filter { it.isHuman }
                    .maxOfOrNull { it.totalRaceProgress } ?: 0f

                for (kart in karts) {
                    if (kart.slotIndex == localSlotIndex && !kart.hasFinishedRace) {
                        // Local human player simulation
                        handleItemActivation(kart, localInput)
                        KartPhysics.stepKart(
                            dt = dt,
                            kart = kart,
                            input = localInput,
                            track = currentTrack,
                            allKarts = karts,
                            autoAccelerate = autoAccelerate,
                            smartSteering = smartSteering,
                            speedScale = 1.0f,
                            onWallBump = { synthAudio.playWallBumpTick() },
                            onBoostTriggered = { synthAudio.playBoostWhoosh() },
                            onRespawnToast = { showToast("RESPAWN", 1.5f) },
                            onLapCompleted = { completedLap ->
                                val lapDuration = raceMs - kart.currentLapStartMs
                                kart.currentLapStartMs = raceMs
                                if (kart.bestLapMs == 0L || lapDuration < kart.bestLapMs) {
                                    kart.bestLapMs = lapDuration
                                }
                                if (onNewBestLapRecorded(currentTrack.id, lapDuration)) {
                                    newRecordAchievedLocal = true
                                }
                                if (completedLap >= totalLaps) {
                                    kart.hasFinishedRace = true
                                    kart.finishTimeMs = raceMs
                                    phase = RacePhase.FINISHED
                                    finishDelaySec = 2.8f
                                    synthAudio.playFinishFanfare()
                                    bannerText = "FINISHED ${ordinal(kart.racePosition)}!"
                                    bannerTimerSec = 3.0f
                                } else {
                                    bannerText = if (completedLap + 1 == totalLaps) {
                                        "FINAL LAP!"
                                    } else {
                                        "LAP ${completedLap + 1}/$totalLaps"
                                    }
                                    bannerTimerSec = 2.2f
                                }
                            }
                        )
                        if (kart.landingShakeTrigger) {
                            kart.landingShakeTrigger = false
                            landingShakeCounter++
                        }
                        synthAudio.engineSpeedRatio = kotlin.math.abs(kart.forwardSpeed) / GameConstants.TOP_SPEED
                        synthAudio.isDriftingActive = kart.isDrifting
                    } else if (!kart.isHuman || kart.hasFinishedRace) {
                        // CPU or Finished auto-pilot simulation (when host or solo)
                        if (isHostAuthority || !isMultiplayer) {
                            val (cpuInput, speedScale) = CpuDriver.computeCpuInput(
                                kart = kart,
                                track = currentTrack,
                                allKarts = karts,
                                difficulty = cpuDifficulty,
                                leadingHumanProgress = leadingHumanProgress
                            )
                            handleItemActivation(kart, cpuInput)
                            KartPhysics.stepKart(
                                dt = dt,
                                kart = kart,
                                input = cpuInput,
                                track = currentTrack,
                                allKarts = karts,
                                autoAccelerate = true,
                                smartSteering = true,
                                speedScale = speedScale,
                                onLapCompleted = { completedLap ->
                                    val lapDuration = raceMs - kart.currentLapStartMs
                                    kart.currentLapStartMs = raceMs
                                    if (kart.bestLapMs == 0L || lapDuration < kart.bestLapMs) {
                                        kart.bestLapMs = lapDuration
                                    }
                                    if (completedLap >= totalLaps && !kart.hasFinishedRace) {
                                        kart.hasFinishedRace = true
                                        kart.finishTimeMs = raceMs
                                        if (kart.racePosition == 1) {
                                            showToast("${kart.playerName} finished 1st!", 2.5f)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Step Items & Projectiles
                if (isHostAuthority || !isMultiplayer) {
                    ItemsEngine.stepProjectilesAndPickups(
                        dt = dt,
                        track = currentTrack,
                        karts = karts,
                        itemBoxes = itemBoxes,
                        projectiles = projectiles,
                        itemMode = itemMode,
                        onHitEvent = { victimSlot, attackerSlot, _ ->
                            synthAudio.playItemHitSound()
                            val attackerName = karts.firstOrNull { it.slotIndex == attackerSlot }?.playerName ?: "Racer"
                            val victimName = karts.firstOrNull { it.slotIndex == victimSlot }?.playerName ?: "Racer"
                            if (victimSlot == localSlotIndex) {
                                showToast("Hit by $attackerName!", 2.0f)
                            } else if (attackerSlot == localSlotIndex) {
                                showToast("Hit $victimName!", 1.6f)
                            }
                        },
                        onBoxPickedUp = { slot, _, _ ->
                            if (slot == localSlotIndex) {
                                synthAudio.playItemPickupBlip()
                            }
                        }
                    )
                }

                // Update live race positions (1..N) based on totalRaceProgress
                val sorted = karts.sortedWith(
                    compareByDescending<KartRuntime> { it.hasFinishedRace }
                        .thenBy { if (it.hasFinishedRace) it.finishTimeMs else Long.MAX_VALUE }
                        .thenByDescending { it.totalRaceProgress }
                )
                for (idx in sorted.indices) {
                    sorted[idx].racePosition = idx + 1
                }

                if (phase == RacePhase.FINISHED) {
                    finishDelaySec -= dt
                    if (finishDelaySec <= 0f) {
                        // Fill any unfinished racers' estimated times and transition to RESULTS
                        for (k in karts) {
                            if (!k.hasFinishedRace) {
                                k.hasFinishedRace = true
                                k.finishTimeMs = raceMs + (k.racePosition * 1250L)
                            }
                        }
                        phase = RacePhase.RESULTS
                        synthAudio.isEngineActive = false
                    }
                }
            }

            RacePhase.RESULTS -> {
                showroomAngleRad += dt * 0.5f
                synthAudio.isEngineActive = false
            }
        }
    }

    private fun handleItemActivation(kart: KartRuntime, input: TouchInputSnapshot) {
        if (kart.heldItem == ItemType.NONE || kart.rouletteTimerSec > 0f) return

        // Eraser shield hold check
        if (kart.heldItem == ItemType.ERASER && input.itemHeldDown) {
            kart.shieldEraserHeld = true
            return
        }

        val shouldFire = input.itemJustPressed || (kart.shieldEraserHeld && !input.itemHeldDown)
        if (!shouldFire) return

        kart.shieldEraserHeld = false
        val fwdX = sin(kart.headingRad)
        val fwdZ = cos(kart.headingRad)

        if (isMultiplayer && !isHostAuthority && kart.slotIndex == localSlotIndex) {
            onLocalClientItemAction?.invoke(kart.heldItem, input.itemFireBackward)
        }

        executeKartItemAction(kart, input.itemFireBackward, fwdX, fwdZ)
    }

    @Synchronized
    fun executeKartItemAction(kart: KartRuntime, fireBackward: Boolean, fwdX: Float, fwdZ: Float) {
        when (kart.heldItem) {
            ItemType.MARBLE -> {
                val dirSign = if (fireBackward) -1f else 1f
                val vx = fwdX * GameConstants.MARBLE_SPEED * dirSign
                val vz = fwdZ * GameConstants.MARBLE_SPEED * dirSign
                projectiles.add(
                    WorldProjectile(
                        id = nextProjectileId++,
                        kind = ProjectileKind.MARBLE,
                        ownerSlot = kart.slotIndex,
                        x = kart.x + fwdX * 2.2f * dirSign,
                        y = kart.y + 0.5f,
                        z = kart.z + fwdZ * 2.2f * dirSign,
                        vx = vx,
                        vz = vz,
                        headingRad = kart.headingRad
                    )
                )
                kart.heldItem = ItemType.NONE
            }

            ItemType.ERASER -> {
                projectiles.add(
                    WorldProjectile(
                        id = nextProjectileId++,
                        kind = ProjectileKind.ERASER,
                        ownerSlot = kart.slotIndex,
                        x = kart.x - fwdX * 2.3f,
                        y = kart.y + 0.35f,
                        z = kart.z - fwdZ * 2.3f,
                        vx = 0f,
                        vz = 0f,
                        bouncesRemaining = 0,
                        lifeRemainingSec = 18f
                    )
                )
                kart.heldItem = ItemType.NONE
            }

            ItemType.BATTERY -> {
                KartPhysics.applyBoost(kart, GameConstants.BATTERY_BOOST_SEC)
                if (kart.slotIndex == localSlotIndex) synthAudio.playBoostWhoosh()
                kart.heldItem = ItemType.NONE
            }

            ItemType.TRIPLE_TACKS -> {
                if (kart.orbitingTacksCount <= 0) kart.orbitingTacksCount = 3
                kart.orbitingTacksCount--
                projectiles.add(
                    WorldProjectile(
                        id = nextProjectileId++,
                        kind = ProjectileKind.TACK,
                        ownerSlot = kart.slotIndex,
                        x = kart.x + fwdX * 2.1f,
                        y = kart.y + 0.4f,
                        z = kart.z + fwdZ * 2.1f,
                        vx = fwdX * 38f,
                        vz = fwdZ * 38f,
                        bouncesRemaining = 2,
                        lifeRemainingSec = 3.5f
                    )
                )
                if (kart.orbitingTacksCount <= 0) {
                    kart.heldItem = ItemType.NONE
                }
            }

            ItemType.PAPER_PLANE -> {
                val targetPos = (kart.racePosition - 1).coerceAtLeast(1)
                val targetKart = karts.firstOrNull { it.racePosition == targetPos && it.slotIndex != kart.slotIndex }
                    ?: karts.firstOrNull { it.slotIndex != kart.slotIndex }
                projectiles.add(
                    WorldProjectile(
                        id = nextProjectileId++,
                        kind = ProjectileKind.PAPER_PLANE,
                        ownerSlot = kart.slotIndex,
                        x = kart.x + fwdX * 2.0f,
                        y = kart.y + 1.4f,
                        z = kart.z + fwdZ * 2.0f,
                        vx = fwdX * 38f,
                        vz = fwdZ * 38f,
                        headingRad = kart.headingRad,
                        lifeRemainingSec = GameConstants.PAPER_PLANE_FLIGHT_SEC,
                        targetSlot = targetKart?.slotIndex ?: -1
                    )
                )
                kart.heldItem = ItemType.NONE
            }

            ItemType.MAGNET -> {
                kart.magnetTimerSec = GameConstants.MAGNET_DURATION_SEC
                KartPhysics.applyBoost(kart, 1.2f, 1.22f)
                if (kart.slotIndex == localSlotIndex) synthAudio.playBoostWhoosh()
                kart.heldItem = ItemType.NONE
            }

            ItemType.NONE -> Unit
        }
    }

    @Synchronized
    fun buildWorldRenderSnapshot(): WorldRenderSnapshot {
        val kartSnapshots = karts.map { k ->
            val flashHide = k.invulnerableTimerSec > 0f && k.spinOutTimerSec <= 0f &&
                ((k.invulnerableTimerSec * 14f).toInt() % 2 == 1)
            RenderKartSnapshot(
                slotIndex = k.slotIndex,
                name = k.playerName,
                x = k.x,
                y = k.y,
                z = k.z,
                headingRad = k.headingRad,
                visualYawOffsetRad = k.visualYawOffsetRad,
                visualRollRad = k.visualRollRad,
                visualPitchRad = k.visualPitchRad,
                speed = k.forwardSpeed,
                colorHex = k.colorHex,
                bodyType = k.bodyType,
                sparkTier = k.sparkTier,
                isBoosting = k.boostTimerSec > 0f,
                isOffTrack = k.isOffTrack,
                isInvulnerableFlashing = flashHide,
                orbitingTacksCount = k.orbitingTacksCount,
                shieldEraserHeld = k.shieldEraserHeld,
                heldItem = k.heldItem
            )
        }

        val projSnapshots = projectiles.map { p ->
            RenderProjectileSnapshot(
                id = p.id,
                kind = p.kind,
                x = p.x,
                y = p.y,
                z = p.z,
                headingRad = p.headingRad
            )
        }

        val boxSnapshots = itemBoxes.map { b ->
            RenderItemBoxSnapshot(
                id = b.id,
                x = b.x,
                y = b.y,
                z = b.z,
                active = b.active,
                rotationRad = b.rotationRad
            )
        }

        return WorldRenderSnapshot(
            phase = phase,
            trackId = currentTrack.id,
            localSlotIndex = localSlotIndex,
            karts = kartSnapshots,
            projectiles = projSnapshots,
            itemBoxes = boxSnapshots,
            landingShakeSignal = landingShakeCounter,
            showroomCameraAngleRad = showroomAngleRad
        )
    }

    @Synchronized
    fun buildHudState(showRocketTipPref: Boolean): HudState {
        val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex } ?: karts.firstOrNull()
        val speedU = kotlin.math.abs(localKart?.forwardSpeed ?: 0f)
        val speedKmh = (speedU * 4.2f).roundToInt()
        val speedRatio = (speedU / (GameConstants.TOP_SPEED * 1.35f)).coerceIn(0f, 1f)

        val rouletteSpinning = (localKart?.rouletteTimerSec ?: 0f) > 0f
        val rouletteDisplay = if (rouletteSpinning) {
            val cycleIdx = (((localKart?.rouletteTimerSec ?: 0f) * 14f).toInt() % 6) + 1
            ItemType.fromId(cycleIdx)
        } else {
            localKart?.heldItem ?: ItemType.NONE
        }

        val dots = karts.map { k ->
            MinimapDot(
                slotIndex = k.slotIndex,
                x = k.x,
                z = k.z,
                colorHex = k.colorHex,
                isLocal = (k.slotIndex == localSlotIndex)
            )
        }

        val standings = karts.sortedBy { it.racePosition }.map { k ->
            RacerStanding(
                slotIndex = k.slotIndex,
                position = k.racePosition,
                name = k.playerName,
                colorHex = k.colorHex,
                isHuman = k.isHuman,
                isLocal = (k.slotIndex == localSlotIndex),
                totalTimeMs = if (k.finishTimeMs > 0L) k.finishTimeMs else (raceElapsedSec * 1000f).toLong(),
                bestLapMs = k.bestLapMs,
                isNewRecord = (k.slotIndex == localSlotIndex && newRecordAchievedLocal)
            )
        }

        val cdNum = when {
            phase != RacePhase.COUNTDOWN -> -1
            countdownRemainingSec > 2.0f -> 3
            countdownRemainingSec > 1.0f -> 2
            countdownRemainingSec > 0.0f -> 1
            else -> 0
        }

        return HudState(
            phase = phase,
            trackId = currentTrack.id,
            trackName = currentTrack.name,
            position = localKart?.racePosition ?: 1,
            totalRacers = karts.size.coerceAtLeast(1),
            lap = (localKart?.lap ?: 1).coerceAtMost(totalLaps),
            totalLaps = totalLaps,
            raceTimeMs = (raceElapsedSec * 1000f).toLong(),
            bestLapMs = localKart?.bestLapMs ?: 0L,
            speedKmh = speedKmh,
            speedRatio = speedRatio,
            isDrifting = localKart?.isDrifting ?: false,
            driftChargeRatio = ((localKart?.driftChargeUnits ?: 0) / 450f).coerceIn(0f, 1f),
            sparkTier = localKart?.sparkTier ?: 0,
            heldItem = localKart?.heldItem ?: ItemType.NONE,
            isRouletteSpinning = rouletteSpinning,
            rouletteDisplayItem = rouletteDisplay,
            countdownNumber = cdNum,
            isWrongWay = localKart?.isWrongWay ?: false,
            bannerText = bannerText,
            toastText = toastText,
            showRocketStartTip = (phase == RacePhase.COUNTDOWN && showRocketTipPref),
            minimapDots = dots,
            standings = standings
        )
    }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }
}
