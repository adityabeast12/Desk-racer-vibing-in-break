package com.example.game

import com.example.audio.SynthAudio

data class TrackQaResult(
    val trackId: Int,
    val trackName: String,
    val finishedCount: Int,
    val totalBots: Int,
    val averageLapMs: Long,
    val totalWatchdogCount: Int,
    val maxWatchdogPerBot: Int,
    val slowestBotTimeMs: Long,
    val raceEndFired: Boolean,
    val validationIssues: List<String>
) {
    val passed: Boolean
        get() = finishedCount == totalBots &&
            raceEndFired &&
            maxWatchdogPerBot <= 6 && // <= 2 per lap across 3 laps
            validationIssues.isEmpty()
}

object HeadlessRaceTest {

    private val BOT_NAMES = arrayOf(
        "APEX", "NIBBLES", "GRAPHITE", "VOLT", "SKETCH", "TURBO", "BLAZE", "PIXEL"
    )

    fun runAllTracksQa(laps: Int = 3): List<TrackQaResult> {
        return Tracks.all.map { runTrackQa(it.id, laps) }
    }

    fun runTrackQa(trackId: Int, laps: Int = 3): TrackQaResult {
        val track = Tracks.byId(trackId)
        val validation = TrackValidator.validate(track)
        val silentAudio = SynthAudio(context = null).apply {
            soundEnabled = false
            hapticsEnabled = false
        }
        val session = RaceSession(
            synthAudio = silentAudio,
            onNewBestLapRecorded = { _, _ -> false }
        )

        val bots = ArrayList<KartRuntime>(GameConstants.MAX_RACERS)
        for (slot in 0 until GameConstants.MAX_RACERS) {
            bots.add(
                KartRuntime(
                    slotIndex = slot,
                    playerName = BOT_NAMES[slot % BOT_NAMES.size],
                    isHuman = false,
                    isLocalPlayer = false,
                    colorHex = GameConstants.KART_COLORS[slot % GameConstants.KART_COLORS.size],
                    bodyType = KartBodyType.fromId(slot % 5)
                )
            )
        }

        session.startNewRace(
            trackId = trackId,
            laps = laps,
            mode = ItemMode.SKILL_ONLY, // Deterministic skill items so all 8 bots race cleanly
            difficulty = CpuDifficulty.PRO,
            cc = CcClass.CC_150,
            gameMode = GameMode.QUICK_RACE,
            multiplayer = false,
            hostAuthority = true,
            localSlot = -1, // All 8 karts driven by CpuDriver
            racerConfigs = bots,
            skipFlyby = true
        )

        val dt = GameConstants.SIM_DT
        val maxSteps = (600f / dt).toInt() // Up to 600s simulated
        val emptyInput = TouchInputSnapshot()
        var step = 0
        while (step < maxSteps && session.phase != RacePhase.RESULTS) {
            session.stepFixed(
                dt = dt,
                localInput = emptyInput,
                autoAccelerate = true,
                smartSteering = true,
                showRocketStartTip = false
            )
            step++
        }

        val finishedBots = session.karts.count { it.hasFinishedRace && !it.isDnf }
        val totalWatchdogs = session.karts.sumOf { it.watchdogRespawnCount }
        val maxWatchdog = session.karts.maxOfOrNull { it.watchdogRespawnCount } ?: 0
        val avgLap = session.karts.mapNotNull { if (it.bestLapMs > 0L) it.bestLapMs else null }
            .average()
            .let { if (it.isNaN()) 0L else it.toLong() }
        val slowestBotMs = session.karts.maxOfOrNull { it.finishTimeMs } ?: 0L
        val raceEnded = (session.phase == RacePhase.RESULTS)

        silentAudio.shutdown()

        return TrackQaResult(
            trackId = track.id,
            trackName = track.name,
            finishedCount = finishedBots,
            totalBots = GameConstants.MAX_RACERS,
            averageLapMs = avgLap,
            totalWatchdogCount = totalWatchdogs,
            maxWatchdogPerBot = maxWatchdog,
            slowestBotTimeMs = slowestBotMs,
            raceEndFired = raceEnded,
            validationIssues = validation.issues
        )
    }
}
