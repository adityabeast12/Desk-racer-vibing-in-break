package com.example.game

import com.example.audio.SynthAudio
import com.example.data.GhostPoseSample
import com.example.data.StoredGhostLap
import com.example.net.ReliableRaceEvent
import com.example.net.UdpKartStatePacket
import com.example.net.WorldSnapshotKartV4
import com.example.net.WorldSnapshotProjectileV4
import com.example.net.WorldSnapshotV4
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
    val hasOverallBestLap: Boolean = false,
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
    val isDrifting: Boolean = false,
    val isOffTrack: Boolean,
    val isInvulnerableFlashing: Boolean,
    val isGhostMode: Boolean = false,
    val rocketStallSmoke: Boolean = false,
    val orbitingTacksCount: Int,
    val shieldEraserHeld: Boolean,
    val heldItem: ItemType,
    val coinsHeld: Int = 0,
    val balloonsRemaining: Int = 0,
    val liveryIndex: Int = 0,
    val paintFinishIndex: Int = 0,
    val draftLevel: Float = 0f
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
    val headingRad: Float,
    val distanceTravelled: Float = 0f
)

data class RenderPylonSnapshot(
    val propId: Int,
    val modelPath: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yawRad: Float,
    val pitchRad: Float,
    val rollRad: Float,
    val scale: Float,
    val radius: Float
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
    val pylons: List<RenderPylonSnapshot> = emptyList(),
    val itemBoxes: List<RenderItemBoxSnapshot> = emptyList(),
    val coins: List<RenderCoinSnapshot> = emptyList(),
    val dynamicHazards: List<RenderDynamicHazardSnapshot> = emptyList(),
    val landingShakeSignal: Int = 0,
    val showroomCameraAngleRad: Float = 0f,
    val isGarageStageActive: Boolean = false,
    val garageZoomFactor: Float = 1.0f,
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
    val isFinished: Boolean = false,
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
    val isBoosting: Boolean,
    val isDrifting: Boolean = false
)

