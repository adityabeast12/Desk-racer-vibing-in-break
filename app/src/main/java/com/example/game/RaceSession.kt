package com.example.game

import com.example.audio.SynthAudio
import com.example.data.GhostPoseSample
import com.example.data.StoredGhostLap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class RacePhase {
    SHOWROOM_MENU,
    FLYBY_INTRO,
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
    val bodyType: KartBodyType = KartBodyType.CLASSIC,
    val isHuman: Boolean,
    val isLocal: Boolean,
    val totalTimeMs: Long,
    val bestLapMs: Long,
    val isDnf: Boolean = false,
    val isNewRecord: Boolean = false,
    val cupPointsEarned: Int = 0,
    val cupTotalPoints: Int = 0
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
    val wheelSpinRad: Float = 0f,
    val frontWheelSteerRad: Float = 0f,
    val speed: Float,
    val colorHex: Long,
    val paintTintHex: Long = 0L,
    val bodyType: KartBodyType,
    val sparkTier: Int,
    val isBoosting: Boolean,
    val isOffTrack: Boolean,
    val isInvulnerableFlashing: Boolean,
    val isGhostMode: Boolean = false,
    val rocketStallSmoke: Boolean = false,
    val orbitingTacksCount: Int,
    val shieldEraserHeld: Boolean,
    val heldItem: ItemType,
    val coinsHeld: Int = 0,
    val balloonsRemaining: Int = 0
)

