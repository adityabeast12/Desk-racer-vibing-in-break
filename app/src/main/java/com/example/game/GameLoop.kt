package com.example.game

import com.example.data.Prefs
import com.example.render.RenderInterp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Part H1 & H2: Runs the fixed-timestep 60 Hz simulation off the UI thread.
 * Publishes lock-free WorldRenderSnapshot for OpenGL ES 3.0 and throttles HudState to ~15 Hz for Compose.
 * Supports thermal/battery saver throttling (`thermalThrottle30Fps`).
 */
class GameLoop(
    val inputState: InputState,
    val raceSession: RaceSession,
    private val prefs: Prefs
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    val latestRenderSnapshot = AtomicReference(raceSession.buildWorldRenderSnapshot())

    private val _hudState = MutableStateFlow(raceSession.buildHudState(showRocketTipPref = false))
    val hudState: StateFlow<HudState> = _hudState.asStateFlow()

    @Volatile
    var isPausedSolo: Boolean = false

    /** Bumped when the Activity pauses the race for lifecycle reasons, so the UI can open the pause menu. */
    val lifecyclePauseSignal = MutableStateFlow(0)

    @Volatile
    var thermalThrottle30Fps: Boolean = false

    var onSimTickForNetwork: ((WorldRenderSnapshot) -> Unit)? = null

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            // v6 fix: the simulation is driven by REAL elapsed time with a fixed-step accumulator.
            // Previously one 1/60 s step ran per loop iteration, so a 33 ms throttle (thermal step-down
            // or LOW graphics, which the step-down also persists) made the whole game run at half speed.
            // Throttling now only changes how often we wake, never the game speed.
            val stepNs = (GameConstants.SIM_DT * 1_000_000_000.0).toLong()
            var lastNs = System.nanoTime()
            var accNs = 0L
            RenderInterp.reset()
            while (isActive) {
                val nowNs = System.nanoTime()
                val frameNs = (nowNs - lastNs).coerceIn(0L, MAX_FRAME_NS)
                lastNs = nowNs
                val settings = prefs.state.value

                if (captureMode) {
                    // QA capture: the renderer drives the sim (captureAdvance) on a virtual clock
                    accNs = 0L
                } else if (!isPausedSolo) {
                    accNs += frameNs
                    var steps = 0
                    while (accNs >= stepNs && steps < MAX_STEPS_PER_WAKE) {
                        accNs -= stepNs
                        steps++
                        runOneStep(nowNs - accNs)
                    }
                    if (steps >= MAX_STEPS_PER_WAKE) accNs = 0L // device stalled: drop backlog, don't spiral
                } else {
                    accNs = 0L
                }

                // v6.2: always wake for the next 60 Hz step (cheap) so render interpolation never runs
                // dry between bursts; thermal savings come from the renderer/refresh-rate cap instead.
                val untilNextStepMs = ((stepNs - accNs).coerceAtLeast(0L) / 1_000_000L)
                val sleepMs = untilNextStepMs.coerceIn(1L, 16L)
                delay(sleepMs)
            }
        }
    }

    private var hudTickCounter = 0
    private var netTickCounter = 0

    private fun runOneStep(publishNs: Long) {
        val settings = prefs.state.value
        val input = inputState.consumeFrameInput()
        raceSession.stepFixed(
            dt = GameConstants.SIM_DT,
            localInput = input,
            autoAccelerate = settings.autoAccelerate,
            smartSteering = settings.smartSteering,
            showRocketStartTip = !settings.hasSeenRocketStartTip
        )
        val renderSnap = raceSession.buildWorldRenderSnapshot()
        latestRenderSnapshot.set(renderSnap)
        RenderInterp.publish(renderSnap, publishNs)

        // Network state channel @ 30 Hz of sim time (every 2nd step)
        netTickCounter++
        if (netTickCounter >= 2) {
            netTickCounter = 0
            onSimTickForNetwork?.invoke(renderSnap)
        }

        // Throttled HUD StateFlow @ 15 Hz of sim time (every 4th step)
        hudTickCounter++
        if (hudTickCounter >= 4) {
            hudTickCounter = 0
            _hudState.value = raceSession.buildHudState(
                showRocketTipPref = !settings.hasSeenRocketStartTip
            )
        }
    }

    /** QA capture mode: deterministic 30 fps game time regardless of how slow the device renders. */
    @Volatile
    var captureMode: Boolean = false
    private var captureVirtualNs = 0L

    /** Called by the renderer once per drawn frame in capture mode; returns the virtual render time. */
    @Synchronized
    fun captureAdvance(): Long {
        val stepNs = (GameConstants.SIM_DT * 1_000_000_000.0).toLong()
        if (captureVirtualNs == 0L) captureVirtualNs = System.nanoTime()
        if (!isPausedSolo) {
            repeat(2) {
                captureVirtualNs += stepNs
                runOneStep(captureVirtualNs)
            }
        } else {
            captureVirtualNs += stepNs * 2
        }
        return captureVirtualNs - stepNs / 2
    }

    private companion object {
        const val MAX_FRAME_NS = 250_000_000L
        const val MAX_STEPS_PER_WAKE = 8
    }

    fun forceHudRefresh() {
        val settings = prefs.state.value
        latestRenderSnapshot.set(raceSession.buildWorldRenderSnapshot())
        _hudState.value = raceSession.buildHudState(!settings.hasSeenRocketStartTip)
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }
}
