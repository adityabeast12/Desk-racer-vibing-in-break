package com.example

import com.example.game.HeadlessRaceTest
import com.example.game.TrackValidator
import com.example.game.Tracks
import com.example.ui.hud.HudZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `verify 9 HUD zones never overlap across all landscape screen sizes`() {
        val screenSizes = listOf(
            640 to 360,
            800 to 360,
            844 to 390,
            915 to 412,
            1024 to 600
        )
        val edgeZones = HudZone.entries.filter { it != HudZone.CENTER }

        for ((w, h) in screenSizes) {
            for (i in edgeZones.indices) {
                for (j in (i + 1) until edgeZones.size) {
                    val a = edgeZones[i]
                    val b = edgeZones[j]
                    val ax0 = a.leftFrac * w
                    val ax1 = a.rightFrac * w
                    val ay0 = a.topFrac * h
                    val ay1 = a.bottomFrac * h

                    val bx0 = b.leftFrac * w
                    val bx1 = b.rightFrac * w
                    val by0 = b.topFrac * h
                    val by1 = b.bottomFrac * h

                    val overlapX = maxOf(0f, minOf(ax1, bx1) - maxOf(ax0, bx0))
                    val overlapY = maxOf(0f, minOf(ay1, by1) - maxOf(ay0, by0))
                    val intersects = overlapX > 0.5f && overlapY > 0.5f
                    assertFalse(
                        "HUD zones ${a.name} and ${b.name} overlapped on ${w}x${h}dp",
                        intersects
                    )
                }
            }
        }
    }
}
