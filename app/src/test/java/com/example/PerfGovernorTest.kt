package com.example

import com.example.render.DeviceProfiler
import com.example.render.DeviceTier
import com.example.render.PerfGovernor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfGovernorTest {

    /** Simple GPU cost model: frame time scales with the budget level and refresh target. */
    private fun simulate(gov: PerfGovernor, panelHz: Float, msAtLevel: (Int) -> Float, seconds: Float): List<String> {
        val log = ArrayList<String>()
        var t = 0f
        while (t < seconds) {
            val cap = if (gov.capHz > 0f) minOf(gov.capHz, panelHz) else panelHz
            val vsync = 1f / cap
            val work = msAtLevel(gov.level) / 1000f
            val dt = maxOf(vsync, kotlin.math.ceil(work / vsync) * vsync) // frames land on vsync
            if (gov.onFrame(dt, panelHz)) log += "t=%.1f level=${gov.level} cap=${gov.capHz}".format(t)
            t += dt
        }
        return log
    }

    @Test
    fun `low-end phone on a 90 Hz panel settles at 60 fps without oscillating`() {
        // potato 9 ms, each level +4.5 ms: holds 60 (16.7 ms) at level 1, not at 2
        val gov = PerfGovernor(startLevel = 3)
        val log = simulate(gov, 90f, { 9f + it * 4.5f }, 120f)
        println("GOV low-end: $log final level=${gov.level} cap=${gov.capHz}")
        assertEquals(60f, gov.capHz)
        assertTrue("settled at a level that holds 60", 9f + gov.level * 4.5f <= 16.7f)
        assertTrue("no endless oscillation", log.size < 12)
    }

    @Test
    fun `flagship climbs to max and keeps high refresh`() {
        val gov = PerfGovernor(startLevel = 2)
        val log = simulate(gov, 120f, { 3f + it * 0.8f }, 60f)
        println("GOV flagship: $log")
        assertEquals(4, gov.level)
        assertEquals(0f, gov.capHz)
    }

    @Test
    fun `hopeless device falls back to a steady 30`() {
        val gov = PerfGovernor(startLevel = 2)
        simulate(gov, 60f, { 26f + it * 6f }, 60f)
        assertEquals(0, gov.level)
        assertEquals(30f, gov.capHz)
    }

    @Test
    fun `tiers`() {
        assertEquals(DeviceTier.LOW, DeviceProfiler.classify(3f, 8, 2.0f, "Mali-G52 MC2", 0))
        assertEquals(DeviceTier.HIGH, DeviceProfiler.classify(12f, 8, 3.2f, "Adreno (TM) 740", 33))
        assertTrue(DeviceProfiler.classify(6f, 8, 2.4f, "Mali-G68 MC4", 0) != DeviceTier.HIGH)
    }
}
