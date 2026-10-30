package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.game.ControlSchemeType
import com.example.game.CpuDifficulty
import com.example.game.GameConstants
import com.example.game.GraphicsQuality
import com.example.game.KartBodyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class UserProfileAndSettings(
    val playerName: String = "RACER",
    val kartColorIndex: Int = 0,
    val kartBody: KartBodyType = KartBodyType.CLASSIC,
    val controlScheme: ControlSchemeType = ControlSchemeType.THUMB_STEER,
    val steerSensitivity: Float = 1.0f,
    val autoAccelerate: Boolean = true,
    val smartSteering: Boolean = true,
    val showMinimap: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.MEDIUM,
    val cpuDifficulty: CpuDifficulty = CpuDifficulty.NORMAL,
    val hasSeenRocketStartTip: Boolean = false,
    val hasSeenHowToPlay: Boolean = false,
    val bestLapMsByTrack: Map<Int, Long> = emptyMap()
) {
    val kartColorHex: Long
        get() = GameConstants.KART_COLORS.getOrElse(kartColorIndex) { GameConstants.KART_COLORS[0] }
}

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("desk_racer_lan_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserProfileAndSettings> = _state.asStateFlow()

    private fun load(): UserProfileAndSettings {
        val defaultName = "RACER${Random.nextInt(10, 99)}"
        val bestLaps = mutableMapOf<Int, Long>()
        for (trackId in 0..2) {
            val v = prefs.getLong("best_lap_$trackId", -1L)
            if (v > 0L) bestLaps[trackId] = v
        }
        return UserProfileAndSettings(
            playerName = prefs.getString("player_name", defaultName)?.take(10) ?: defaultName,
            kartColorIndex = prefs.getInt("kart_color_idx", 0).coerceIn(0, GameConstants.KART_COLORS.lastIndex),
            kartBody = KartBodyType.fromId(prefs.getInt("kart_body_id", 0)),
            controlScheme = ControlSchemeType.fromId(prefs.getInt("control_scheme", 0)),
            steerSensitivity = prefs.getFloat("steer_sensitivity", 1.0f).coerceIn(0.5f, 1.8f),
            autoAccelerate = prefs.getBoolean("auto_accelerate", true),
            smartSteering = prefs.getBoolean("smart_steering", true),
            showMinimap = prefs.getBoolean("show_minimap", true),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            hapticsEnabled = prefs.getBoolean("haptics_enabled", true),
            graphicsQuality = GraphicsQuality.fromId(prefs.getInt("graphics_quality", 1)),
            cpuDifficulty = CpuDifficulty.fromId(prefs.getInt("cpu_difficulty", 1)),
            hasSeenRocketStartTip = prefs.getBoolean("seen_rocket_tip", false),
            hasSeenHowToPlay = prefs.getBoolean("seen_how_to_play", false),
            bestLapMsByTrack = bestLaps
        )
    }

    fun updatePlayerName(name: String) {
        val clean = name.trim().take(10).ifEmpty { "RACER" }
        prefs.edit().putString("player_name", clean).apply()
        _state.value = _state.value.copy(playerName = clean)
    }

    fun updateKartColor(index: Int) {
        val idx = index.coerceIn(0, GameConstants.KART_COLORS.lastIndex)
        prefs.edit().putInt("kart_color_idx", idx).apply()
        _state.value = _state.value.copy(kartColorIndex = idx)
    }

    fun updateKartBody(body: KartBodyType) {
        prefs.edit().putInt("kart_body_id", body.id).apply()
        _state.value = _state.value.copy(kartBody = body)
    }

    fun updateControlScheme(scheme: ControlSchemeType) {
        prefs.edit().putInt("control_scheme", scheme.id).apply()
        _state.value = _state.value.copy(controlScheme = scheme)
    }

    fun updateSteerSensitivity(sens: Float) {
        val c = sens.coerceIn(0.5f, 1.8f)
        prefs.edit().putFloat("steer_sensitivity", c).apply()
        _state.value = _state.value.copy(steerSensitivity = c)
    }

    fun updateAutoAccelerate(enabled: Boolean) {
        prefs.edit().putBoolean("auto_accelerate", enabled).apply()
        _state.value = _state.value.copy(autoAccelerate = enabled)
    }

    fun updateSmartSteering(enabled: Boolean) {
        prefs.edit().putBoolean("smart_steering", enabled).apply()
        _state.value = _state.value.copy(smartSteering = enabled)
    }

    fun updateShowMinimap(enabled: Boolean) {
        prefs.edit().putBoolean("show_minimap", enabled).apply()
        _state.value = _state.value.copy(showMinimap = enabled)
    }

    fun updateSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
        _state.value = _state.value.copy(soundEnabled = enabled)
    }

    fun updateHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
        _state.value = _state.value.copy(hapticsEnabled = enabled)
    }

    fun updateGraphicsQuality(quality: GraphicsQuality) {
        prefs.edit().putInt("graphics_quality", quality.id).apply()
        _state.value = _state.value.copy(graphicsQuality = quality)
    }

    fun updateCpuDifficulty(diff: CpuDifficulty) {
        prefs.edit().putInt("cpu_difficulty", diff.id).apply()
        _state.value = _state.value.copy(cpuDifficulty = diff)
    }

    fun markRocketStartTipSeen() {
        prefs.edit().putBoolean("seen_rocket_tip", true).apply()
        _state.value = _state.value.copy(hasSeenRocketStartTip = true)
    }

    fun markHowToPlaySeen() {
        prefs.edit().putBoolean("seen_how_to_play", true).apply()
        _state.value = _state.value.copy(hasSeenHowToPlay = true)
    }

    fun recordLapTime(trackId: Int, lapMs: Long): Boolean {
        if (lapMs <= 0L) return false
        val currentBest = _state.value.bestLapMsByTrack[trackId]
        if (currentBest == null || lapMs < currentBest) {
            prefs.edit().putLong("best_lap_$trackId", lapMs).apply()
            val updated = _state.value.bestLapMsByTrack.toMutableMap()
            updated[trackId] = lapMs
            _state.value = _state.value.copy(bestLapMsByTrack = updated)
            return true
        }
        return false
    }
}
