package com.example.game

import com.example.data.Prefs
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
 * Runs the fixed-timestep 60 Hz simulation off the UI thread.
 * Publishes lock-free WorldRenderSnapshot for OpenGL ES 3.0 and throttles HudState to ~15 Hz for Compose.
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

    var onSimTickForNetwork: ((WorldRenderSnapshot) -> Unit)? = null

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var hudTickCounter = 0
            var netTickCounter = 0
            while (isActive) {
                val frameStartNs = System.nanoTime()
                val settings = prefs.state.value

                if (!isPausedSolo) {
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

                    // Network state channel @ 30 Hz (every 2nd 60 Hz tick)
                    netTickCounter++
                    if (netTickCounter >= 2) {
                        netTickCounter = 0
                        onSimTickForNetwork?.invoke(renderSnap)
                    }

                    // Throttled HUD StateFlow @ 15 Hz (every 4th 60 Hz tick)
                    hudTickCounter++
                    if (hudTickCounter >= 4) {
                        hudTickCounter = 0
                        _hudState.value = raceSession.buildHudState(
                            showRocketTipPref = !settings.hasSeenRocketStartTip
                        )
                    }
                }

                val elapsedMs = (System.nanoTime() - frameStartNs) / 1_000_000L
                val targetFrameMs = if (settings.graphicsQuality == GraphicsQuality.LOW) 33L else 16L
                val sleepMs = (targetFrameMs - elapsedMs).coerceAtLeast(2L)
                delay(sleepMs)
            }
        }
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
