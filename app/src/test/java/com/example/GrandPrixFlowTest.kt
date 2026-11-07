package com.example

import com.example.audio.SynthAudio
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.CupRanking
import com.example.game.CupType
import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.KartBodyType
import com.example.game.KartRuntime
import com.example.game.RacePhase
import com.example.game.RaceSession
import com.example.game.RacerStanding
import com.example.game.TouchInputSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A whole 4-race cup ends (no endless loop), points add up, and the podium ranks by points. */
class GrandPrixFlowTest {

    private fun racers(): List<KartRuntime> = (0 until GameConstants.MAX_RACERS).map { slot ->
        KartRuntime(
            slotIndex = slot,
            playerName = if (slot == 0) "YOU" else "BOT$slot",
            isHuman = slot == 0,
            isLocalPlayer = slot == 0,
            colorHex = GameConstants.KART_COLORS[slot % GameConstants.KART_COLORS.size],
            bodyType = KartBodyType.fromId(slot % 5)
        )
    }

    private fun runToResults(session: RaceSession) {
        val dt = GameConstants.SIM_DT
        var steps = 0
        while (session.phase != RacePhase.RESULTS && steps < (400f / dt).toInt()) {
            session.stepFixed(dt = dt, localInput = TouchInputSnapshot(), autoAccelerate = true, smartSteering = true, showRocketStartTip = false)
            steps++
        }
        assertEquals("race did not reach results", RacePhase.RESULTS, session.phase)
    }

    @Test
    fun `a cup runs four races, then ends with a podium instead of looping`() {
        val audio = SynthAudio(context = null).apply { soundEnabled = false; hapticsEnabled = false }
        val session = RaceSession(synthAudio = audio, onNewBestLapRecorded = { _, _ -> false })
        session.autopilotLocal = true
        val cup = CupType.DESK_CUP
        session.startGrandPrixCup(cup, laps = 1, mode = ItemMode.SKILL_ONLY, difficulty = CpuDifficulty.PRO,
            cc = CcClass.CC_150, localSlot = 0, racerConfigs = racers())

        val tracksRaced = ArrayList<Int>()
        for (race in 0 until 4) {
            tracksRaced += session.currentTrack.id
            assertEquals(race == 3, session.isCupFinale)
            runToResults(session)
            if (race < 3) assertTrue(session.advanceGrandPrixNextRace(racers(), localSlot = 0))
        }
        assertEquals(cup.trackIds, tracksRaced)
        assertFalse("the cup must end after race 4", session.advanceGrandPrixNextRace(racers(), localSlot = 0))

        val standings = session.buildHudState(false).standings
        val perRace = GameConstants.GRAND_PRIX_POINTS.take(GameConstants.MAX_RACERS).sum()
        assertEquals(4 * perRace, standings.sumOf { it.cupTotalPoints })
        val table = CupRanking.finalOrder(standings)
        assertEquals((1..GameConstants.MAX_RACERS).toList(), table.map { it.rank })
        assertTrue(table.zipWithNext().all { (a, b) -> a.points >= b.points })
        assertTrue(CupRanking.localRank(standings) in 1..GameConstants.MAX_RACERS)
        audio.shutdown()
    }

    @Test
    fun `cup ties go to the better finish in the last race`() {
        fun s(slot: Int, pos: Int, pts: Int, local: Boolean = false) = RacerStanding(
            slotIndex = slot, position = pos, name = "R$slot", colorHex = 0L, isHuman = local, isLocal = local,
            totalTimeMs = 0L, bestLapMs = 0L, cupTotalPoints = pts
        )
        val order = CupRanking.finalOrder(listOf(s(0, 3, 40, local = true), s(1, 1, 40), s(2, 2, 52)))
        assertEquals(listOf(2, 1, 0), order.map { it.standing.slotIndex })
        assertEquals(3, CupRanking.localRank(listOf(s(0, 3, 40, local = true), s(1, 1, 40), s(2, 2, 52))))
        assertEquals(300, CupRanking.podiumBonusCoins(1))
        assertEquals(0, CupRanking.podiumBonusCoins(4))
    }
}
