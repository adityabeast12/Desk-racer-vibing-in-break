package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.game.CcClass
import com.example.game.ControlSchemeType
import com.example.game.CpuDifficulty
import com.example.game.CupType
import com.example.game.GameConstants
import com.example.game.GraphicsQuality
import com.example.game.ItemMode
import com.example.game.KartBodyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.random.Random

data class GhostPoseSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float
)

data class StoredGhostLap(
    val trackId: Int,
    val lapTimeMs: Long,
    val bodyType: KartBodyType,
    val colorHex: Long,
    val samples: List<GhostPoseSample> // Sampled at 20 Hz (50ms intervals)
)

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
    val cpuDifficulty: CpuDifficulty = CpuDifficulty.PRO,
    val raceWithBots: Boolean = true,
    val botCount: Int = 7,
    val selectedLaps: Int = 3,
    val selectedItemMode: ItemMode = ItemMode.NORMAL,
    val selectedCcClass: CcClass = CcClass.CC_150,
    val selectedTrackId: Int = 0,
    val selectedCup: CupType = CupType.DESK_CUP,
    val hasSeenRocketStartTip: Boolean = false,
    val hasSeenHowToPlay: Boolean = false,
    val bestLapMsByTrack: Map<Int, Long> = emptyMap(),
    val cupTrophies: Map<Int, Int> = emptyMap() // cupId -> best place (1=Gold, 2=Silver, 3=Bronze)
) {
    val kartColorHex: Long
        get() = GameConstants.KART_COLORS.getOrElse(kartColorIndex) { GameConstants.KART_COLORS[0] }
}

class Prefs(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("desk_racer_lan_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserProfileAndSettings> = _state.asStateFlow()

    private fun load(): UserProfileAndSettings {
        val defaultName = "RACER${Random.nextInt(10, 99)}"
        val bestLaps = mutableMapOf<Int, Long>()
        for (trackId in 0..7) {
            val v = prefs.getLong("best_lap_$trackId", -1L)
            if (v > 0L) bestLaps[trackId] = v
        }
        val trophies = mutableMapOf<Int, Int>()
        for (cupId in 0..1) {
            val rank = prefs.getInt("cup_trophy_$cupId", 0)
            if (rank in 1..3) trophies[cupId] = rank
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
            raceWithBots = prefs.getBoolean("race_with_bots", true),
            botCount = prefs.getInt("bot_count", 7).coerceIn(0, 7),
            selectedLaps = prefs.getInt("selected_laps", 3).coerceIn(1, 5),
            selectedItemMode = ItemMode.fromId(prefs.getInt("selected_item_mode", 0)),
            selectedCcClass = CcClass.fromId(prefs.getInt("selected_cc_class", 2)),
            selectedTrackId = prefs.getInt("selected_track_id", 0).coerceIn(0, 7),
            selectedCup = CupType.fromId(prefs.getInt("selected_cup_id", 0)),
            hasSeenRocketStartTip = prefs.getBoolean("seen_rocket_tip", false),
            hasSeenHowToPlay = prefs.getBoolean("seen_how_to_play", false),
            bestLapMsByTrack = bestLaps,
            cupTrophies = trophies
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

    fun updateRaceWithBots(withBots: Boolean) {
        prefs.edit().putBoolean("race_with_bots", withBots).apply()
        _state.value = _state.value.copy(raceWithBots = withBots)
    }

    fun updateBotCount(count: Int) {
        val c = count.coerceIn(0, 7)
        prefs.edit().putInt("bot_count", c).apply()
        _state.value = _state.value.copy(botCount = c)
    }

    fun updateSelectedLaps(laps: Int) {
        val l = laps.coerceIn(1, 5)
        prefs.edit().putInt("selected_laps", l).apply()
        _state.value = _state.value.copy(selectedLaps = l)
    }

    fun updateSelectedItemMode(mode: ItemMode) {
        prefs.edit().putInt("selected_item_mode", mode.id).apply()
        _state.value = _state.value.copy(selectedItemMode = mode)
    }

    fun updateSelectedCcClass(cc: CcClass) {
        prefs.edit().putInt("selected_cc_class", cc.id).apply()
        _state.value = _state.value.copy(selectedCcClass = cc)
    }

    fun updateSelectedTrackId(trackId: Int) {
        val t = trackId.coerceIn(0, 7)
        prefs.edit().putInt("selected_track_id", t).apply()
        _state.value = _state.value.copy(selectedTrackId = t)
    }

    fun updateSelectedCup(cup: CupType) {
        prefs.edit().putInt("selected_cup_id", cup.id).apply()
        _state.value = _state.value.copy(selectedCup = cup)
    }

    fun recordCupTrophy(cupId: Int, rank: Int) {
        if (rank !in 1..3) return
        val existing = _state.value.cupTrophies[cupId] ?: 99
        if (rank < existing) {
            prefs.edit().putInt("cup_trophy_$cupId", rank).apply()
            val updated = _state.value.cupTrophies.toMutableMap()
            updated[cupId] = rank
            _state.value = _state.value.copy(cupTrophies = updated)
        }
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

    /**
     * Saves a 20 Hz Time Trial ghost lap to local file storage (Part C).
     */
    fun saveTimeTrialGhost(ghost: StoredGhostLap) {
        try {
            val file = File(context.filesDir, "ghost_track_${ghost.trackId}.bin")
            DataOutputStream(file.outputStream().buffered()).use { dos ->
                dos.writeInt(1) // format version
                dos.writeInt(ghost.trackId)
                dos.writeLong(ghost.lapTimeMs)
                dos.writeInt(ghost.bodyType.id)
                dos.writeLong(ghost.colorHex)
                dos.writeInt(ghost.samples.size)
                for (s in ghost.samples) {
                    dos.writeFloat(s.x)
                    dos.writeFloat(s.y)
                    dos.writeFloat(s.z)
                    dos.writeFloat(s.headingRad)
                }
            }
        } catch (e: Exception) {
            Log.w("Prefs", "Failed to save ghost for track ${ghost.trackId}: ${e.message}")
        }
    }

    fun loadTimeTrialGhost(trackId: Int): StoredGhostLap? {
        val file = File(context.filesDir, "ghost_track_$trackId.bin")
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { dis ->
                val ver = dis.readInt()
                if (ver != 1) return null
                val tId = dis.readInt()
                val lapMs = dis.readLong()
                val body = KartBodyType.fromId(dis.readInt())
                val color = dis.readLong()
                val count = dis.readInt().coerceIn(0, 20000)
                val list = ArrayList<GhostPoseSample>(count)
                for (i in 0 until count) {
                    list.add(
                        GhostPoseSample(
                            x = dis.readFloat(),
                            y = dis.readFloat(),
                            z = dis.readFloat(),
                            headingRad = dis.readFloat()
                        )
                    )
                }
                StoredGhostLap(tId, lapMs, body, color, list)
            }
        } catch (e: Exception) {
            Log.w("Prefs", "Failed to load ghost for track $trackId: ${e.message}")
            null
        }
    }
}