class RaceSession(
    private val synthAudio: SynthAudio,
    private val onNewBestLapRecorded: (trackId: Int, lapMs: Long) -> Boolean = { _, _ -> false },
    private val onSaveTimeTrialGhost: (StoredGhostLap) -> Unit = {},
    private val onLoadTimeTrialGhost: (trackId: Int) -> StoredGhostLap? = { null }
) {
    // Part B10: Single-threaded GameCommandQueue drained at start of each 60 Hz sim tick
    private val commandQueue = ConcurrentLinkedQueue<() -> Unit>()

    fun enqueueCommand(cmd: () -> Unit) {
        commandQueue.add(cmd)
    }

    fun drainCommandQueue() {
        while (true) {
            val cmd = commandQueue.poll() ?: break
            cmd()
        }
    }

    var activeRaceId: Int = 0
        private set

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

    /** True on the last race of the active cup (the one whose results end the cup). */
    val isCupFinale: Boolean
        get() = gameMode == GameMode.GRAND_PRIX && grandPrixRaceIndex >= activeCup.trackIds.size - 1
    val cupPointsBySlot = IntArray(GameConstants.MAX_RACERS)

    val karts = ArrayList<KartRuntime>(GameConstants.MAX_RACERS)
    val itemBoxes = ArrayList<ActiveItemBox>()
    val coins = ArrayList<ActiveCoin>()
    val projectiles = ArrayList<WorldProjectile>()
    val pylons = ArrayList<ActivePylon>()

    private fun rebuildTrackPylons() {
        pylons.clear()
        for (p in currentTrack.placedProps) {
            if (p.isPylon) {
                pylons.add(
                    ActivePylon(
                        propId = p.id,
                        modelPath = p.model,
                        baseX = p.x,
                        baseY = p.y,
                        baseZ = p.z,
                        baseYawRad = p.yawRad,
                        scale = p.scale,
                        radius = p.radius
                    )
                )
            }
        }
    }

    // Remote Kart Snapshot Interpolation Buffer (8 samples per kart)
    private val remoteBuffers = Array(GameConstants.MAX_RACERS) { ArrayDeque<RemoteSnapshotEntry>(8) }

    /** QA: drive the local kart with the CPU driver (playtest capture via `--ez dr_autoplay true`). */
    @Volatile
    var autopilotLocal: Boolean = false

    // v7: remote pose timestamps come from the SENDER's clock (mapped onto ours with a smoothed
    // offset) instead of "when the packet happened to be processed". Packets arrive with Wi-Fi
    // jitter and are only drained on 60 Hz sim ticks, so receipt-time stamps made interpolation
    // spans swing 17-50 ms for equally spaced poses — remote karts visibly sped up / hitched.
    private val senderClockOffsetMs = DoubleArray(GameConstants.MAX_RACERS + 1)
    private val senderClockInit = BooleanArray(GameConstants.MAX_RACERS + 1)

    private fun mapSenderTimeMs(channel: Int, senderMs: Long): Long {
        val localMs = (raceElapsedSec * 1000f).toLong()
        val sample = (localMs - senderMs).toDouble()
        if (!senderClockInit[channel] || kotlin.math.abs(sample - senderClockOffsetMs[channel]) > 400.0) {
            senderClockOffsetMs[channel] = sample
            senderClockInit[channel] = true
        } else if (sample < senderClockOffsetMs[channel]) {
            // a packet that arrived faster than usual is the best latency estimate: follow quickly
            senderClockOffsetMs[channel] += (sample - senderClockOffsetMs[channel]) * 0.3
        } else {
            senderClockOffsetMs[channel] += (sample - senderClockOffsetMs[channel]) * 0.02
        }
        return senderMs + senderClockOffsetMs[channel].toLong()
    }

    private fun pushRemote(slot: Int, entry: RemoteSnapshotEntry) {
        val buf = remoteBuffers[slot]
        val last = buf.lastOrNull()
        if (last != null && entry.hostTimeMs <= last.hostTimeMs) return // stale / reordered UDP
        if (buf.size >= 8) buf.removeFirst()
        buf.addLast(entry)
    }
    private val lastClientItemUseMs = LongArray(GameConstants.MAX_RACERS)

    // Part B8: Client race time tracking from hostRaceTimeMs + (now - snapshotReceiptTime)
    private var lastHostRaceTimeMs: Int = 0
    private var lastSnapshotReceiptTimeMs: Long = 0L

    // Time Trial 20 Hz Ghost recording & playback
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
    private var lastBroadcastCountdownSec = -1
    private var allHumansFinishedDelaySec = 0f
    private var slowMoFinishTimerSec = 0f
    private var nextFinishPositionToAssign = 1

    // Part B6: Host position hysteresis (0.75 u for 120 ms)
    private val overtakeHoldTimersSec = FloatArray(GameConstants.MAX_RACERS * GameConstants.MAX_RACERS)

    // Part C1: Position display animation cooldown (120 ms) and >= 400 ms duration before showing ▲/▼ chip
    private var displayedLocalPosition = 1
    private var displayPosCooldownSec = 0f
    private var candidateStablePos = 1
    private var candidateStableHoldSec = 0f
    private var chipAnnouncedPos = 1
    private var positionDeltaChip: String? = null
    private var positionDeltaTimerSec = 0f

    private var bannerText: String? = null
    private var bannerTimerSec = 0f
    private var toastText: String? = null
    private var toastTimerSec = 0f
    private var landingShakeCounter = 0

    // Part E3: Showroom & Garage 3D Stage controls (drag inertia, pinch zoom 0.8-1.4x, 4s auto-rotate pause)
    private var showroomAngleRad = 0f
    private var garageAngularVelRadPerSec = 0f
    private var garageAutoRotatePauseSec = 0f
    var isGarageStageActive: Boolean = false
    var garageZoomFactor: Float = 1.0f
        private set
    private var localPaintFinishIndex: Int = 0

    private var newRecordAchievedLocal = false

    // Callbacks for multiplayer network sync
    var onLocalClientItemAction: ((ItemType, Boolean) -> Unit)? = null
    var onHostEmitReliableEvent: ((ReliableRaceEvent) -> Unit)? = null
    var onHostRaceResultsReady: ((List<RacerStanding>) -> Unit)? = null
    var onLocalPlayerHitHaptic: (() -> Unit)? = null

    init {
        setupShowroom(
            playerName = "RACER",
            colorHex = GameConstants.KART_COLORS[0],
            bodyType = KartBodyType.CLASSIC,
            trackId = 0
        )
    }

    @Synchronized
    fun updateGarageStageControls(
        yawDeltaRad: Float = 0f,
        flingVelocityRadPerSec: Float = 0f,
        zoomScaleMultiplier: Float = 1f,
        resetView: Boolean = false
    ) {
        if (resetView) {
            showroomAngleRad = 0.55f
            garageAngularVelRadPerSec = 0f
            garageZoomFactor = 1.0f
            garageAutoRotatePauseSec = 0f
            return
        }
        if (abs(yawDeltaRad) > 0.0001f) {
            showroomAngleRad = (showroomAngleRad + yawDeltaRad) % (2f * PI.toFloat())
            garageAutoRotatePauseSec = 4.0f
        }
        if (abs(flingVelocityRadPerSec) > 0.01f) {
            garageAngularVelRadPerSec = flingVelocityRadPerSec.coerceIn(-8f, 8f)
            garageAutoRotatePauseSec = 4.0f
        }
        if (abs(zoomScaleMultiplier - 1f) > 0.0001f) {
            garageZoomFactor = (garageZoomFactor * zoomScaleMultiplier).coerceIn(0.8f, 1.4f)
            garageAutoRotatePauseSec = 4.0f
        }
    }

    @Synchronized
    fun setupShowroom(
        playerName: String,
        colorHex: Long,
        bodyType: KartBodyType,
        trackId: Int,
        liveryIndex: Int = 0,
        paintFinishIndex: Int = localPaintFinishIndex
    ) {
        commandQueue.clear()
        phase = RacePhase.SHOWROOM_MENU
        localPaintFinishIndex = paintFinishIndex.coerceIn(0, 3)
        currentTrack = Tracks.byId(trackId)
        karts.clear()
        projectiles.clear()
        itemBoxes.clear()
        coins.clear()
        rebuildTrackPylons()
        val slot0 = currentTrack.startGrid.first()
        val demoKart = KartRuntime(
            slotIndex = 0,
            playerName = playerName,
            isHuman = true,
            isLocalPlayer = true,
            colorHex = colorHex,
            bodyType = bodyType,
            liveryIndex = liveryIndex
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
        synthAudio.isBoostingActive = false
        synthAudio.updateRemoteKartEngines(0)
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
        skipFlyby: Boolean = false,
        raceId: Int = 0,
        initialCountdownSec: Float = 3.2f
    ) {
        commandQueue.clear()
        activeRaceId = raceId
        isGarageStageActive = false
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
        senderClockInit.fill(false)
        lastClientItemUseMs.fill(0L)
        overtakeHoldTimersSec.fill(0f)
        lastHostRaceTimeMs = 0
        lastSnapshotReceiptTimeMs = 0L

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
        rebuildTrackPylons()

        currentLapGhostSamples.clear()
        ghostSampleTickCounter = 0
        activeGhostLap = if (gameMode == GameMode.TIME_TRIAL) {
            onLoadTimeTrialGhost(trackId)
        } else {
            null
        }

        firstFinisherHappened = false
        timeSinceFirstFinishSec = 0f
        lastBroadcastCountdownSec = -1
        allHumansFinishedDelaySec = 0f
        slowMoFinishTimerSec = 0f
        nextFinishPositionToAssign = 1
        raceElapsedSec = 0f

        val startPos = karts.firstOrNull { it.slotIndex == localSlotIndex }?.racePosition ?: 1
        displayedLocalPosition = startPos
        displayPosCooldownSec = 0f
        candidateStablePos = startPos
        candidateStableHoldSec = 0f
        chipAnnouncedPos = startPos
        positionDeltaChip = null
        positionDeltaTimerSec = 0f

        if (!multiplayer && !skipFlyby) {
            phase = RacePhase.FLYBY_INTRO
            flybyRemainingSec = GameConstants.PRE_RACE_FLYBY_SEC
            bannerText = currentTrack.name
            bannerTimerSec = 1.6f
            synthAudio.isEngineActive = false
            synthAudio.playMusicLoop(currentTrack.musicAssetPath, 1.0f)
        } else {
            enterCountdownPhase(initialCountdownSec)
        }
        toastText = null
        toastTimerSec = 0f
    }

    @Synchronized
    fun skipFlybyIntro() {
        if (phase == RacePhase.FLYBY_INTRO) {
            enterCountdownPhase(3.2f)
        }
    }

    private fun enterCountdownPhase(initialCountdownSec: Float = 3.2f) {
        phase = RacePhase.COUNTDOWN
        countdownRemainingSec = initialCountdownSec.coerceIn(0.05f, 4.0f)
        lastCountdownBeepInt = 4
        bannerText = "LAP 1/$totalLaps"
        bannerTimerSec = 1.6f // Part F5: banners fade after 1.6s
        synthAudio.isEngineActive = true
        synthAudio.playMusicLoop(currentTrack.musicAssetPath, 1.0f)
        synthAudio.playCountdownJingle()
    }

    @Synchronized
    fun showToast(msg: String, durationSec: Float = 1.6f) {
        toastText = msg
        toastTimerSec = durationSec
    }

    /**
     * Part B2 & B10: Host receives ClientKartState from a remote human player.
     */
    fun onHostReceiveClientKartState(pkt: UdpKartStatePacket, clientPingMs: Int = 12) {
        enqueueCommand {
            applyClientKartStateOnHostInternal(pkt, clientPingMs)
        }
    }

    /**
     * Part B4 & B5: Host receives reliable TCP OP_ITEM_ACTION from client.
     */
    fun onHostReceiveClientItemAction(slotIndex: Int, item: ItemType, backward: Boolean, clientPingMs: Int = 12) {
        enqueueCommand {
            handleHostAuthoritativeClientItemUse(slotIndex, backward, clientPingMs)
        }
    }

    @Synchronized
    private fun applyClientKartStateOnHostInternal(pkt: UdpKartStatePacket, clientPingMs: Int) {
        if (!isHostAuthority) return
        val slot = pkt.slotIndex
        if (slot == localSlotIndex || slot !in 0 until GameConstants.MAX_RACERS) return
        val kart = karts.firstOrNull { it.slotIndex == slot } ?: return

        val stampMs = mapSenderTimeMs(slot, pkt.raceTimeMs.toLong())
        pushRemote(
            slot,
            RemoteSnapshotEntry(
                hostTimeMs = stampMs,
                x = pkt.x,
                y = pkt.y,
                z = pkt.z,
                headingRad = pkt.headingRad,
                speed = pkt.speed,
                sparkTier = pkt.sparkTier,
                isBoosting = pkt.isBoosting,
                isDrifting = pkt.isDrifting
            )
        )
        kart.frontWheelSteerRad = (-pkt.steer * 0.42f).coerceIn(-0.45f, 0.45f)

        // Part B2: Input edges (shield hold + item press repeated in 3 consecutive packets)
        if (kart.heldItem == ItemType.ERASER && pkt.itemHeldEdge) {
            kart.shieldEraserHeld = true
        } else if (kart.shieldEraserHeld && !pkt.itemHeldEdge) {
            // Released held eraser shield -> drop eraser behind
            handleHostAuthoritativeClientItemUse(slot, true, clientPingMs)
        }

        if (pkt.itemPressedEdge) {
            handleHostAuthoritativeClientItemUse(slot, pkt.itemBackwardEdge, clientPingMs)
        }
    }

    @Synchronized
    private fun handleHostAuthoritativeClientItemUse(slot: Int, fireBackward: Boolean, clientPingMs: Int) {
        if (!isHostAuthority) return
        val nowMs = (raceElapsedSec * 1000f).toLong()
        if (lastClientItemUseMs[slot] > 0L && nowMs - lastClientItemUseMs[slot] < 220L) return // Deduplicate 3-packet edge repeat
        val kart = karts.firstOrNull { it.slotIndex == slot } ?: return

        // Allow client to fast-forward roulette after 0.25s just like local player
        if (kart.rouletteTimerSec > 0f && kart.rouletteTimerSec < GameConstants.ITEM_ROULETTE_SEC - 0.25f && kart.pendingItem != ItemType.NONE) {
            kart.rouletteTimerSec = 0f
            kart.heldItem = kart.pendingItem
            kart.pendingItem = ItemType.NONE
            if (kart.heldItem == ItemType.TRIPLE_TACKS) {
                kart.orbitingTacksCount = 3
            }
        }

        val usedItem = kart.heldItem
        if (usedItem == ItemType.NONE || kart.rouletteTimerSec > 0f) return

        lastClientItemUseMs[slot] = nowMs
        kart.shieldEraserHeld = false
        val fwdX = sin(kart.headingRad)
        val fwdZ = cos(kart.headingRad)
        val rttHalfSec = (clientPingMs * 0.0005f).coerceIn(0f, 0.15f)
        val spawned = executeKartItemAction(kart, fireBackward, fwdX, fwdZ, advanceSec = rttHalfSec)

        onHostEmitReliableEvent?.invoke(
            ReliableRaceEvent.ItemUsed(activeRaceId, slot, usedItem, fireBackward)
        )
        if (spawned != null) {
            onHostEmitReliableEvent?.invoke(
                ReliableRaceEvent.ProjectileSpawned(
                    raceId = activeRaceId,
                    id = spawned.id,
                    kind = spawned.kind,
                    ownerSlot = spawned.ownerSlot,
                    x = spawned.x,
                    y = spawned.y,
                    z = spawned.z,
                    vx = spawned.vx,
                    vz = spawned.vz,
                    headingRad = spawned.headingRad
                )
            )
        }
    }

    /**
     * Legacy helper retained for direct pose injection in tests.
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

    /**
     * Part B3: Builds the authoritative WorldSnapshotV4 on the Host to broadcast at 30 Hz.
     */
    @Synchronized
    fun buildWorldSnapshotV4(raceId: Int = activeRaceId, seq: Int = 0): WorldSnapshotV4 {
        val kartStates = ArrayList<WorldSnapshotKartV4>(karts.size)
        for (k in karts) {
            val effectiveItem = if (k.rouletteTimerSec > 0f && k.pendingItem != ItemType.NONE) {
                k.pendingItem
            } else {
                k.heldItem
            }
            kartStates.add(
                WorldSnapshotKartV4(
                    slot = k.slotIndex,
                    x = k.x,
                    y = k.y,
                    z = k.z,
                    headingRad = k.headingRad,
                    speed = k.forwardSpeed,
                    boosting = k.boostTimerSec > 0f,
                    drifting = k.isDrifting,
                    sparkTier = k.sparkTier,
                    spinningOut = k.spinOutTimerSec > 0f,
                    invulnerable = k.invulnerableTimerSec > 0f,
                    finished = k.hasFinishedRace,
                    respawning = k.isRespawning,
                    shield = k.shieldEraserHeld,
                    orbitingTacksCount = k.orbitingTacksCount,
                    heldItem = effectiveItem,
                    rouletteRemainingMs = (k.rouletteTimerSec * 1000f).toInt().coerceAtLeast(0),
                    coins = k.coinsHeld,
                    lap = k.lap,
                    racePosition = k.racePosition,
                    raceDistance = k.raceDistance
                )
            )
        }

        var boxesMask = 0
        for (i in 0 until itemBoxes.size.coerceAtMost(32)) {
            if (itemBoxes[i].active) {
                boxesMask = boxesMask or (1 shl i)
            }
        }
        var coinsMask = 0
        for (i in 0 until coins.size.coerceAtMost(32)) {
            if (coins[i].active) {
                coinsMask = coinsMask or (1 shl i)
            }
        }

        val projStates = ArrayList<WorldSnapshotProjectileV4>(projectiles.size.coerceAtMost(16))
        for (i in 0 until projectiles.size.coerceAtMost(16)) {
            val p = projectiles[i]
            projStates.add(
                WorldSnapshotProjectileV4(
                    id = p.id,
                    kind = p.kind,
                    ownerSlot = p.ownerSlot,
                    x = p.x,
                    y = p.y,
                    z = p.z,
                    headingRad = p.headingRad
                )
            )
        }

        return WorldSnapshotV4(
            raceId = raceId,
            seq = seq,
            hostRaceTimeMs = (raceElapsedSec * 1000f).toInt(),
            phase = phase,
            karts = kartStates,
            activeBoxesMask = boxesMask,
            activeCoinsMask = coinsMask,
            projectiles = projStates,
            hazardPhaseSec = raceElapsedSec
        )
    }

    /**
     * Part B3, B5, B6, B8: Client applies WorldSnapshotV4 from Host.
     */
    fun onClientReceiveWorldSnapshot(snap: WorldSnapshotV4, receiptTimeMs: Long = System.currentTimeMillis()) {
        enqueueCommand {
            applyWorldSnapshotV4Internal(snap, receiptTimeMs)
        }
    }

    @Synchronized
    fun applyWorldSnapshotV4Internal(snap: WorldSnapshotV4, receiptTimeMs: Long = System.currentTimeMillis()) {
        if (isHostAuthority) return
        if (activeRaceId != 0 && snap.raceId != 0 && snap.raceId != activeRaceId) return

        lastHostRaceTimeMs = snap.hostRaceTimeMs
        lastSnapshotReceiptTimeMs = receiptTimeMs

        val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex }
        val localBoxPending = (localKart?.clientBoxPickupPendingSec ?: 0f) > 0f

        // 1. Sync Item Boxes bitmask (preserving local client's 150ms predicted pickup hide)
        for (i in 0 until itemBoxes.size.coerceAtMost(32)) {
            val hostActive = (snap.activeBoxesMask and (1 shl i)) != 0
            if (!localBoxPending || hostActive.not()) {
                itemBoxes[i].active = hostActive
            }
        }

        // 2. Sync Coins bitmask
        for (i in 0 until coins.size.coerceAtMost(32)) {
            coins[i].active = (snap.activeCoinsMask and (1 shl i)) != 0
        }

        // 3. Sync Projectiles (self-healing if a TCP event was delayed)
        val incomingIds = HashSet<Int>(snap.projectiles.size)
        for (sp in snap.projectiles) {
            incomingIds.add(sp.id)
            val existing = projectiles.firstOrNull { it.id == sp.id }
            if (existing != null) {
                existing.x = sp.x
                existing.y = sp.y
                existing.z = sp.z
                existing.headingRad = sp.headingRad
            } else {
                val spd = if (sp.kind == ProjectileKind.ERASER) 0f else GameConstants.MARBLE_SPEED
                projectiles.add(
                    WorldProjectile(
                        id = sp.id,
                        kind = sp.kind,
                        ownerSlot = sp.ownerSlot,
                        x = sp.x,
                        y = sp.y,
                        z = sp.z,
                        vx = sin(sp.headingRad) * spd,
                        vz = cos(sp.headingRad) * spd,
                        headingRad = sp.headingRad
                    )
                )
            }
        }
        projectiles.removeAll { it.id !in incomingIds }

        // 4. Sync Karts (Part B6: Host racePosition and lap are One Truth for ALL karts)
        val snapStampMs = mapSenderTimeMs(GameConstants.MAX_RACERS, snap.hostRaceTimeMs.toLong())
        for (ks in snap.karts) {
            val kart = karts.firstOrNull { it.slotIndex == ks.slot } ?: continue
            kart.racePosition = ks.racePosition
            kart.lap = ks.lap
            kart.lapsCompleted = (ks.lap - 1).coerceAtLeast(0)
            kart.coinsHeld = ks.coins

            if (ks.slot != localSlotIndex) {
                pushRemote(
                    ks.slot,
                    RemoteSnapshotEntry(
                        hostTimeMs = snapStampMs,
                        x = ks.x,
                        y = ks.y,
                        z = ks.z,
                        headingRad = ks.headingRad,
                        speed = ks.speed,
                        sparkTier = ks.sparkTier,
                        isBoosting = ks.boosting,
                        isDrifting = ks.drifting
                    )
                )
                kart.heldItem = ks.heldItem
                kart.rouletteTimerSec = ks.rouletteRemainingMs / 1000f
                kart.shieldEraserHeld = ks.shield
                kart.orbitingTacksCount = ks.orbitingTacksCount
                kart.hasFinishedRace = ks.finished
                kart.isRespawning = ks.respawning
                kart.raceDistance = ks.raceDistance
                if (ks.spinningOut && kart.spinOutTimerSec <= 0f) {
                    kart.spinOutTimerSec = GameConstants.SPIN_OUT_DURATION_SEC * 0.65f
                }
            } else {
                // Local Client Kart authoritative state reconciliation (Part B4 & B5)
                if (ks.spinningOut && kart.spinOutTimerSec <= 0f && kart.invulnerableTimerSec <= 0f) {
                    KartPhysics.triggerSpinOut(kart)
                    synthAudio.playItemHitSound()
                    onLocalPlayerHitHaptic?.invoke()
                }
                if (ks.rouletteRemainingMs > 0 || ks.heldItem != ItemType.NONE) {
                    kart.clientBoxPickupPendingSec = 0f
                    if (kart.clientUsedItemHoldbackSec <= 0f) {
                        if (ks.rouletteRemainingMs > 0) {
                            kart.pendingItem = ks.heldItem
                            if (kart.rouletteTimerSec <= 0f) {
                                kart.rouletteTimerSec = ks.rouletteRemainingMs / 1000f
                            }
                        } else {
                            kart.heldItem = ks.heldItem
                            kart.pendingItem = ItemType.NONE
                            kart.rouletteTimerSec = 0f
                            kart.orbitingTacksCount = ks.orbitingTacksCount
                            kart.shieldEraserHeld = ks.shield
                        }
                    }
                } else if (kart.clientBoxPickupPendingSec <= 0f) {
                    // Host says local player has no item
                    kart.heldItem = ItemType.NONE
                    kart.pendingItem = ItemType.NONE
                    kart.rouletteTimerSec = 0f
                    kart.clientPreUseItem = ItemType.NONE
                    kart.clientUsedItemHoldbackSec = 0f
                }

                if (ks.finished && !kart.hasFinishedRace) {
                    kart.hasFinishedRace = true
                    kart.frozenFinishPosition = ks.racePosition
                    phase = RacePhase.FINISHED
                    synthAudio.playFinishFanfare(ks.racePosition)
                    bannerText = "FINISHED ${ordinal(ks.racePosition)}!"
                    bannerTimerSec = 1.6f
                }
            }
        }
    }

    /**
     * Part B4: Client applies ordered ReliableRaceEvent from Host (including when slot == localSlotIndex).
     */
    fun onClientReceiveReliableEvent(event: ReliableRaceEvent) {
        enqueueCommand {
            applyReliableRaceEventInternal(event)
        }
    }

    @Synchronized
    fun applyReliableRaceEventInternal(event: ReliableRaceEvent) {
        if (isHostAuthority) return
        if (activeRaceId != 0 && event.raceId != 0 && event.raceId != activeRaceId) return

        when (event) {
            is ReliableRaceEvent.ItemGranted -> {
                val kart = karts.firstOrNull { it.slotIndex == event.slot } ?: return
                kart.clientBoxPickupPendingSec = 0f
                kart.pendingItem = event.item
                kart.rouletteTimerSec = (event.rouletteMs / 1000f).coerceAtLeast(0.2f)
                if (event.slot == localSlotIndex) {
                    synthAudio.playItemPickupBlip()
                }
            }

            is ReliableRaceEvent.ItemUsed -> {
                val kart = karts.firstOrNull { it.slotIndex == event.slot } ?: return
                if (event.slot == localSlotIndex) {
                    // Host confirmed our item use!
                    kart.clientUsedItemHoldbackSec = 0f
                    kart.clientPreUseItem = ItemType.NONE
                } else {
                    if (event.item == ItemType.BATTERY) {
                        KartPhysics.applyBoost(kart, GameConstants.BATTERY_BOOST_SEC)
                    } else if (event.item == ItemType.MAGNET) {
                        kart.magnetTimerSec = GameConstants.MAGNET_DURATION_SEC
                        KartPhysics.applyBoost(kart, 1.2f, 1.22f)
                    }
                    if (event.item != ItemType.TRIPLE_TACKS || kart.orbitingTacksCount <= 1) {
                        kart.heldItem = ItemType.NONE
                        kart.orbitingTacksCount = 0
                    } else {
                        kart.orbitingTacksCount--
                    }
                }
            }

            is ReliableRaceEvent.ProjectileSpawned -> {
                val existing = projectiles.firstOrNull { it.id == event.id }
                if (existing == null) {
                    projectiles.add(
                        WorldProjectile(
                            id = event.id,
                            kind = event.kind,
                            ownerSlot = event.ownerSlot,
                            x = event.x,
                            y = event.y,
                            z = event.z,
                            vx = event.vx,
                            vz = event.vz,
                            headingRad = event.headingRad
                        )
                    )
                }
            }

            is ReliableRaceEvent.Hit -> {
                val victim = karts.firstOrNull { it.slotIndex == event.victimSlot }
                if (victim != null) {
                    KartPhysics.triggerSpinOut(victim)
                }
                val attackerName = karts.firstOrNull { it.slotIndex == event.attackerSlot }?.playerName ?: "Racer"
                val victimName = victim?.playerName ?: "Racer"
                if (event.victimSlot == localSlotIndex) {
                    synthAudio.playItemHitSound()
                    onLocalPlayerHitHaptic?.invoke()
                    showToast("Hit by $attackerName!", 1.6f)
                } else if (event.attackerSlot == localSlotIndex) {
                    synthAudio.playItemHitSound()
                    showToast("Hit $victimName!", 1.6f)
                }
            }

            is ReliableRaceEvent.CoinPicked -> {
                val kart = karts.firstOrNull { it.slotIndex == event.slot } ?: return
                kart.coinsHeld = event.totalCoins
                if (event.slot == localSlotIndex) {
                    synthAudio.playCoinPickup()
                }
            }

            is ReliableRaceEvent.LapCompleted -> {
                val kart = karts.firstOrNull { it.slotIndex == event.slot } ?: return
                kart.lapsCompleted = event.lap
                kart.lap = (event.lap + 1).coerceAtMost(totalLaps)
                val isFaster = kart.bestLapMs == 0L || event.lapTimeMs < kart.bestLapMs
                if (isFaster && event.lapTimeMs > 0L) {
                    kart.bestLapMs = event.lapTimeMs
                }
                if (event.slot == localSlotIndex) {
                    if (onNewBestLapRecorded(currentTrack.id, event.lapTimeMs)) {
                        newRecordAchievedLocal = true
                    }
                    if (event.lap < totalLaps) {
                        if (event.lap + 1 == totalLaps) {
                            bannerText = "FINAL LAP!"
                            synthAudio.playFinalLapJingle()
                        } else {
                            bannerText = "LAP ${event.lap + 1}/$totalLaps"
                        }
                        bannerTimerSec = 1.6f
                    }
                }
            }

            is ReliableRaceEvent.Finished -> {
                val kart = karts.firstOrNull { it.slotIndex == event.slot } ?: return
                kart.hasFinishedRace = true
                kart.frozenFinishPosition = event.position
                kart.racePosition = event.position
                kart.finishTimeMs = event.finishTimeMs
                if (!firstFinisherHappened) {
                    firstFinisherHappened = true
                    timeSinceFirstFinishSec = 0f
                }
                if (event.slot == localSlotIndex) {
                    phase = RacePhase.FINISHED
                    synthAudio.playFinishFanfare(event.position)
                    bannerText = "FINISHED ${ordinal(event.position)}!"
                    bannerTimerSec = 1.6f
                } else if (event.position == 1) {
                    showToast("${kart.playerName} finished 1st!", 1.6f)
                }
            }

            is ReliableRaceEvent.RaceEndCountdown -> {
                firstFinisherHappened = true
                timeSinceFirstFinishSec = (GameConstants.DNF_COUNTDOWN_AFTER_FIRST_FINISH_SEC - event.secondsLeft)
                    .coerceAtLeast(0f)
            }

            is ReliableRaceEvent.Results -> {
                applyHostFinalResults(event.standings)
            }

            is ReliableRaceEvent.Rematch -> Unit
        }
    }

    private fun updateInterpolatedRemoteKarts(nowMs: Long, raceMs: Long, effectiveDt: Float) {
        val renderTimeMs = nowMs - GameConstants.INTERP_BUFFER_MS
        for (kart in karts) {
            if (kart.slotIndex == localSlotIndex) continue
            // On host, only connected human client karts use remote buffers; CPU bots are stepped by host
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
            val drifting: Boolean

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
                drifting = latest.isDrifting
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
                drifting = b.isDrifting
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
            kart.isDrifting = drifting || sparkTier > 0
            kart.driftChargeUnits = when (sparkTier) {
                3 -> GameConstants.SPARK_PURPLE_UNITS
                2 -> GameConstants.SPARK_ORANGE_UNITS
                1 -> GameConstants.SPARK_BLUE_UNITS
                else -> 0
            }
            if (boosting && kart.boostTimerSec <= 0f) {
                kart.boostTimerSec = 0.25f
            }

            // Part B6 & B7: ONLY Host tracks unwrapped progress and lap completion for remote karts!
            // Clients NEVER count laps for remote karts.
            if (isHostAuthority) {
                // Tick roulette timer for remote human karts on host
                if (kart.rouletteTimerSec > 0f) {
                    kart.rouletteTimerSec = (kart.rouletteTimerSec - effectiveDt).coerceAtLeast(0f)
                    if (kart.rouletteTimerSec <= 0f && kart.pendingItem != ItemType.NONE) {
                        kart.heldItem = kart.pendingItem
                        kart.pendingItem = ItemType.NONE
                        if (kart.heldItem == ItemType.TRIPLE_TACKS) {
                            kart.orbitingTacksCount = 3
                        }
                    }
                }
                if (kart.spinOutTimerSec > 0f) {
                    kart.spinOutTimerSec = (kart.spinOutTimerSec - effectiveDt).coerceAtLeast(0f)
                }
                if (kart.invulnerableTimerSec > 0f) {
                    kart.invulnerableTimerSec = (kart.invulnerableTimerSec - effectiveDt).coerceAtLeast(0f)
                }

                val oldS = kart.trackS
                KartPhysics.updateKartSplineProgress(kart, currentTrack)
                val newS = kart.trackS
                val deltaS = currentTrack.wrapSignedDelta(newS - oldS)

                val gate0 = currentTrack.checkpoints[0]
                val prevSide0 = (kart.prevX - gate0.center.x) * gate0.normalX + (kart.prevZ - gate0.center.z) * gate0.normalZ
                val currSide0 = (kart.x - gate0.center.x) * gate0.normalX + (kart.z - gate0.center.z) * gate0.normalZ
                val lat0 = abs((kart.x - gate0.center.x) * gate0.rightX + (kart.z - gate0.center.z) * gate0.rightZ)
                val crossedPlane = prevSide0 < 0f && currSide0 >= 0f && lat0 <= gate0.halfWidth && abs(kart.y - gate0.center.y) < 5.0f
                val wrappedFinish = oldS > currentTrack.totalLength * 0.80f &&
                    newS < currentTrack.totalLength * 0.20f && deltaS > 0f
                val reqDist = kart.lapsCompleted * currentTrack.totalLength + 0.85f * currentTrack.totalLength
                if ((crossedPlane || wrappedFinish) && kart.raceDistance >= reqDist && !kart.hasFinishedRace) {
                    val subTickFrac = if (crossedPlane) {
                        val denom = currSide0 - prevSide0
                        if (denom > 0.0001f) (-prevSide0 / denom).coerceIn(0f, 1f) else 1f
                    } else {
                        val distToLine = (currentTrack.totalLength - oldS).coerceAtLeast(0f)
                        if (deltaS > 0.001f) (distToLine / deltaS).coerceIn(0f, 1f) else 1f
                    }
                    kart.lapsCompleted += 1
                    kart.lap = kart.lapsCompleted + 1
                    kart.raceDistance = kart.lapsCompleted * currentTrack.totalLength + newS
                    val prevRaceMs = ((raceElapsedSec - effectiveDt).coerceAtLeast(0f) * 1000f).toLong()
                    val exactCrossMs = prevRaceMs + (subTickFrac * effectiveDt * 1000f).toLong()
                    handleKartLapCompleted(kart, kart.lapsCompleted, exactCrossMs)
                }
            }
        }
    }

    @Synchronized
    fun convertSlotToCpu(slotIndex: Int, playerName: String) {
        val kart = karts.firstOrNull { it.slotIndex == slotIndex } ?: return
        kart.isHuman = false
        val baseName = playerName.removeSuffix(" (CPU)")
        kart.playerName = "$baseName (CPU)"
        showToast("$baseName disconnected — Bot driving (30s rejoin)", 1.6f)
    }

    /**
     * Part B12: Reclaim slot when a disconnected human rejoins within 30s with their stable playerId.
     */
    @Synchronized
    fun reclaimSlotForHuman(slotIndex: Int, playerName: String) {
        val kart = karts.firstOrNull { it.slotIndex == slotIndex } ?: return
        kart.isHuman = true
        kart.playerName = playerName
        remoteBuffers[slotIndex].clear()
        senderClockInit[slotIndex] = false
        showToast("$playerName rejoined!", 1.6f)
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
        synthAudio.updateRemoteKartEngines(0)
    }

    @Synchronized
    fun stepFixed(
        dt: Float,
        localInput: TouchInputSnapshot,
        autoAccelerate: Boolean,
        smartSteering: Boolean,
        showRocketStartTip: Boolean
    ) {
        // Part B10: Drain queued network commands at the start of each simulation tick
        drainCommandQueue()

        // Handle finish slow-motion beat (time scale 0.4 for 0.6s in solo)
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
                if (garageAutoRotatePauseSec > 0f) {
                    garageAutoRotatePauseSec = (garageAutoRotatePauseSec - dt).coerceAtLeast(0f)
                }
                if (abs(garageAngularVelRadPerSec) > 0.005f) {
                    showroomAngleRad = (showroomAngleRad + garageAngularVelRadPerSec * dt) % (2f * PI.toFloat())
                    garageAngularVelRadPerSec *= (1f - 4.5f * dt).coerceIn(0f, 1f)
                } else if (garageAutoRotatePauseSec <= 0f) {
                    showroomAngleRad = (showroomAngleRad + dt * 0.55f) % (2f * PI.toFloat())
                }
                for (box in itemBoxes) box.rotationRad += dt * 1.8f
                for (coin in coins) coin.rotationRad += dt * 2.5f
                synthAudio.isEngineActive = false
                synthAudio.isBoostingActive = false
            }

            RacePhase.FLYBY_INTRO -> {
                flybyRemainingSec -= dt
                for (box in itemBoxes) box.rotationRad += dt * 1.8f
                for (coin in coins) coin.rotationRad += dt * 2.5f
                if (flybyRemainingSec <= 0f || localInput.gasPressed || localInput.driftJustPressed || localInput.itemJustPressed) {
                    enterCountdownPhase(3.2f)
                }
            }

            RacePhase.COUNTDOWN -> {
                countdownRemainingSec -= dt
                val ceilSec = kotlin.math.ceil(countdownRemainingSec.toDouble()).toInt().coerceIn(0, 3)
                if (ceilSec in 1..3 && ceilSec < lastCountdownBeepInt) {
                    lastCountdownBeepInt = ceilSec
                    synthAudio.playCountdownBeep(isGo = false)
                }

                // Rocket Start window is the 0.35s right after "2" goes off
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
                            showToast("ROCKET START!", 1.6f)
                        }
                    }
                }
            }

            RacePhase.RACING, RacePhase.FINISHED -> {
                val prevRaceSec = raceElapsedSec
                raceElapsedSec += effectiveDt
                val prevRaceMs = (prevRaceSec * 1000f).toLong()
                val raceMs = getEffectiveRaceTimeMs()

                if (isMultiplayer) {
                    updateInterpolatedRemoteKarts((raceElapsedSec * 1000f).toLong(), raceMs, effectiveDt)
                }

                val leadingHumanProgress = karts.filter { it.isHuman && !it.hasFinishedRace }
                    .maxOfOrNull { it.totalRaceProgress } ?: 0f

                for (kart in karts) {
                    // Client prediction timeout management for local kart (Part B5)
                    if (kart.slotIndex == localSlotIndex && isMultiplayer && !isHostAuthority) {
                        if (kart.clientBoxPickupPendingSec > 0f) {
                            kart.clientBoxPickupPendingSec = (kart.clientBoxPickupPendingSec - effectiveDt).coerceAtLeast(0f)
                            if (kart.clientBoxPickupPendingSec <= 0f && kart.pendingItem == ItemType.NONE && kart.heldItem == ItemType.NONE) {
                                // Host did not confirm box pickup within 250ms; roll back roulette
                                kart.rouletteTimerSec = 0f
                            }
                        }
                        if (kart.clientUsedItemHoldbackSec > 0f) {
                            kart.clientUsedItemHoldbackSec = (kart.clientUsedItemHoldbackSec - effectiveDt).coerceAtLeast(0f)
                            if (kart.clientUsedItemHoldbackSec <= 0f && kart.clientPreUseItem != ItemType.NONE) {
                                // Host rejected item use or timed out -> restore HUD item
                                if (kart.heldItem == ItemType.NONE) {
                                    kart.heldItem = kart.clientPreUseItem
                                }
                                kart.clientPreUseItem = ItemType.NONE
                            }
                        }
                    }

                    if (kart.slotIndex == localSlotIndex && !kart.hasFinishedRace) {
                        // Local human player active driving (or the QA autopilot for playtest capture)
                        val driveInput = if (autopilotLocal) {
                            CpuDriver.computeCpuInput(
                                kart = kart, track = currentTrack, allKarts = karts, projectiles = projectiles,
                                itemBoxes = itemBoxes, coins = coins, difficulty = CpuDifficulty.CHAMPION,
                                ccClass = ccClass, leadingHumanProgress = 0f, raceElapsedSec = raceElapsedSec
                            ).first
                        } else localInput
                        handleItemActivation(kart, driveInput)
                        KartPhysics.stepKart(
                            dt = effectiveDt,
                            kart = kart,
                            input = driveInput,
                            track = currentTrack,
                            allKarts = karts,
                            autoAccelerate = autoAccelerate,
                            smartSteering = smartSteering,
                            speedScale = ccClass.speedScale,
                            raceElapsedSec = raceElapsedSec,
                            activePylons = pylons,
                            onWallBump = { synthAudio.playWallBumpTick() },
                            onPylonKnocked = { synthAudio.playWallBumpTick() },
                            onBoostTriggered = { synthAudio.playBoostWhoosh() },
                            onLanded = { synthAudio.playLandSound() },
                            onRespawnToast = { showToast("RESPAWN", 1.5f) },
                            onLapCompleted = { completedLap, subTickFrac ->
                                if (isHostAuthority || !isMultiplayer) {
                                    val exactMs = prevRaceMs + (subTickFrac * effectiveDt * 1000f).toLong()
                                    handleKartLapCompleted(kart, completedLap, exactMs)
                                }
                            }
                        )
                        if (kart.hopJustStarted) {
                            kart.hopJustStarted = false
                            synthAudio.playHopSound()
                        }
                        if (kart.landingShakeTrigger) {
                            kart.landingShakeTrigger = false
                            landingShakeCounter++
                        }
                        synthAudio.engineSpeedRatio = abs(kart.forwardSpeed) / GameConstants.TOP_SPEED
                        synthAudio.isDriftingActive = kart.isDrifting
                        synthAudio.isBoostingActive = kart.boostTimerSec > 0f

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
                    } else if (kart.slotIndex == localSlotIndex && kart.hasFinishedRace) {
                        // Part B12: Local human who finished is driven by their OWN phone's autopilot!
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
                            activePylons = pylons
                        )
                        synthAudio.engineSpeedRatio = abs(kart.forwardSpeed) / GameConstants.TOP_SPEED
                        synthAudio.isDriftingActive = false
                        synthAudio.isBoostingActive = false
                    } else if (!kart.isHuman) {
                        // Part B12 & B13: Host (or solo) runs CPU bots; Host NEVER autopilots a connected remote human!
                        if (isHostAuthority || !isMultiplayer) {
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
                                activePylons = pylons,
                                onLapCompleted = { completedLap, subTickFrac ->
                                    val exactMs = prevRaceMs + (subTickFrac * effectiveDt * 1000f).toLong()
                                    handleKartLapCompleted(kart, completedLap, exactMs)
                                }
                            )
                        }
                    }
                }
                KartPhysics.stepPylons(effectiveDt, pylons)

                // Step Items, Coins & Projectiles (Host authority or Client visual prediction per Part B2 & B5)
                val authoritativeItems = isHostAuthority || !isMultiplayer
                ItemsEngine.stepProjectilesAndPickups(
                    dt = effectiveDt,
                    track = currentTrack,
                    karts = karts,
                    itemBoxes = itemBoxes,
                    coins = coins,
                    projectiles = projectiles,
                    itemMode = itemMode,
                    isTimeTrial = (gameMode == GameMode.TIME_TRIAL),
                    hostAuthority = authoritativeItems,
                    localSlotIndex = localSlotIndex,
                    onHitEvent = { victimSlot, attackerSlot, kind ->
                        synthAudio.playItemHitSound()
                        if (victimSlot == localSlotIndex) {
                            onLocalPlayerHitHaptic?.invoke()
                        }
                        val attackerName = karts.firstOrNull { it.slotIndex == attackerSlot }?.playerName ?: "Racer"
                        val victimName = karts.firstOrNull { it.slotIndex == victimSlot }?.playerName ?: "Racer"
                        if (victimSlot == localSlotIndex) {
                            showToast("Hit by $attackerName!", 1.6f)
                        } else if (attackerSlot == localSlotIndex) {
                            showToast("Hit $victimName!", 1.6f)
                        }
                        if (isMultiplayer && isHostAuthority) {
                            onHostEmitReliableEvent?.invoke(
                                ReliableRaceEvent.Hit(activeRaceId, victimSlot, attackerSlot, kind)
                            )
                        }
                    },
                    onBoxPickedUp = { slot, _, grantedItem ->
                        if (slot == localSlotIndex) {
                            synthAudio.playItemPickupBlip()
                        }
                        if (isMultiplayer && isHostAuthority && grantedItem != ItemType.NONE) {
                            onHostEmitReliableEvent?.invoke(
                                ReliableRaceEvent.ItemGranted(
                                    raceId = activeRaceId,
                                    slot = slot,
                                    item = grantedItem,
                                    rouletteMs = (GameConstants.ITEM_ROULETTE_SEC * 1000f).toInt()
                                )
                            )
                        }
                    },
                    onCoinPickedUp = { slot, _ ->
                        if (slot == localSlotIndex) {
                            synthAudio.playCoinPickup()
                        }
                        if (isMultiplayer && isHostAuthority) {
                            val totalCoins = karts.firstOrNull { it.slotIndex == slot }?.coinsHeld ?: 0
                            onHostEmitReliableEvent?.invoke(
                                ReliableRaceEvent.CoinPicked(activeRaceId, slot, totalCoins)
                            )
                        }
                    }
                )

                // Part B6: Host ranks karts by (finished first by finishOrder) then raceDistance with 0.75 u / 120 ms hysteresis
                if (isHostAuthority || !isMultiplayer) {
                    updateHostLivePositionsWithHysteresis(effectiveDt)
                }

                // Part C1: Update local displayed position (max 1 change per 120 ms, ▲/▼ chip only after >= 400 ms)
                updateLocalDisplayedPositionAndChip(effectiveDt)

                // Part I2: Update 3 nearest remote kart engines (pitched by speed, panned by angle, attenuated by distance)
                updateSpatialRemoteKartAudio()

                // Part B4: Explicit Race-End Rules
                evaluateRaceEndConditions(effectiveDt, raceMs)
            }

            RacePhase.RESULTS -> {
                showroomAngleRad = (showroomAngleRad + dt * 0.5f) % (2f * PI.toFloat())
                synthAudio.isEngineActive = false
                synthAudio.isBoostingActive = false
                synthAudio.updateRemoteKartEngines(0)
            }
        }
    }

    /**
     * Part B6: Host ranks karts by (finished first by finishOrder) then raceDistance,
     * with hysteresis of 0.75 u for 120 ms so two karts side-by-side don't flicker,
     * and breaks ties at the start line by lower slot.
     */
    private fun updateHostLivePositionsWithHysteresis(dt: Float) {
        if (karts.isEmpty()) return

        // C3: Sort finished karts by exact sub-tick finishTimeMs (then slotIndex) and assign frozenFinishPosition
        val finishedKarts = karts.filter { it.hasFinishedRace }
            .sortedWith(compareBy<KartRuntime> { it.finishTimeMs }.thenBy { it.slotIndex })
        for (i in finishedKarts.indices) {
            finishedKarts[i].frozenFinishPosition = i + 1
            finishedKarts[i].racePosition = i + 1
        }

        val racingKarts = karts.filter { !it.hasFinishedRace }
            .sortedWith(compareBy<KartRuntime> { it.racePosition }.thenBy { it.slotIndex })
            .toMutableList()

        fun metric(k: KartRuntime): Float {
            return if (gameMode == GameMode.BATTLE_BALLOONS) {
                k.balloonsRemaining * 10000f + k.totalRaceProgress
            } else {
                k.totalRaceProgress
            }
        }

        // At the very start of the race (< 0.15s), order by lower slot index on start line ties
        if (raceElapsedSec < 0.15f) {
            racingKarts.sortWith(
                Comparator { a, b ->
                    val diff = metric(b) - metric(a)
                    if (abs(diff) < 0.05f) a.slotIndex.compareTo(b.slotIndex)
                    else diff.compareTo(0f)
                }
            )
        } else {
            // Apply 0.75 u for 120 ms hysteresis before allowing a trailing kart to overtake a leading kart
            var i = 0
            while (i < racingKarts.size - 1) {
                val ahead = racingKarts[i]
                val behind = racingKarts[i + 1]
                val leadMargin = metric(behind) - metric(ahead)
                val pairIdx = behind.slotIndex * GameConstants.MAX_RACERS + ahead.slotIndex
                if (leadMargin > 0.75f) {
                    overtakeHoldTimersSec[pairIdx] += dt
                    if (overtakeHoldTimersSec[pairIdx] >= 0.12f) {
                        overtakeHoldTimersSec[pairIdx] = 0f
                        racingKarts[i] = behind
                        racingKarts[i + 1] = ahead
                    }
                } else {
                    overtakeHoldTimersSec[pairIdx] = 0f
                }
                i++
            }
        }

        var nextRank = finishedKarts.size + 1
        for (rk in racingKarts) {
            rk.racePosition = nextRank++
        }
    }

    /**
     * Part C1: Position display changes at most once per 120 ms, and shows "▲3 / ▼4" chip
     * ONLY for changes lasting >= 400 ms.
     */
    private fun updateLocalDisplayedPositionAndChip(dt: Float) {
        val localNowPos = karts.firstOrNull { it.slotIndex == localSlotIndex }?.racePosition ?: 1
        if (raceElapsedSec < 1.2f) {
            displayedLocalPosition = localNowPos
            candidateStablePos = localNowPos
            chipAnnouncedPos = localNowPos
            return
        }

        if (displayPosCooldownSec > 0f) {
            displayPosCooldownSec = (displayPosCooldownSec - dt).coerceAtLeast(0f)
        }
        if (localNowPos != displayedLocalPosition && displayPosCooldownSec <= 0f) {
            displayedLocalPosition = localNowPos
            displayPosCooldownSec = 0.12f // Max 1 display change per 120 ms
        }

        if (localNowPos == candidateStablePos) {
            candidateStableHoldSec += dt
            if (candidateStableHoldSec >= 0.40f && candidateStablePos != chipAnnouncedPos) {
                val improved = candidateStablePos < chipAnnouncedPos
                positionDeltaChip = if (improved) "▲$candidateStablePos" else "▼$candidateStablePos"
                positionDeltaTimerSec = 1.6f
                chipAnnouncedPos = candidateStablePos
            }
        } else {
            candidateStablePos = localNowPos
            candidateStableHoldSec = 0f
        }
    }

    /**
     * Part I2: Mix in 3 nearest remote kart engines, pitched by speed, panned by angle, attenuated by distance.
     */
    private fun updateSpatialRemoteKartAudio() {
        val localKart = karts.firstOrNull { it.slotIndex == localSlotIndex }
        if (localKart == null || (phase != RacePhase.RACING && phase != RacePhase.FINISHED)) {
            synthAudio.updateRemoteKartEngines(0)
            return
        }
        val rightX = -cos(localKart.headingRad)
        val rightZ = sin(localKart.headingRad)
        val maxAudioDist = 42f

        var best0Dist = Float.MAX_VALUE
        var best1Dist = Float.MAX_VALUE
        var best2Dist = Float.MAX_VALUE
        var best0Kart: KartRuntime? = null
        var best1Kart: KartRuntime? = null
        var best2Kart: KartRuntime? = null

        for (i in 0 until karts.size) {
            val k = karts[i]
            if (k.slotIndex == localSlotIndex || k.isRespawning) continue
            val d = hypot(k.x - localKart.x, k.z - localKart.z)
            if (d > maxAudioDist) continue
            if (d < best0Dist) {
                best2Dist = best1Dist; best2Kart = best1Kart
                best1Dist = best0Dist; best1Kart = best0Kart
                best0Dist = d; best0Kart = k
            } else if (d < best1Dist) {
                best2Dist = best1Dist; best2Kart = best1Kart
                best1Dist = d; best1Kart = k
            } else if (d < best2Dist) {
                best2Dist = d; best2Kart = k
            }
        }

        fun panFor(k: KartRuntime?, dist: Float): Float {
            if (k == null || dist < 0.01f) return 0f
            val dx = (k.x - localKart.x) / dist
            val dz = (k.z - localKart.z) / dist
            return (dx * rightX + dz * rightZ).coerceIn(-1f, 1f)
        }

        fun gainFor(k: KartRuntime?, dist: Float): Float {
            if (k == null || dist >= maxAudioDist) return 0f
            val norm = (1f - dist / maxAudioDist).coerceIn(0f, 1f)
            return norm * norm
        }

        fun speedFor(k: KartRuntime?): Float {
            if (k == null) return 0f
            return (abs(k.forwardSpeed) / GameConstants.TOP_SPEED).coerceIn(0f, 1.4f)
        }

        val count = when {
            best2Kart != null -> 3
            best1Kart != null -> 2
            best0Kart != null -> 1
            else -> 0
        }
        synthAudio.updateRemoteKartEngines(
            count = count,
            s0 = speedFor(best0Kart), pan0 = panFor(best0Kart, best0Dist), gain0 = gainFor(best0Kart, best0Dist),
            s1 = speedFor(best1Kart), pan1 = panFor(best1Kart, best1Dist), gain1 = gainFor(best1Kart, best1Dist),
            s2 = speedFor(best2Kart), pan2 = panFor(best2Kart, best2Dist), gain2 = gainFor(best2Kart, best2Dist)
        )
    }

    private fun handleKartLapCompleted(kart: KartRuntime, completedLap: Int, exactCrossRaceMs: Long) {
        if (kart.hasFinishedRace) return
        val lapDuration = (exactCrossRaceMs - kart.currentLapStartMs).coerceAtLeast(1000L)
        kart.currentLapStartMs = exactCrossRaceMs

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

        if (isMultiplayer && isHostAuthority) {
            onHostEmitReliableEvent?.invoke(
                ReliableRaceEvent.LapCompleted(activeRaceId, kart.slotIndex, completedLap, lapDuration)
            )
        }

        if (completedLap >= totalLaps) {
            kart.hasFinishedRace = true
            kart.finishTimeMs = exactCrossRaceMs
            kart.lap = totalLaps // Part C2: Cap lap display at totalLaps

            // Part C3: Re-rank all finishers by exact sub-tick finishTimeMs
            val finishedSorted = karts.filter { it.hasFinishedRace }
                .sortedWith(compareBy<KartRuntime> { it.finishTimeMs }.thenBy { it.slotIndex })
            for (i in finishedSorted.indices) {
                finishedSorted[i].frozenFinishPosition = i + 1
                finishedSorted[i].racePosition = i + 1
            }
            nextFinishPositionToAssign = finishedSorted.size + 1

            if (!firstFinisherHappened) {
                firstFinisherHappened = true
                timeSinceFirstFinishSec = 0f
            }

            if (isMultiplayer && isHostAuthority) {
                onHostEmitReliableEvent?.invoke(
                    ReliableRaceEvent.Finished(
                        raceId = activeRaceId,
                        slot = kart.slotIndex,
                        position = kart.frozenFinishPosition,
                        finishTimeMs = kart.finishTimeMs
                    )
                )
            }

            if (kart.slotIndex == localSlotIndex) {
                phase = RacePhase.FINISHED
                slowMoFinishTimerSec = 0.6f
                synthAudio.playFinishFanfare(kart.frozenFinishPosition)
                bannerText = "FINISHED ${ordinal(kart.frozenFinishPosition)}!"
                bannerTimerSec = 1.6f // Part F5: 1.6s banner
            } else if (kart.frozenFinishPosition == 1) {
                showToast("${kart.playerName} finished 1st!", 1.6f)
            }
        } else if (kart.slotIndex == localSlotIndex) {
            if (completedLap + 1 == totalLaps) {
                bannerText = "FINAL LAP!"
                synthAudio.playFinalLapJingle()
            } else {
                bannerText = "LAP ${completedLap + 1}/$totalLaps"
            }
            bannerTimerSec = 1.6f // Part F5: 1.6s banner
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
            if (isMultiplayer && isHostAuthority) {
                val remSec = (GameConstants.DNF_COUNTDOWN_AFTER_FIRST_FINISH_SEC - timeSinceFirstFinishSec)
                    .roundToInt().coerceAtLeast(0)
                if (remSec != lastBroadcastCountdownSec && remSec in 0..30 && remSec % 5 == 0) {
                    lastBroadcastCountdownSec = remSec
                    onHostEmitReliableEvent?.invoke(
                        ReliableRaceEvent.RaceEndCountdown(activeRaceId, remSec)
                    )
                }
            }
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

        if ((allHumansFinished && allHumansFinishedDelaySec >= 2.5f) ||
            (humanKarts.isEmpty() && allBotsFinishedToo) ||
            dnfTimeoutReached ||
            hardCapReached
        ) {
            finalizeRaceAndEnterResults(raceMs)
        }
    }

    private fun finalizeRaceAndEnterResults(raceMs: Long) {
        // Part C3 & C4: Order finishers by sub-tick finishTimeMs, and place unfinished (DNF) after finishers ordered by raceDistance
        val finishers = karts.filter { it.hasFinishedRace }
            .sortedWith(compareBy<KartRuntime> { it.finishTimeMs }.thenBy { it.slotIndex })
        for (i in finishers.indices) {
            finishers[i].frozenFinishPosition = i + 1
            finishers[i].racePosition = i + 1
        }
        nextFinishPositionToAssign = finishers.size + 1

        val unfinished = karts.filter { !it.hasFinishedRace }
            .sortedWith(compareByDescending<KartRuntime> { it.raceDistance }.thenBy { it.slotIndex })

        val allHumansDone = karts.filter { it.isHuman }.all { it.hasFinishedRace }
        val dnfByTimeoutOrCap = (firstFinisherHappened && timeSinceFirstFinishSec >= GameConstants.DNF_COUNTDOWN_AFTER_FIRST_FINISH_SEC) ||
            (raceElapsedSec >= totalLaps * GameConstants.HARD_TIME_CAP_PER_LAP_SEC)

        for (k in unfinished) {
            val pos = nextFinishPositionToAssign++
            k.frozenFinishPosition = pos
            k.racePosition = pos
            if (allHumansDone && !k.isHuman && !dnfByTimeoutOrCap) {
                k.hasFinishedRace = true
                k.isDnf = false
                k.finishTimeMs = raceMs + (pos * 1150L)
            } else {
                // Part C4: Unfinished karts after the 30s countdown are marked DNF and placed after finishers ordered by raceDistance
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
        synthAudio.isBoostingActive = false
        synthAudio.updateRemoteKartEngines(0)
        val finalStandings = buildStandingsList(raceMs)
        onHostRaceResultsReady?.invoke(finalStandings)
        if (isMultiplayer && isHostAuthority) {
            onHostEmitReliableEvent?.invoke(
                ReliableRaceEvent.Results(activeRaceId, finalStandings)
            )
        }
    }

    private fun handleItemActivation(kart: KartRuntime, input: TouchInputSnapshot) {
        if (kart.heldItem == ItemType.NONE || kart.rouletteTimerSec > 0f) return

        if (kart.heldItem == ItemType.ERASER && input.itemHeldDown) {
            kart.shieldEraserHeld = true
            return
        }

        val shouldFire = input.itemJustPressed || (kart.shieldEraserHeld && !input.itemHeldDown)
        if (!shouldFire) return

        val usedItem = kart.heldItem
        kart.shieldEraserHeld = false
        val fwdX = sin(kart.headingRad)
        val fwdZ = cos(kart.headingRad)

        if (kart.slotIndex == localSlotIndex) {
            synthAudio.playItemFireSound()
        }

        if (isMultiplayer && !isHostAuthority && kart.slotIndex == localSlotIndex) {
            // Part B5: Client Item Flow — play throw animation/sound immediately (client prediction)
            // and send edge to Host. If Host rejects/times out, restore HUD item.
            kart.clientPreUseItem = usedItem
            kart.clientUsedItemHoldbackSec = 0.40f
            if (usedItem == ItemType.BATTERY) {
                KartPhysics.applyBoost(kart, GameConstants.BATTERY_BOOST_SEC)
                synthAudio.playBoostWhoosh()
            } else if (usedItem == ItemType.MAGNET) {
                kart.magnetTimerSec = GameConstants.MAGNET_DURATION_SEC
                KartPhysics.applyBoost(kart, 1.2f, 1.22f)
                synthAudio.playBoostWhoosh()
            }
            if (usedItem == ItemType.TRIPLE_TACKS && kart.orbitingTacksCount > 1) {
                kart.orbitingTacksCount--
            } else {
                kart.orbitingTacksCount = 0
                kart.heldItem = ItemType.NONE
            }
            onLocalClientItemAction?.invoke(usedItem, input.itemFireBackward)
            return
        }

        val spawned = executeKartItemAction(kart, input.itemFireBackward, fwdX, fwdZ)
        if (isMultiplayer && isHostAuthority) {
            onHostEmitReliableEvent?.invoke(
                ReliableRaceEvent.ItemUsed(activeRaceId, kart.slotIndex, usedItem, input.itemFireBackward)
            )
            if (spawned != null) {
                onHostEmitReliableEvent?.invoke(
                    ReliableRaceEvent.ProjectileSpawned(
                        raceId = activeRaceId,
                        id = spawned.id,
                        kind = spawned.kind,
                        ownerSlot = spawned.ownerSlot,
                        x = spawned.x,
                        y = spawned.y,
                        z = spawned.z,
                        vx = spawned.vx,
                        vz = spawned.vz,
                        headingRad = spawned.headingRad
                    )
                )
            }
        }
    }

    @Synchronized
    fun executeKartItemAction(
        kart: KartRuntime,
        fireBackward: Boolean,
        fwdX: Float,
        fwdZ: Float,
        advanceSec: Float = 0f
    ): WorldProjectile? {
        var spawned: WorldProjectile? = null
        when (kart.heldItem) {
            ItemType.MARBLE -> {
                val dirSign = if (fireBackward) -1f else 1f
                val vx = fwdX * GameConstants.MARBLE_SPEED * dirSign
                val vz = fwdZ * GameConstants.MARBLE_SPEED * dirSign
                val p = WorldProjectile(
                    id = nextProjectileId++,
                    kind = ProjectileKind.MARBLE,
                    ownerSlot = kart.slotIndex,
                    x = kart.x + fwdX * 2.2f * dirSign + vx * advanceSec,
                    y = kart.y + 0.5f,
                    z = kart.z + fwdZ * 2.2f * dirSign + vz * advanceSec,
                    vx = vx,
                    vz = vz,
                    headingRad = kart.headingRad
                )
                projectiles.add(p)
                spawned = p
                kart.heldItem = ItemType.NONE
            }

            ItemType.ERASER -> {
                val p = WorldProjectile(
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
                projectiles.add(p)
                spawned = p
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
                val vx = fwdX * 38f
                val vz = fwdZ * 38f
                val p = WorldProjectile(
                    id = nextProjectileId++,
                    kind = ProjectileKind.TACK,
                    ownerSlot = kart.slotIndex,
                    x = kart.x + fwdX * 2.1f + vx * advanceSec,
                    y = kart.y + 0.4f,
                    z = kart.z + fwdZ * 2.1f + vz * advanceSec,
                    vx = vx,
                    vz = vz,
                    bouncesRemaining = 2,
                    lifeRemainingSec = 3.5f
                )
                projectiles.add(p)
                spawned = p
                if (kart.orbitingTacksCount <= 0) {
                    kart.heldItem = ItemType.NONE
                }
            }

            ItemType.PAPER_PLANE -> {
                // Part B6: Paper-plane targets use Host's racePosition
                // leader's plane goes after 2nd place instead of a random kart
                val targetPos = if (kart.racePosition <= 1) 2 else kart.racePosition - 1
                val targetKart = karts.firstOrNull { it.racePosition == targetPos && it.slotIndex != kart.slotIndex }
                    ?: karts.firstOrNull { it.slotIndex != kart.slotIndex }
                val vx = fwdX * 38f
                val vz = fwdZ * 38f
                val p = WorldProjectile(
                    id = nextProjectileId++,
                    kind = ProjectileKind.PAPER_PLANE,
                    ownerSlot = kart.slotIndex,
                    x = kart.x + fwdX * 2.0f + vx * advanceSec,
                    y = kart.y + 1.4f,
                    z = kart.z + fwdZ * 2.0f + vz * advanceSec,
                    vx = vx,
                    vz = vz,
                    headingRad = kart.headingRad,
                    lifeRemainingSec = GameConstants.PAPER_PLANE_FLIGHT_SEC,
                    targetSlot = targetKart?.slotIndex ?: -1
                )
                projectiles.add(p)
                spawned = p
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
        return spawned
    }

    /**
     * Part B8: Client displays race time from hostRaceTimeMs + (now - snapshotReceiptTime).
     */
    private fun getEffectiveRaceTimeMs(): Long {
        if (isMultiplayer && !isHostAuthority && lastSnapshotReceiptTimeMs > 0L) {
            val delta = (System.currentTimeMillis() - lastSnapshotReceiptTimeMs).coerceIn(0L, 500L)
            return (lastHostRaceTimeMs + delta).coerceAtLeast(0L)
        }
        return (raceElapsedSec * 1000f).toLong()
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
                isDrifting = k.isDrifting,
                isOffTrack = k.isOffTrack,
                isInvulnerableFlashing = flashHide,
                isGhostMode = k.ghostTimerSec > 0f,
                rocketStallSmoke = k.rocketStartStallTimerSec > 0f,
                orbitingTacksCount = k.orbitingTacksCount,
                shieldEraserHeld = k.shieldEraserHeld,
                heldItem = k.heldItem,
                coinsHeld = k.coinsHeld,
                balloonsRemaining = if (gameMode == GameMode.BATTLE_BALLOONS) k.balloonsRemaining else 0,
                liveryIndex = k.liveryIndex,
                draftLevel = (k.draftSec / DRAFT_CHARGE_SEC).coerceIn(0f, 1f),
                paintFinishIndex = when {
                    k.slotIndex == localSlotIndex || (phase == RacePhase.SHOWROOM_MENU && k.isLocalPlayer) -> localPaintFinishIndex
                    !k.isHuman -> (k.slotIndex * 3) % 4 // CPU field gets a mix of gloss / matte / metal / pearl
                    else -> 0
                }
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
                headingRad = p.headingRad,
                distanceTravelled = p.distanceTravelled
            )
        }

        val pylonSnapshots = pylons.map { py ->
            RenderPylonSnapshot(
                propId = py.propId,
                modelPath = py.modelPath,
                x = py.x,
                y = py.y,
                z = py.z,
                yawRad = py.yawRad,
                pitchRad = py.pitchRad,
                rollRad = py.rollRad,
                scale = py.scale,
                radius = py.radius
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
            pylons = pylonSnapshots,
            itemBoxes = boxSnapshots,
            coins = coinSnapshots,
            dynamicHazards = hazardSnapshots,
            landingShakeSignal = landingShakeCounter,
            showroomCameraAngleRad = showroomAngleRad,
            isGarageStageActive = isGarageStageActive,
            garageZoomFactor = garageZoomFactor,
            flybyProgress = flybyProg,
            celebrationConfettiActive = (phase == RacePhase.FINISHED),
            elapsedTimeSec = raceElapsedSec
        )
    }

    private fun buildStandingsList(raceMs: Long): List<RacerStanding> {
        val fastestLapOverall = karts.filter { it.bestLapMs > 0L }.minOfOrNull { it.bestLapMs } ?: 0L
        // Part C4: Finishers ordered by position (sub-tick finishTimeMs), DNF placed after finishers ordered by raceDistance
        val sortedKarts = karts.sortedWith(
            Comparator { a, b ->
                if (a.isDnf != b.isDnf) {
                    if (a.isDnf) 1 else -1
                } else if (!a.isDnf) {
                    a.racePosition.compareTo(b.racePosition)
                } else {
                    b.raceDistance.compareTo(a.raceDistance)
                }
            }
        )
        return sortedKarts.mapIndexed { idx, k ->
            val finalRank = if (phase == RacePhase.RESULTS) idx + 1 else k.racePosition
            val ptsEarned = if (gameMode == GameMode.GRAND_PRIX) {
                GameConstants.GRAND_PRIX_POINTS.getOrElse(finalRank - 1) { 0 }
            } else 0
            RacerStanding(
                slotIndex = k.slotIndex,
                position = finalRank,
                name = k.playerName,
                colorHex = k.colorHex,
                bodyType = k.bodyType,
                isHuman = k.isHuman,
                isLocal = (k.slotIndex == localSlotIndex),
                totalTimeMs = if (k.finishTimeMs > 0L) k.finishTimeMs else raceMs,
                bestLapMs = k.bestLapMs,
                isDnf = k.isDnf,
                isNewRecord = (k.slotIndex == localSlotIndex && newRecordAchievedLocal),
                hasOverallBestLap = (k.bestLapMs > 0L && k.bestLapMs == fastestLapOverall),
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

        // Part B6: Minimap dots ordered by racePosition (leader drawn clearly, local player on top)
        val dots = karts.sortedByDescending { it.racePosition }.map { k ->
            MinimapDot(
                slotIndex = k.slotIndex,
                x = k.x,
                z = k.z,
                colorHex = k.colorHex,
                isLocal = (k.slotIndex == localSlotIndex)
            )
        }

        val raceMs = getEffectiveRaceTimeMs()
        val standings = buildStandingsList(raceMs)

        val cdNum = when {
            phase != RacePhase.COUNTDOWN -> -1
            countdownRemainingSec > 2.0f -> 3
            countdownRemainingSec > 1.0f -> 2
            countdownRemainingSec > 0.0f -> 1
            else -> 0
        }

        val dnfRemaining = if (firstFinisherHappened && (phase == RacePhase.RACING || phase == RacePhase.FINISHED)) {
            (GameConstants.DNF_COUNTDOWN_AFTER_FIRST_FINISH_SEC - timeSinceFirstFinishSec)
                .roundToInt().coerceAtLeast(0)
        } else {
            -1
        }

        // Part C1 & B6: Use displayedLocalPosition (which tracks Host's racePosition with 120ms max rate)
        val effectivePos = if (phase == RacePhase.RACING) {
            displayedLocalPosition.coerceIn(1, karts.size.coerceAtLeast(1))
        } else {
            (localKart?.racePosition ?: 1).coerceIn(1, karts.size.coerceAtLeast(1))
        }

        return HudState(
            phase = phase,
            gameMode = gameMode,
            trackId = currentTrack.id,
            trackName = currentTrack.name,
            trackSubtitle = currentTrack.subtitle,
            position = effectivePos,
            totalRacers = karts.size.coerceAtLeast(1),
            positionDeltaChip = positionDeltaChip,
            lap = (localKart?.lap ?: 1).coerceIn(1, totalLaps),
            totalLaps = totalLaps,
            isFinished = localKart?.hasFinishedRace == true,
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

    @Synchronized
    fun buildHudSnapshot(showRocketTipPref: Boolean = false): HudState = buildHudState(showRocketTipPref)

    private fun ordinal(n: Int): String = when (n) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }
}
