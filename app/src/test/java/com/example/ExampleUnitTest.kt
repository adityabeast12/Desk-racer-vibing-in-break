package com.example

import com.example.game.HeadlessRaceTest
import com.example.game.TrackValidator
import com.example.game.Tracks
import com.example.render.Camera
import com.example.ui.hud.HudCoverageCalculator
import com.example.ui.hud.HudZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
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
    fun `verify 9 HUD zones never overlap and HUD coverage stays within v4 budget across all landscape screen sizes`() {
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

            // Part F2: Coverage budget check (<= 12% opaque, <= 22% total translucent)
            val effectiveScale = if (h < 380) 0.80f else 0.90f
            val metrics = HudCoverageCalculator.compute(
                screenWidthDp = w.toFloat(),
                screenHeightDp = h.toFloat(),
                hudScale = effectiveScale,
                showMinimap = true,
                showWrongWay = false
            )
            assertTrue(
                "Opaque HUD coverage ${metrics.opaqueFraction} exceeded 12% on ${w}x${h}dp",
                metrics.opaqueFraction <= 0.12f
            )
            assertTrue(
                "Total HUD coverage ${metrics.totalTranslucentFraction} exceeded 22% on ${w}x${h}dp",
                metrics.totalTranslucentFraction <= 0.22f
            )
            assertTrue(metrics.withinBudget)
        }
    }

    @Test
    fun `verify Part E2 Garage off-center camera projects all 8 kart bounding box corners inside left 60 percent stage`() {
        val screenSizes = listOf(
            640f to 360f,
            800f to 360f,
            915f to 412f
        )

        // Kart bounding box in local turntable space (half-extents around origin on plinth)
        val halfW = 0.85f
        val minY = 0.0f
        val maxY = 1.15f
        val halfL = 1.25f

        val corners = arrayOf(
            floatArrayOf(-halfW, minY, -halfL),
            floatArrayOf(+halfW, minY, -halfL),
            floatArrayOf(-halfW, maxY, -halfL),
            floatArrayOf(+halfW, maxY, -halfL),
            floatArrayOf(-halfW, minY, +halfL),
            floatArrayOf(+halfW, minY, +halfL),
            floatArrayOf(-halfW, maxY, +halfL),
            floatArrayOf(+halfW, maxY, +halfL)
        )

        val fovYDeg = 46f
        val camDist = Camera.computeGarageCameraDistance(fovYDeg = fovYDeg)
        val orbitYaw = 0.55f
        val eyeX = sin(orbitYaw) * camDist
        val eyeY = 1.55f
        val eyeZ = cos(orbitYaw) * camDist
        val targetX = 0f
        val targetY = 0.58f
        val targetZ = 0f

        // Compute camera basis vectors (look-at)
        var fx = targetX - eyeX
        var fy = targetY - eyeY
        var fz = targetZ - eyeZ
        val fLen = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fLen; fy /= fLen; fz /= fLen

        // Right = normalize(cross(f, up=(0,1,0))) = normalize((-fz, 0, fx))
        var rx = -fz
        var ry = 0f
        var rz = fx
        val rLen = kotlin.math.sqrt(rx * rx + rz * rz)
        rx /= rLen; rz /= rLen

        // True Up = cross(r, f)
        val ux = ry * fz - rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy - ry * fx

        val fScale = 1f / tan(Math.toRadians((fovYDeg * 0.5).toDouble())).toFloat()

        for ((w, h) in screenSizes) {
            val aspect = w / h
            val stageCenterX = w * 0.30f // center of left 60% stage
            val lensShiftX = Camera.computeLensShiftX(stageCenterX, w)
            assertEquals(-0.40f, lensShiftX, 0.001f)

            for ((idx, c) in corners.withIndex()) {
                val dx = c[0] - eyeX
                val dy = c[1] - eyeY
                val dz = c[2] - eyeZ

                val xCam = dx * rx + dy * ry + dz * rz
                val yCam = dx * ux + dy * uy + dz * uz
                val zCam = -(dx * fx + dy * fy + dz * fz) // negative in front of camera

                val negZ = (-zCam).coerceAtLeast(0.01f)
                val xNdc = (xCam * (fScale / aspect)) / negZ + lensShiftX
                val yNdc = (yCam * fScale) / negZ

                val screenX = (xNdc * 0.5f + 0.5f) * w
                val screenY = (0.5f - yNdc * 0.5f) * h

                assertTrue(
                    "Corner #$idx projected outside left 60% stage horizontally ($screenX vs 0..${w * 0.60f}) on ${w}x${h}",
                    screenX in (w * 0.03f)..(w * 0.57f)
                )
                assertTrue(
                    "Corner #$idx projected outside stage vertically ($screenY vs 0..$h) on ${w}x${h}",
                    screenY in (h * 0.08f)..(h * 0.92f)
                )
            }
        }
    }
}