data class RenderGhostSnapshot(
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float,
    val bodyType: KartBodyType,
    val colorHex: Long
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

data class RenderCoinSnapshot(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val active: Boolean,
    val rotationRad: Float
)

data class RenderDynamicHazardSnapshot(
    val id: Int,
    val kind: DynamicHazardKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float,
    val rotationRad: Float,
    val colorHex: Long
)

data class WorldRenderSnapshot(
    val phase: RacePhase = RacePhase.SHOWROOM_MENU,
    val gameMode: GameMode = GameMode.QUICK_RACE,
    val trackId: Int = 0,
    val localSlotIndex: Int = 0,
    val countdownLightsLit: Int = 0, // 0, 1, 2, 3 (red), 4 (all GREEN)
    val karts: List<RenderKartSnapshot> = emptyList(),
    val ghostKart: RenderGhostSnapshot? = null,
    val projectiles: List<RenderProjectileSnapshot> = emptyList(),
    val itemBoxes: List<RenderItemBoxSnapshot> = emptyList(),
    val coins: List<RenderCoinSnapshot> = emptyList(),
    val dynamicHazards: List<RenderDynamicHazardSnapshot> = emptyList(),
    val landingShakeSignal: Int = 0,
    val showroomCameraAngleRad: Float = 0f,
    val flybyProgress: Float = 0f,
    val celebrationConfettiActive: Boolean = false,
    val elapsedTimeSec: Float = 0f
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
    val gameMode: GameMode = GameMode.QUICK_RACE,
    val trackId: Int = 0,
    val trackName: String = "STUDY DESK",
    val trackSubtitle: String = "",
    val position: Int = 1,
    val totalRacers: Int = 8,
    val positionDeltaChip: String? = null,
    val lap: Int = 1,
    val totalLaps: Int = 3,
    val raceTimeMs: Long = 0L,
    val bestLapMs: Long = 0L,
    val coinsHeld: Int = 0,
    val balloonsRemaining: Int = 3,
    val speedKmh: Int = 0,
    val speedRatio: Float = 0f,
    val isDrifting: Boolean = false,
    val driftChargeRatio: Float = 0f,
    val sparkTier: Int = 0,
    val heldItem: ItemType = ItemType.NONE,
    val isRouletteSpinning: Boolean = false,
    val rouletteDisplayItem: ItemType = ItemType.NONE,
    val countdownNumber: Int = 3, // 3, 2, 1, 0 ("GO!")
    val dnfCountdownSec: Int = -1, // "Race ends in 0:30" after first finisher
    val isWrongWay: Boolean = false,
    val bannerText: String? = null,
    val toastText: String? = null,
    val showRocketStartTip: Boolean = false,
    val minimapDots: List<MinimapDot> = emptyList(),
    val standings: List<RacerStanding> = emptyList(),
    val grandPrixRaceIndex: Int = 0, // 0..3
    val cupType: CupType = CupType.DESK_CUP
)

private data class RemoteSnapshotEntry(
    val hostTimeMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float,
    val speed: Float,
    val sparkTier: Int,
    val isBoosting: Boolean
)

class RaceSession(
    private val synthAudio: SynthAudio,
    private val onNewBestLapRecorded: (trackId: Int, lapMs: Long) -> Boolean,
    private val onSaveTimeTrialGhost: (StoredGhostLap) -> Unit = {},
    private val onLoadTimeTrialGhost: (trackId: Int) -> StoredGhostLap? = { null }
) {
    var phase: RacePhase = RacePhase.SHOWROOM_MENU
        private set

    var gameMode: GameMode = GameMode.QUICK_RACE
        private set

    var currentTrack: Track = Tracks.studyDesk
        private set

    var totalLaps: Int = 3
        private set

    var itemMode: ItemMode = ItemMode.NORMAL
        private set

    var cpuDifficulty: CpuDifficulty = CpuDifficulty.PRO
        private set

    var ccClass: CcClass = CcClass.CC_150
        private set

    var isMultiplayer: Boolean = false
        private set

    var isHostAuthority: Boolean = true
        private set

    var localSlotIndex: Int = 0
        private set

    // Grand Prix Cup state
    var activeCup: CupType = CupType.DESK_CUP
        private set
    var grandPrixRaceIndex: Int = 0
        private set
    val cupPointsBySlot = IntArray(GameConstants.MAX_RACERS)

    val karts = ArrayList<KartRuntime>(GameConstants.MAX_RACERS)
    val itemBoxes = ArrayList<ActiveItemBox>()
    val coins = ArrayList<ActiveCoin>()
    val projectiles = ArrayList<WorldProjectile>()

    // Remote Kart Snapshot Interpolation Buffer (Part B9: 8 samples per kart)
    private val remoteBuffers = Array(GameConstants.MAX_RACERS) { ArrayDeque<RemoteSnapshotEntry>(8) }

    // Time Trial 20 Hz Ghost recording & playback (Part C)
    private var activeGhostLap: StoredGhostLap? = null
    private val currentLapGhostSamples = ArrayList<GhostPoseSample>(1800)
    private var ghostSampleTickCounter = 0

    private var nextProjectileId = 100
    private var flybyRemainingSec = 0f
    private var countdownRemainingSec = 3.2f
    private var lastCountdownBeepInt = 4
    private var raceElapsedSec = 0f
    private var firstFinisherHappened = false
    private var timeSinceFirstFinishSec = 0f
    private var allHumansFinishedDelaySec = 0f
    private var slowMoFinishTimerSec = 0f
    private var nextFinishPositionToAssign = 1

    private var lastLocalPosition = 1
    private var positionDeltaChip: String? = null
    private var positionDeltaTimerSec = 0f

    private var bannerText: String? = null
    private var bannerTimerSec = 0f
    private var toastText: String? = null
    private var toastTimerSec = 0f
    private var landingShakeCounter = 0
    private var showroomAngleRad = 0f
    private var newRecordAchievedLocal = false

    // Callbacks for multiplayer network sync
    var onLocalClientItemAction: ((ItemType, Boolean) -> Unit)? = null
    var onHostRaceResultsReady: ((List<RacerStanding>) -> Unit)? = null

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
        coins.clear()
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
        for (c in currentTrack.coins) {
            coins.add(ActiveCoin(c.id, c.x, c.y, c.z, active = true))
        }
        synthAudio.isEngineActive = false
        synthAudio.playMusicLoop("audio/music/menu_loop.ogg", 1.0f)
    }

    @Synchronized
    fun startGrandPrixCup(
        cup: CupType,
        laps: Int,
        mode: ItemMode,
        difficulty: CpuDifficulty,
        cc: CcClass,
        localSlot: Int,
        racerConfigs: List<KartRuntime>
    ) {
        activeCup = cup
        grandPrixRaceIndex = 0
        cupPointsBySlot.fill(0)
        startNewRace(
            trackId = cup.trackIds.first(),
            laps = laps,
            mode = mode,
            difficulty = difficulty,
            cc = cc,
            gameMode = GameMode.GRAND_PRIX,
            multiplayer = false,
            hostAuthority = true,
            localSlot = localSlot,
            racerConfigs = racerConfigs,
            skipFlyby = false
        )
    }

    @Synchronized
    fun advanceGrandPrixNextRace(racerConfigs: List<KartRuntime>, localSlot: Int): Boolean {
        if (grandPrixRaceIndex + 1 >= activeCup.trackIds.size) {
            return false
        }
        grandPrixRaceIndex++
        val nextTrackId = activeCup.trackIds[grandPrixRaceIndex]
        startNewRace(
            trackId = nextTrackId,
            laps = totalLaps,
            mode = itemMode,
            difficulty = cpuDifficulty,
            cc = ccClass,
            gameMode = GameMode.GRAND_PRIX,
            multiplayer = false,
            hostAuthority = true,
            localSlot = localSlot,
            racerConfigs = racerConfigs,
            skipFlyby = false
        )
        return true
    }

    @Synchronized
    fun startNewRace(
        trackId: Int,
        laps: Int,
        mode: ItemMode,
        difficulty: CpuDifficulty,
        cc: CcClass = CcClass.CC_150,
        gameMode: GameMode = GameMode.QUICK_RACE,
        multiplayer: Boolean,
        hostAuthority: Boolean,
        localSlot: Int,
        racerConfigs: List<KartRuntime>,
        skipFlyby: Boolean = false
    ) {
        currentTrack = Tracks.byId(trackId)
        totalLaps = if (gameMode == GameMode.TIME_TRIAL) 3 else laps.coerceIn(1, 5)
        itemMode = if (gameMode == GameMode.TIME_TRIAL) ItemMode.OFF else mode
        cpuDifficulty = difficulty
        ccClass = cc
        this.gameMode = gameMode
        isMultiplayer = multiplayer
        isHostAuthority = hostAuthority
        localSlotIndex = localSlot
        newRecordAchievedLocal = false

        for (buf in remoteBuffers) buf.clear()

        karts.clear()
        for (cfg in racerConfigs) {
            val gridSlot = currentTrack.startGrid.getOrElse(cfg.slotIndex) { currentTrack.startGrid.first() }
            KartPhysics.resetKartToGrid(cfg, gridSlot, currentTrack)
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
                    active = (itemMode != ItemMode.OFF && gameMode != GameMode.TIME_TRIAL)
                )
            )
        }
        coins.clear()
        for (c in currentTrack.coins) {
            coins.add(ActiveCoin(c.id, c.x, c.y, c.z, active = true))
        }
        projectiles.clear()

        currentLapGhostSamples.clear()
        ghostSampleTickCounter = 0
        activeGhostLap = if (gameMode == GameMode.TIME_TRIAL) {
            onLoadTimeTrialGhost(trackId)
        } else {
            null
        }

        firstFinisherHappened = false
        timeSinceFirstFinishSec = 0f
        allHumansFinishedDelaySec = 0f
        slowMoFinishTimerSec = 0f
        nextFinishPositionToAssign = 1
        raceElapsedSec = 0f
        lastLocalPosition = karts.firstOrNull { it.slotIndex == localSlotIndex }?.racePosition ?: 1
        positionDeltaChip = null
        positionDeltaTimerSec = 0f

        if (!multiplayer && !skipFlyby) {
            phase = RacePhase.FLYBY_INTRO
            flybyRemainingSec = GameConstants.PRE_RACE_FLYBY_SEC
            bannerText = currentTrack.name
            bannerTimerSec = GameConstants.PRE_RACE_FLYBY_SEC
            synthAudio.isEngineActive = false
            synthAudio.playMusicLoop(currentTrack.musicAssetPath, 1.0f)
        } else {
            enterCountdownPhase()
        }
        toastText = null
        toastTimerSec = 0f
    }

    @Synchronized
    fun skipFlybyIntro() {
        if (phase == RacePhase.FLYBY_INTRO) {
            enterCountdownPhase()
        }
    }

    private fun enterCountdownPhase() {
        phase = RacePhase.COUNTDOWN
        countdownRemainingSec = 3.2f
        lastCountdownBeepInt = 4
        bannerText = "LAP 1/$totalLaps"
        bannerTimerSec = 2.0f
        synthAudio.isEngineActive = true
        synthAudio.playMusicLoop(currentTrack.musicAssetPath, 1.0f)
        synthAudio.playCountdownJingle()
    }

    @Synchronized
    fun showToast(msg: String, durationSec: Float = 2.0f) {
        toastText = msg
        toastTimerSec = durationSec
    }

    /**
     * Part B9 & B4: Buffers remote kart snapshots with host timestamp for 100ms interpolation,
     * and on the Host also runs unwrapped progress & lap completion so remote human finishes count!
     */
    @Synchronized
    fun applyRemoteKartState(
        slotIndex: Int,
        x: Float,
        y: Float,
        z: Float,
        headingRad: Float,
        speed: Float,
        sparkTier: Int,
        isBoosting: Boolean,
        hostTimestampMs: Long = System.currentTimeMillis()
    ) {
        if (slotIndex == localSlotIndex || slotIndex !in 0 until GameConstants.MAX_RACERS) return
        val buf = remoteBuffers[slotIndex]
        if (buf.size >= 8) buf.removeFirst()
        buf.addLast(
            RemoteSnapshotEntry(
                hostTimeMs = hostTimestampMs,
                x = x,
                y = y,
                z = z,
                headingRad = headingRad,
                speed = speed,
                sparkTier = sparkTier,
                isBoosting = isBoosting
            )
        )
    }

    private fun updateInterpolatedRemoteKarts(nowMs: Long, raceMs: Long) {
        val renderTimeMs = nowMs - GameConstants.INTERP_BUFFER_MS
        for (kart in karts) {
            if (kart.slotIndex == localSlotIndex) continue
            // On host, only human client karts use remote buffers; on client, all non-local karts use remote buffers
            if (isHostAuthority && !kart.isHuman) continue

            val buf = remoteBuffers[kart.slotIndex]
            if (buf.isEmpty()) continue

            kart.prevX = kart.x
            kart.prevY = kart.y
            kart.prevZ = kart.z

            val latest = buf.last()
            val targetX: Float
            val targetY: Float
            val targetZ: Float
            val targetHeading: Float
            val targetSpeed: Float
            val sparkTier: Int
            val boosting: Boolean

            if (buf.size == 1 || renderTimeMs >= latest.hostTimeMs) {
                val extrapMs = (renderTimeMs - latest.hostTimeMs).coerceIn(0L, GameConstants.MAX_EXTRAPOLATION_MS)
                val extrapSec = extrapMs / 1000f
                targetX = latest.x + sin(latest.headingRad) * latest.speed * extrapSec
                targetY = latest.y
                targetZ = latest.z + cos(latest.headingRad) * latest.speed * extrapSec
                targetHeading = latest.headingRad
                targetSpeed = latest.speed
                sparkTier = latest.sparkTier
                boosting = latest.isBoosting
            } else {
                var a = buf.first()
                var b = latest
                for (i in 0 until buf.size - 1) {
                    val s0 = buf.elementAt(i)
                    val s1 = buf.elementAt(i + 1)
                    if (renderTimeMs in s0.hostTimeMs..s1.hostTimeMs) {
                        a = s0
                        b = s1
                        break
                    }
                }
                val span = (b.hostTimeMs - a.hostTimeMs).coerceAtLeast(1L).toFloat()
                val t = ((renderTimeMs - a.hostTimeMs).toFloat() / span).coerceIn(0f, 1f)
                targetX = a.x + (b.x - a.x) * t
                targetY = a.y + (b.y - a.y) * t
                targetZ = a.z + (b.z - a.z) * t
                val dAngle = KartPhysics.wrapAngle(b.headingRad - a.headingRad)
                targetHeading = a.headingRad + dAngle * t
                targetSpeed = a.speed + (b.speed - a.speed) * t
                sparkTier = b.sparkTier
                boosting = b.isBoosting
            }

            val err = hypot(targetX - kart.x, targetZ - kart.z)
            if (err > GameConstants.TELEPORT_ERROR_THRESHOLD) {
                kart.x = targetX
                kart.y = targetY
                kart.z = targetZ
            } else {
                kart.x += (targetX - kart.x) * 0.55f
                kart.y += (targetY - kart.y) * 0.55f
                kart.z += (targetZ - kart.z) * 0.55f
            }
            kart.headingRad = targetHeading
            kart.forwardSpeed = targetSpeed
            kart.isDrifting = sparkTier > 0
            kart.driftChargeUnits = when (sparkTier) {
                3 -> GameConstants.SPARK_PURPLE_UNITS
                2 -> GameConstants.SPARK_ORANGE_UNITS
                1 -> GameConstants.SPARK_BLUE_UNITS
                else -> 0
            }
            if (boosting && kart.boostTimerSec <= 0f) {
                kart.boostTimerSec = 0.25f
            }

            // Track unwrapped progress and lap completion for remote karts too (Part B4)
            val nearest = currentTrack.findNearestSampleWindowed(
                kart.x, kart.y, kart.z, kart.lastSampleIndex, 40
            )
            val oldS = kart.trackS
            val newS = nearest.sample.distance
            kart.lastSampleIndex = nearest.sample.index
            kart.trackS = newS
            kart.splineDistance = newS
            val deltaS = currentTrack.wrapSignedDelta(newS - oldS)
            if (abs(deltaS) <= 25f) {
                kart.raceDistance += deltaS
            }
            val wrappedFinish = oldS > currentTrack.totalLength * 0.80f &&
                newS < currentTrack.totalLength * 0.20f && deltaS > 0f
            val reqDist = kart.lapsCompleted * currentTrack.totalLength + 0.85f * currentTrack.totalLength
            if (wrappedFinish && kart.raceDistance >= reqDist && !kart.hasFinishedRace) {
                kart.lapsCompleted += 1
                kart.lap = kart.lapsCompleted + 1
                kart.raceDistance = kart.lapsCompleted * currentTrack.totalLength + newS
                handleKartLapCompleted(kart, kart.lapsCompleted, raceMs)
            }
        }
    }

    @Synchronized
    fun convertSlotToCpu(slotIndex: Int, playerName: String) {
        val kart = karts.firstOrNull { it.slotIndex == slotIndex } ?: return
        kart.isHuman = false
        kart.playerName = "$playerName (CPU)"
        showToast("$playerName left — CPU took over", 2.5f)
    }

    @Synchronized
    fun applyHostFinalResults(remoteStandings: List<RacerStanding>) {
        for (st in remoteStandings) {
            val k = karts.firstOrNull { it.slotIndex == st.slotIndex } ?: continue
            k.frozenFinishPosition = st.position
            k.racePosition = st.position
            k.finishTimeMs = st.totalTimeMs
            k.bestLapMs = st.bestLapMs
            k.isDnf = st.isDnf
            k.hasFinishedRace = !st.isDnf
        }
        phase = RacePhase.RESULTS
        synthAudio.isEngineActive = false
    }

    @Synchronized
    fun stepFixed(
        dt: Float,
        localInput: TouchInputSnapshot,
        autoAccelerate: Boolean,
        smartSteering: Boolean,
        showRocketStartTip: Boolean
    ) {
        // Handle finish slow-motion beat (Part D5: time scale 0.4 for 0.6s in solo)
        val effectiveDt = if (slowMoFinishTimerSec > 0f && !isMultiplayer) {
            slowMoFinishTimerSec = (slowMoFinishTimerSec - dt).coerceAtLeast(0f)
            dt * 0.4f
        } else {
            dt
        }

        if (bannerTimerSec > 0f) {
            bannerTimerSec -= dt
            if (bannerTimerSec <= 0f) bannerText = null
        }
        if (toastTimerSec > 0f) {
            toastTimerSec -= dt
            if (toastTimerSec <= 0f) toastText = null
        }
        if (positionDeltaTimerSec > 0f) {
            positionDeltaTimerSec -= dt
            if (positionDeltaTimerSec <= 0f) positionDeltaChip = null
        }

        when (phase) {
            RacePhase.SHOWROOM_MENU -> {
                showroomAngleRad += dt * 0.55f
                for (box in itemBoxes) box.rotationRad += dt * 1.8f
                for (coin in coins) coin.rotationRad += dt * 2.5f
                synthAudio.isEngineActive = false
            }

            RacePhase.FLYBY_INTRO -> {
                flybyRemainingSec -= dt
                for (box in itemBoxes) box.rotationRad += dt * 1.8f
                for (coin in coins) coin.rotationRad += dt * 2.5f
                if (flybyRemainingSec <= 0f || localInput.gasPressed || localInput.driftJustPressed || localInput.itemJustPressed) {
                    enterCountdownPhase()
                }
            }

            RacePhase.COUNTDOWN -> {
                countdownRemainingSec -= dt
                val ceilSec = kotlin.math.ceil(countdownRemainingSec.toDouble()).toInt().coerceIn(0, 3)
                if (ceilSec in 1..3 && ceilSec < lastCountdownBeepInt) {
                    lastCountdownBeepInt = ceilSec
                    synthAudio.playCountdownBeep(isGo = false)
                }

                // Part D2: Rocket Start window is the 0.35s right after "2" goes off (countdownRemainingSec in 0.65f..1.00f).
                // Holding gas before "2" goes off (> 1.15f) stalls the engine (0.6s puff of smoke, no spin).
                val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex }
                if (localKart != null) {
                    if (localInput.gasPressed) {
                        if (countdownRemainingSec > 1.15f) {
                            localKart.rocketStartOverheated = true
                        } else if (!localKart.rocketStartOverheated && countdownRemainingSec in 0.55f..1.15f) {
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
                            localKart.rocketStartStallTimerSec = GameConstants.ROCKET_START_FAIL_SPIN_SEC
                            showToast("ENGINE STALL! (TOO EARLY)", 1.5f)
                        } else if (localKart.rocketStartHoldSec >= 0.08f) {
                            KartPhysics.applyBoost(localKart, GameConstants.ROCKET_START_BOOST_SEC)
                            synthAudio.playBoostWhoosh()
                            showToast("🚀 ROCKET START!", 1.6f)
                        }
                    }
                }
            }

            RacePhase.RACING, RacePhase.FINISHED -> {
                raceElapsedSec += effectiveDt
                val raceMs = (raceElapsedSec * 1000f).toLong()

                if (isMultiplayer) {
                    updateInterpolatedRemoteKarts(System.currentTimeMillis(), raceMs)
                }

                val leadingHumanProgress = karts.filter { it.isHuman && !it.hasFinishedRace }
                    .maxOfOrNull { it.totalRaceProgress } ?: 0f

                for (kart in karts) {
                    if (kart.slotIndex == localSlotIndex && !kart.hasFinishedRace) {
                        // Local human player active driving
                        if (localInput.driftJustPressed && kart.y <= currentTrack.sampleAtDistance(kart.trackS).pos.y + 0.14f) {
                            synthAudio.playHopSound()
                        }
                        handleItemActivation(kart, localInput)
                        KartPhysics.stepKart(
                            dt = effectiveDt,
                            kart = kart,
                            input = localInput,
                            track = currentTrack,
                            allKarts = karts,
                            autoAccelerate = autoAccelerate,
                            smartSteering = smartSteering,
                            speedScale = ccClass.speedScale,
                            raceElapsedSec = raceElapsedSec,
                            onWallBump = { synthAudio.playWallBumpTick() },
                            onBoostTriggered = { synthAudio.playBoostWhoosh() },
                            onLanded = { synthAudio.playLandSound() },
                            onRespawnToast = { showToast("RESPAWN", 1.5f) },
                            onLapCompleted = { completedLap ->
                                handleKartLapCompleted(kart, completedLap, raceMs)
                            }
                        )
                        if (kart.landingShakeTrigger) {
                            kart.landingShakeTrigger = false
                            landingShakeCounter++
                        }
                        synthAudio.engineSpeedRatio = abs(kart.forwardSpeed) / GameConstants.TOP_SPEED
                        synthAudio.isDriftingActive = kart.isDrifting

                        // Record 20 Hz Ghost pose in Time Trial mode (every 3rd 60Hz frame = 50ms)
                        if (gameMode == GameMode.TIME_TRIAL) {
                            ghostSampleTickCounter++
                            if (ghostSampleTickCounter >= 3) {
                                ghostSampleTickCounter = 0
                                if (currentLapGhostSamples.size < 6000) {
                                    currentLapGhostSamples.add(
                                        GhostPoseSample(kart.x, kart.y, kart.z, kart.headingRad)
                                    )
                                }
                            }
                        }
                    } else if (!kart.isHuman || kart.hasFinishedRace) {
                        // CPU or Finished human autopilot simulation (Part B4: finished humans switch to bot autopilot)
                        if (isHostAuthority || !isMultiplayer || kart.slotIndex == localSlotIndex) {
                            val (cpuInput, speedScale) = CpuDriver.computeCpuInput(
                                kart = kart,
                                track = currentTrack,
                                allKarts = karts,
                                projectiles = projectiles,
                                itemBoxes = itemBoxes,
                                coins = coins,
                                difficulty = cpuDifficulty,
                                ccClass = ccClass,
                                leadingHumanProgress = leadingHumanProgress,
                                raceElapsedSec = raceElapsedSec
                            )
                            if (!kart.hasFinishedRace) {
                                handleItemActivation(kart, cpuInput)
                            }
                            KartPhysics.stepKart(
                                dt = effectiveDt,
                                kart = kart,
                                input = cpuInput,
                                track = currentTrack,
                                allKarts = karts,
                                autoAccelerate = true,
                                smartSteering = true,
                                speedScale = speedScale,
                                raceElapsedSec = raceElapsedSec,
                                onLapCompleted = { completedLap ->
                                    handleKartLapCompleted(kart, completedLap, raceMs)
                                }
                            )
                        }
                    }
                }

                // Step Items, Coins & Projectiles
                if (isHostAuthority || !isMultiplayer) {
                    ItemsEngine.stepProjectilesAndPickups(
                        dt = effectiveDt,
                        track = currentTrack,
                        karts = karts,
                        itemBoxes = itemBoxes,
                        coins = coins,
                        projectiles = projectiles,
                        itemMode = itemMode,
                        isTimeTrial = (gameMode == GameMode.TIME_TRIAL),
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
                        },
                        onCoinPickedUp = { slot, _ ->
                            if (slot == localSlotIndex) {
                                synthAudio.playCoinPickup()
                            }
                        }
                    )
                }

                // Update live race positions (1..N), freezing position for finished racers (Part B4)
                val finishedKarts = karts.filter { it.hasFinishedRace }
                    .sortedBy { it.frozenFinishPosition }
                val racingKarts = karts.filter { !it.hasFinishedRace }
                    .sortedByDescending {
                        if (gameMode == GameMode.BATTLE_BALLOONS) {
                            it.balloonsRemaining * 10000f + it.totalRaceProgress
                        } else {
                            it.totalRaceProgress
                        }
                    }

                var posRank = 1
                for (fk in finishedKarts) {
                    fk.racePosition = fk.frozenFinishPosition.coerceAtLeast(posRank)
                    posRank = fk.racePosition + 1
                }
                for (rk in racingKarts) {
                    rk.racePosition = posRank++
                }

                // Part D4: Position change animation chip ("▲2" or "▼3")
                val localNowPos = karts.firstOrNull { it.slotIndex == localSlotIndex }?.racePosition ?: 1
                if (localNowPos != lastLocalPosition && raceElapsedSec > 1.5f) {
                    val improved = localNowPos < lastLocalPosition
                    positionDeltaChip = if (improved) "▲$localNowPos" else "▼$localNowPos"
                    positionDeltaTimerSec = 1.8f
                    lastLocalPosition = localNowPos
                }

                // Part B4: Explicit Race-End Rules
                evaluateRaceEndConditions(effectiveDt, raceMs)
            }

            RacePhase.RESULTS -> {
                showroomAngleRad += dt * 0.5f
                synthAudio.isEngineActive = false
            }
        }
    }

    private fun handleKartLapCompleted(kart: KartRuntime, completedLap: Int, raceMs: Long) {
        if (kart.hasFinishedRace) return
        val lapDuration = (raceMs - kart.currentLapStartMs).coerceAtLeast(1000L)
        kart.currentLapStartMs = raceMs

        val isFasterLap = kart.bestLapMs == 0L || lapDuration < kart.bestLapMs
        if (isFasterLap) {
            kart.bestLapMs = lapDuration
        }

        if (kart.slotIndex == localSlotIndex) {
            if (onNewBestLapRecorded(currentTrack.id, lapDuration)) {
                newRecordAchievedLocal = true
            }
            if (gameMode == GameMode.TIME_TRIAL && isFasterLap && currentLapGhostSamples.isNotEmpty()) {
                val newGhost = StoredGhostLap(
                    trackId = currentTrack.id,
                    lapTimeMs = lapDuration,
                    bodyType = kart.bodyType,
                    colorHex = kart.colorHex,
                    samples = ArrayList(currentLapGhostSamples)
                )
                activeGhostLap = newGhost
                onSaveTimeTrialGhost(newGhost)
            }
            currentLapGhostSamples.clear()
        }

        if (completedLap >= totalLaps) {
            kart.hasFinishedRace = true
            kart.finishTimeMs = raceMs
            kart.frozenFinishPosition = nextFinishPositionToAssign++
            kart.racePosition = kart.frozenFinishPosition

            if (!firstFinisherHappened) {
                firstFinisherHappened = true
                timeSinceFirstFinishSec = 0f
            }

            if (kart.slotIndex == localSlotIndex) {
                phase = RacePhase.FINISHED
                slowMoFinishTimerSec = 0.6f
                synthAudio.playFinishFanfare(kart.frozenFinishPosition)
                bannerText = "FINISHED ${ordinal(kart.frozenFinishPosition)}!"
                bannerTimerSec = 3.5f
            } else if (kart.frozenFinishPosition == 1) {
                showToast("${kart.playerName} finished 1st!", 2.5f)
            }
        } else if (kart.slotIndex == localSlotIndex) {
            if (completedLap + 1 == totalLaps) {
                bannerText = "FINAL LAP!"
                synthAudio.playFinalLapJingle()
            } else {
                bannerText = "LAP ${completedLap + 1}/$totalLaps"
            }
            bannerTimerSec = 2.2f
        }
    }

    private fun evaluateRaceEndConditions(dt: Float, raceMs: Long) {
        if (!isHostAuthority && isMultiplayer) return

        // Battle Balloons end rule
        if (gameMode == GameMode.BATTLE_BALLOONS) {
            val aliveKarts = karts.filter { it.balloonsRemaining > 0 }
            val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex }
            if (aliveKarts.size <= 1 || (localKart != null && localKart.balloonsRemaining <= 0)) {
                finalizeRaceAndEnterResults(raceMs)
                return
            }
        }

        if (firstFinisherHappened) {
            timeSinceFirstFinishSec += dt
        }

        val humanKarts = karts.filter { it.isHuman }
        val allHumansFinished = humanKarts.isNotEmpty() && humanKarts.all { it.hasFinishedRace }
        if (allHumansFinished) {
            allHumansFinishedDelaySec += dt
        }

        val allBotsFinishedToo = karts.all { it.hasFinishedRace }
        val dnfTimeoutReached = firstFinisherHappened &&
            timeSinceFirstFinishSec >= GameConstants.DNF_COUNTDOWN_AFTER_FIRST_FINISH_SEC
        val hardCapReached = raceElapsedSec >= totalLaps * GameConstants.HARD_TIME_CAP_PER_LAP_SEC

        // End race when:
        // (a) all humans have finished (after 2.5s celebration camera), OR all 8 karts in headless bot race finished;
        // (b) 30s elapsed after first finisher;
        // (c) hard time cap (totalLaps * 120s) reached.
        if ((allHumansFinished && allHumansFinishedDelaySec >= 2.5f) ||
            (humanKarts.isEmpty() && allBotsFinishedToo) ||
            dnfTimeoutReached ||
            hardCapReached
        ) {
            finalizeRaceAndEnterResults(raceMs)
        }
    }

    private fun finalizeRaceAndEnterResults(raceMs: Long) {
        val unfinished = karts.filter { !it.hasFinishedRace }
            .sortedByDescending { it.totalRaceProgress }

        val allHumansDone = karts.filter { it.isHuman }.all { it.hasFinishedRace }

        for (k in unfinished) {
            val pos = nextFinishPositionToAssign++
            k.frozenFinishPosition = pos
            k.racePosition = pos
            if (allHumansDone && !k.isHuman) {
                // Estimate close finishing time for trailing bots when human finished early
                k.hasFinishedRace = true
                k.isDnf = false
                k.finishTimeMs = raceMs + (pos * 1150L)
            } else {
                // DNF per Part B4 (30s timeout or hard cap)
                k.isDnf = true
                k.finishTimeMs = raceMs
            }
        }

        // Award Grand Prix points (15, 12, 10, 8, 6, 4, 2, 1)
        if (gameMode == GameMode.GRAND_PRIX) {
            for (k in karts) {
                val pts = GameConstants.GRAND_PRIX_POINTS.getOrElse(k.racePosition - 1) { 0 }
                cupPointsBySlot[k.slotIndex] += pts
            }
        }

        phase = RacePhase.RESULTS
        synthAudio.isEngineActive = false
        val finalStandings = buildStandingsList(raceMs)
        onHostRaceResultsReady?.invoke(finalStandings)
    }

    private fun handleItemActivation(kart: KartRuntime, input: TouchInputSnapshot) {
        if (kart.heldItem == ItemType.NONE || kart.rouletteTimerSec > 0f) return

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

        if (kart.slotIndex == localSlotIndex) {
            synthAudio.playItemFireSound()
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
            val flashHide = k.invulnerableTimerSec > 0f && k.spinOutTimerSec <= 0f && k.ghostTimerSec <= 0f &&
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
                wheelSpinRad = k.wheelSpinRad,
                frontWheelSteerRad = k.frontWheelSteerRad,
                speed = k.forwardSpeed,
                colorHex = k.colorHex,
                paintTintHex = if (k.paintTintTimerSec > 0f) k.paintTintHex else 0L,
                bodyType = k.bodyType,
                sparkTier = k.sparkTier,
                isBoosting = k.boostTimerSec > 0f,
                isOffTrack = k.isOffTrack,
                isInvulnerableFlashing = flashHide,
                isGhostMode = k.ghostTimerSec > 0f,
                rocketStallSmoke = k.rocketStartStallTimerSec > 0f,
                orbitingTacksCount = k.orbitingTacksCount,
                shieldEraserHeld = k.shieldEraserHeld,
                heldItem = k.heldItem,
                coinsHeld = k.coinsHeld,
                balloonsRemaining = if (gameMode == GameMode.BATTLE_BALLOONS) k.balloonsRemaining else 0
            )
        }

        // Ghost Kart interpolation at 20 Hz for Time Trial mode
        val ghostSnap: RenderGhostSnapshot? = if (gameMode == GameMode.TIME_TRIAL && phase == RacePhase.RACING) {
            val ghost = activeGhostLap
            val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex }
            if (ghost != null && ghost.samples.isNotEmpty() && localKart != null) {
                val lapElapsedMs = ((raceElapsedSec * 1000f).toLong() - localKart.currentLapStartMs).coerceAtLeast(0L)
                val exactIdx = (lapElapsedMs / 50f)
                val idx0 = exactIdx.toInt().coerceIn(0, ghost.samples.lastIndex)
                val idx1 = (idx0 + 1).coerceIn(0, ghost.samples.lastIndex)
                val frac = (exactIdx - idx0).coerceIn(0f, 1f)
                val s0 = ghost.samples[idx0]
                val s1 = ghost.samples[idx1]
                val dh = KartPhysics.wrapAngle(s1.headingRad - s0.headingRad)
                RenderGhostSnapshot(
                    x = s0.x + (s1.x - s0.x) * frac,
                    y = s0.y + (s1.y - s0.y) * frac,
                    z = s0.z + (s1.z - s0.z) * frac,
                    headingRad = s0.headingRad + dh * frac,
                    bodyType = ghost.bodyType,
                    colorHex = ghost.colorHex
                )
            } else null
        } else null

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

        val coinSnapshots = coins.map { c ->
            RenderCoinSnapshot(
                id = c.id,
                x = c.x,
                y = c.y,
                z = c.z,
                active = c.active,
                rotationRad = c.rotationRad
            )
        }

        val hazardSnapshots = currentTrack.dynamicHazards.map { h ->
            val phaseAngle = ((raceElapsedSec / h.periodSec) + h.phaseOffset) * (2f * PI.toFloat())
            val offset = sin(phaseAngle) * h.amplitude
            RenderDynamicHazardSnapshot(
                id = h.id,
                kind = h.kind,
                x = h.centerX + h.axisX * offset,
                y = h.centerY,
                z = h.centerZ + h.axisZ * offset,
                radius = h.radius,
                rotationRad = raceElapsedSec * 5f,
                colorHex = h.colorHex
            )
        }

        val lightsLit = when {
            phase != RacePhase.COUNTDOWN -> if (phase == RacePhase.RACING && raceElapsedSec < 1.2f) 4 else 0
            countdownRemainingSec > 2.2f -> 1
            countdownRemainingSec > 1.2f -> 2
            countdownRemainingSec > 0.15f -> 3
            else -> 4
        }

        val flybyProg = if (phase == RacePhase.FLYBY_INTRO) {
            1f - (flybyRemainingSec / GameConstants.PRE_RACE_FLYBY_SEC).coerceIn(0f, 1f)
        } else {
            0f
        }

        return WorldRenderSnapshot(
            phase = phase,
            gameMode = gameMode,
            trackId = currentTrack.id,
            localSlotIndex = localSlotIndex,
            countdownLightsLit = lightsLit,
            karts = kartSnapshots,
            ghostKart = ghostSnap,
            projectiles = projSnapshots,
            itemBoxes = boxSnapshots,
            coins = coinSnapshots,
            dynamicHazards = hazardSnapshots,
            landingShakeSignal = landingShakeCounter,
            showroomCameraAngleRad = showroomAngleRad,
            flybyProgress = flybyProg,
            celebrationConfettiActive = (phase == RacePhase.FINISHED),
            elapsedTimeSec = raceElapsedSec
        )
    }

    private fun buildStandingsList(raceMs: Long): List<RacerStanding> {
        return karts.sortedBy { it.racePosition }.map { k ->
            val ptsEarned = if (gameMode == GameMode.GRAND_PRIX) {
                GameConstants.GRAND_PRIX_POINTS.getOrElse(k.racePosition - 1) { 0 }
            } else 0
            RacerStanding(
                slotIndex = k.slotIndex,
                position = k.racePosition,
                name = k.playerName,
                colorHex = k.colorHex,
                bodyType = k.bodyType,
                isHuman = k.isHuman,
                isLocal = (k.slotIndex == localSlotIndex),
                totalTimeMs = if (k.finishTimeMs > 0L) k.finishTimeMs else raceMs,
                bestLapMs = k.bestLapMs,
                isDnf = k.isDnf,
                isNewRecord = (k.slotIndex == localSlotIndex && newRecordAchievedLocal),
                cupPointsEarned = ptsEarned,
                cupTotalPoints = cupPointsBySlot.getOrElse(k.slotIndex) { 0 }
            )
        }
    }

    @Synchronized
    fun buildHudState(showRocketTipPref: Boolean): HudState {
        val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex } ?: karts.firstOrNull()
        val speedU = abs(localKart?.forwardSpeed ?: 0f)
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

        val raceMs = (raceElapsedSec * 1000f).toLong()
        val standings = buildStandingsList(raceMs)

        val cdNum = when {
            phase != RacePhase.COUNTDOWN -> -1
            countdownRemainingSec > 2.0f -> 3
            countdownRemainingSec > 1.0f -> 2
            countdownRemainingSec > 0.0f -> 1
            else -> 0
        }

        val dnfRemaining = if (firstFinisherHappened && phase == RacePhase.RACING) {
            (GameConstants.DNF_COUNTDOWN_AFTER_FIRST_FINISH_SEC - timeSinceFirstFinishSec)
                .roundToInt().coerceAtLeast(0)
        } else {
            -1
        }

        return HudState(
            phase = phase,
            gameMode = gameMode,
            trackId = currentTrack.id,
            trackName = currentTrack.name,
            trackSubtitle = currentTrack.subtitle,
            position = localKart?.racePosition ?: 1,
            totalRacers = karts.size.coerceAtLeast(1),
            positionDeltaChip = positionDeltaChip,
            lap = (localKart?.lap ?: 1).coerceAtMost(totalLaps),
            totalLaps = totalLaps,
            raceTimeMs = raceMs,
            bestLapMs = localKart?.bestLapMs ?: 0L,
            coinsHeld = localKart?.coinsHeld ?: 0,
            balloonsRemaining = localKart?.balloonsRemaining ?: 3,
            speedKmh = speedKmh,
            speedRatio = speedRatio,
            isDrifting = localKart?.isDrifting ?: false,
            driftChargeRatio = ((localKart?.driftChargeUnits ?: 0) / 450f).coerceIn(0f, 1f),
            sparkTier = localKart?.sparkTier ?: 0,
            heldItem = localKart?.heldItem ?: ItemType.NONE,
            isRouletteSpinning = rouletteSpinning,
            rouletteDisplayItem = rouletteDisplay,
            countdownNumber = cdNum,
            dnfCountdownSec = dnfRemaining,
            isWrongWay = localKart?.isWrongWay ?: false,
            bannerText = bannerText,
            toastText = toastText,
            showRocketStartTip = (phase == RacePhase.COUNTDOWN && showRocketTipPref),
            minimapDots = dots,
            standings = standings,
            grandPrixRaceIndex = grandPrixRaceIndex,
            cupType = activeCup
        )
    }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }
}
