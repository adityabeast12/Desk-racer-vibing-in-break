package com.example

import com.example.game.HeadlessRaceTest
import com.example.game.TrackValidator
import com.example.game.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `validate all 8 tracks and run 8-bot 3-lap headless simulation`() {
        assertEquals(8, Tracks.all.size)

        val validations = TrackValidator.validateAll()
        for (v in validations) {
            assertTrue("Track ${v.trackName} validation failed: ${v.issues}", v.isValid)
        }

        val qaResults = HeadlessRaceTest.runAllTracksQa()
        assertEquals(8, qaResults.size)
        for (qa in qaResults) {
            println(
                "QA [${qa.trackName}]: finished=${qa.finishedCount}/${qa.totalBots}, " +
                    "avgLap=${"%.1f".format(qa.averageLapMs / 1000f)}s, " +
                    "slowest=${"%.1f".format(qa.slowestBotTimeMs / 1000f)}s, " +
                    "watchdogs=${qa.totalWatchdogCount}"
            )
            assertEquals("Expected all 8 bots to finish 3 laps on ${qa.trackName}", 8, qa.finishedCount)
            assertTrue("Track QA failed on ${qa.trackName}", qa.passed)
        }
    }
}
