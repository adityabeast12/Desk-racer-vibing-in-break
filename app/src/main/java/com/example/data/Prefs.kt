package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
    val playerId: String = "local-player",
    val playerName: String = "RACER",
    val kartColorIndex: Int = 0,
    val kartBody: KartBodyType = KartBodyType.CLASSIC,
    val toyCarLiveryIndex: Int = 0, // 0..2 hue shift liveries for Classic Toy (Part B8)
    val paintFinishIndex: Int = 0,  // 0=Gloss, 1=Matte, 2=Metallic, 3=Pearl (Part E5)
    val wheelStyleIndex: Int = 0,
    val controlScheme: ControlSchemeType = ControlSchemeType.SMART,
    val steerSensitivity: Float = 1.0f,
    val tiltDeadZoneDeg: Float = 2.0f,
    val autoAccelerate: Boolean = true,
    val smartSteering: Boolean = true,
    val separateHopButton: Boolean = false,
    val controlsOpacity: Float = 0.90f, // 0.30..1.00 (Part F6)
    val controlsScale: Float = 1.00f,   // 0.85..1.15 (Part F6)
    val hudScale: Float = 0.90f,        // 0.70..1.30 (Part F1: default 90%)
    val controlsOffsetXFrac: Float = 0f,
    val controlsOffsetYFrac: Float = 0f,
    val leftHandedMirror: Boolean = false,
    val showMinimap: Boolean = true,
    val showHudDebugZones: Boolean = false, // Part E5/F2
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.MEDIUM,
    val fxaaEnabled: Boolean = true,
    val bloomEnabled: Boolean = true,
    val tiltShiftDofEnabled: Boolean = true,
    val filmGrainEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
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
    val completedInteractiveTutorial: Boolean = false,
    val coinsBalance: Int = 300,
    val gemsBalance: Int = 60,
    val deskPassXp: Int = 240,
    val hasDeskPassPremium: Boolean = false,
    val hasProUpgrade: Boolean = false,
    val ownedShopIds: Set<String> = setOf("body_0", "paint_0", "paint_1", "paint_2"),
    val claimedFreePassTiers: Set<Int> = emptySet(),
    val claimedPremiumPassTiers: Set<Int> = emptySet(),
    val soloRacesSinceLastAd: Int = 0,
    val completedRacesCount: Int = 0,
    val bestLapMsByTrack: Map<Int, Long> = emptyMap(),
    val cupTrophies: Map<Int, Int> = emptyMap() // cupId -> best place (1=Gold, 2=Silver, 3=Bronze)
) {
    val kartColorHex: Long
        get() = GameConstants.KART_COLORS.getOrElse(kartColorIndex) { GameConstants.KART_COLORS[0] }

    val deskPassLevel: Int
        get() = ((deskPassXp / 200) + 1).coerceIn(1, 40)

    val deskPassLevelProgress: Float
        get() = ((deskPassXp % 200) / 200f).coerceIn(0f, 1f)

    val kartLiveryIndex: Int get() = toyCarLiveryIndex
    val buttonScale: Float get() = controlsScale
    val hudOpacity: Float get() = controlsOpacity
    val swapHands: Boolean get() = leftHandedMirror
    val postFxEnabled: Boolean get() = fxaaEnabled && bloomEnabled
    val contactShadowsEnabled: Boolean get() = fxaaEnabled
    val showDebugHudZones: Boolean get() = showHudDebugZones
    val starterPackOwned: Boolean get() = "starter_pack" in ownedShopIds
    val proUnlocked: Boolean get() = hasProUpgrade
    val trackPackUnlocked: Boolean get() = hasProUpgrade || "track_pack" in ownedShopIds
    val deskPassPremium: Boolean get() = hasDeskPassPremium
    val unlockedCosmetics: Set<String> get() = ownedShopIds
    val unlockedBodies: Set<Int>
        get() = (0..5).filter { it < 5 || hasProUpgrade || "body_$it" in ownedShopIds }.toSet()
    val claimedPassTiers: Set<Int> get() = claimedFreePassTiers + claimedPremiumPassTiers
}

class Prefs(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("desk_racer_lan_prefs", Context.MODE_PRIVATE)

    val installId: String = run {
        val existing = prefs.getString("install_id", null)
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString("install_id", generated).apply()
            generated
        }
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserProfileAndSettings> = _state.asStateFlow()

    /**
     * Part J1: HMAC key derived from Build fingerprint + installId to deter casual tampering
     * of offline coin/gem balances.
     */
    private fun computeBalanceHmac(coins: Int, gems: Int, xp: Int, pro: Boolean, pass: Boolean): String {
        return try {
            val secret = "DRv3:${Build.FINGERPRINT}:${Build.MODEL}:$installId"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val payload = "$coins|$gems|$xp|$pro|$pass".toByteArray(Charsets.UTF_8)
            val digest = mac.doFinal(payload)
            digest.joinToString("") { "%02x".format(it) }.take(32)
        } catch (e: Exception) {
            Log.w("DeskRacer", "HMAC fallback: ${e.message}")
            "${coins * 31 + gems * 17 + xp}"
        }
    }

    private fun saveBalancesWithHmac(
        coins: Int,
        gems: Int,
        xp: Int,
        pro: Boolean,
        pass: Boolean
    ) {
        val tag = computeBalanceHmac(coins, gems, xp, pro, pass)
        prefs.edit()
            .putInt("coins_balance", coins)
            .putInt("gems_balance", gems)
            .putInt("desk_pass_xp", xp)
            .putBoolean("has_pro_upgrade", pro)
            .putBoolean("has_desk_pass_prem", pass)
            .putString("wallet_hmac", tag)
            .apply()
    }

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

        val rawCoins = prefs.getInt("coins_balance", 300).coerceAtLeast(0)
        val rawGems = prefs.getInt("gems_balance", 60).coerceAtLeast(0)
        val rawXp = prefs.getInt("desk_pass_xp", 240).coerceAtLeast(0)
        val rawPro = prefs.getBoolean("has_pro_upgrade", false)
        val rawPass = prefs.getBoolean("has_desk_pass_prem", false)
        val storedHmac = prefs.getString("wallet_hmac", null)
        val expectedHmac = computeBalanceHmac(rawCoins, rawGems, rawXp, rawPro, rawPass)

        val validWallet = storedHmac == null || storedHmac == expectedHmac
        if (storedHmac == null) {
            saveBalancesWithHmac(rawCoins, rawGems, rawXp, rawPro, rawPass)
        }

        val scheme = ControlSchemeType.fromId(prefs.getInt("control_scheme", 0))
        val ownedIds = prefs.getStringSet(
            "owned_shop_ids",
            setOf("body_0", "paint_0", "paint_1", "paint_2")
        )?.toSet() ?: setOf("body_0", "paint_0", "paint_1", "paint_2")

        val claimedFree = prefs.getStringSet("claimed_free_tiers", emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        val claimedPrem = prefs.getStringSet("claimed_prem_tiers", emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

        return UserProfileAndSettings(
            playerId = installId,
            playerName = sanitizePlayerName(prefs.getString("player_name", defaultName) ?: defaultName),
            kartColorIndex = prefs.getInt("kart_color_idx", 0).coerceIn(0, GameConstants.KART_COLORS.lastIndex),
            kartBody = KartBodyType.fromId(prefs.getInt("kart_body_id", 0)),
            toyCarLiveryIndex = prefs.getInt("toy_livery_idx", 0).coerceIn(0, 2),
            paintFinishIndex = prefs.getInt("paint_finish_idx", 0).coerceIn(0, 3),
            wheelStyleIndex = prefs.getInt("wheel_style_idx", 0).coerceIn(0, 5),
            controlScheme = scheme,
            steerSensitivity = prefs.getFloat("steer_sensitivity", 1.0f).coerceIn(0.6f, 1.6f),
            tiltDeadZoneDeg = prefs.getFloat("tilt_deadzone_deg", 2.0f).coerceIn(0.5f, 6.0f),
            autoAccelerate = prefs.getBoolean("auto_accelerate", true),
            smartSteering = prefs.getBoolean("smart_steering", scheme == ControlSchemeType.SMART),
            separateHopButton = prefs.getBoolean("separate_hop_btn", false),
            controlsOpacity = prefs.getFloat("controls_opacity", 0.90f).coerceIn(0.30f, 1.00f),
            controlsScale = prefs.getFloat("controls_scale", 1.00f).coerceIn(0.85f, 1.15f),
            hudScale = prefs.getFloat("hud_scale", 0.90f).coerceIn(0.70f, 1.30f),
            controlsOffsetXFrac = prefs.getFloat("controls_offset_x", 0f).coerceIn(-0.15f, 0.15f),
            controlsOffsetYFrac = prefs.getFloat("controls_offset_y", 0f).coerceIn(-0.15f, 0.15f),
            leftHandedMirror = prefs.getBoolean("left_handed_mirror", false),
            showMinimap = prefs.getBoolean("show_minimap", true),
            showHudDebugZones = prefs.getBoolean("show_hud_debug_zones", false),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            hapticsEnabled = prefs.getBoolean("haptics_enabled", true),
            graphicsQuality = GraphicsQuality.fromId(prefs.getInt("graphics_quality", 1)),
            fxaaEnabled = prefs.getBoolean("fxaa_enabled", true),
            bloomEnabled = prefs.getBoolean("bloom_enabled", true),
            tiltShiftDofEnabled = prefs.getBoolean("tilt_shift_dof", true),
            filmGrainEnabled = prefs.getBoolean("film_grain", true),
            reducedMotion = prefs.getBoolean("reduced_motion", false),
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
            completedInteractiveTutorial = prefs.getBoolean("completed_tutorial", false),
            coinsBalance = if (validWallet) rawCoins else 300,
            gemsBalance = if (validWallet) rawGems else 60,
            deskPassXp = if (validWallet) rawXp else 240,
            hasDeskPassPremium = if (validWallet) rawPass else false,
            hasProUpgrade = if (validWallet) rawPro else false,
            ownedShopIds = ownedIds,
            claimedFreePassTiers = claimedFree,
            claimedPremiumPassTiers = claimedPrem,
            completedRacesCount = prefs.getInt("completed_races_count", 0).coerceAtLeast(0),
            bestLapMsByTrack = bestLaps,
            cupTrophies = trophies
        )
    }

    fun updatePlayerName(name: String) {
        val clean = sanitizePlayerName(name)
        prefs.edit().putString("player_name", clean).apply()
        _state.value = _state.value.copy(playerName = clean)
    }

    fun updatePaintFinish(index: Int) {
        val idx = index.coerceIn(0, 3)
        prefs.edit().putInt("paint_finish_idx", idx).apply()
        _state.value = _state.value.copy(paintFinishIndex = idx)
    }

    fun updateHudScale(scale: Float) {
        val s = scale.coerceIn(0.70f, 1.30f)
        prefs.edit().putFloat("hud_scale", s).apply()
        _state.value = _state.value.copy(hudScale = s)
    }

    fun incrementCompletedRaces() {
        val next = _state.value.completedRacesCount + 1
        prefs.edit().putInt("completed_races_count", next).apply()
        _state.value = _state.value.copy(completedRacesCount = next)
    }

    companion object {
        private val BLOCKED_WORDS = listOf(
            "fuck", "shit", "bitch", "asshole", "cunt", "dick", "pussy", "nigger", "faggot", "slut", "whore", "bastard"
        )

        fun sanitizePlayerName(raw: String): String {
            var cleaned = raw.trim().replace(Regex("[^A-Za-z0-9 _\\-]"), "").take(12)
            val lower = cleaned.lowercase()
            for (bad in BLOCKED_WORDS) {
                if (lower.contains(bad)) {
                    cleaned = "RACER"
                    break
                }
            }
            return cleaned.ifEmpty { "RACER" }
        }
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

    fun updateToyCarLivery(index: Int) {
        val idx = index.coerceIn(0, 2)
        prefs.edit().putInt("toy_livery_idx", idx).apply()
        _state.value = _state.value.copy(toyCarLiveryIndex = idx)
    }

    fun updateWheelStyle(index: Int) {
        val idx = index.coerceIn(0, 5)
        prefs.edit().putInt("wheel_style_idx", idx).apply()
        _state.value = _state.value.copy(wheelStyleIndex = idx)
    }

    fun updateControlScheme(scheme: ControlSchemeType) {
        val smartDefault = when (scheme) {
            ControlSchemeType.SMART -> true
            ControlSchemeType.PRO -> false
            else -> _state.value.smartSteering
        }
        val autoAccelDefault = when (scheme) {
            ControlSchemeType.SMART -> true
            else -> _state.value.autoAccelerate
        }
        prefs.edit()
            .putInt("control_scheme", scheme.id)
            .putBoolean("smart_steering", smartDefault)
            .putBoolean("auto_accelerate", autoAccelDefault)
            .apply()
        _state.value = _state.value.copy(
            controlScheme = scheme,
            smartSteering = smartDefault,
            autoAccelerate = autoAccelDefault
        )
    }

    fun updateSteerSensitivity(sens: Float) {
        val c = sens.coerceIn(0.6f, 1.6f)
        prefs.edit().putFloat("steer_sensitivity", c).apply()
        _state.value = _state.value.copy(steerSensitivity = c)
    }

    fun updateTiltDeadZone(deg: Float) {
        val c = deg.coerceIn(0.5f, 6.0f)
        prefs.edit().putFloat("tilt_deadzone_deg", c).apply()
        _state.value = _state.value.copy(tiltDeadZoneDeg = c)
    }

    fun updateAutoAccelerate(enabled: Boolean) {
        prefs.edit().putBoolean("auto_accelerate", enabled).apply()
        _state.value = _state.value.copy(autoAccelerate = enabled)
    }

    fun updateSmartSteering(enabled: Boolean) {
        prefs.edit().putBoolean("smart_steering", enabled).apply()
        _state.value = _state.value.copy(smartSteering = enabled)
    }

    fun updateSeparateHopButton(enabled: Boolean) {
        prefs.edit().putBoolean("separate_hop_btn", enabled).apply()
        _state.value = _state.value.copy(separateHopButton = enabled)
    }

    fun updateControlsLayout(
        opacity: Float = _state.value.controlsOpacity,
        scale: Float = _state.value.controlsScale,
        offsetXFrac: Float = _state.value.controlsOffsetXFrac,
        offsetYFrac: Float = _state.value.controlsOffsetYFrac,
        leftHanded: Boolean = _state.value.leftHandedMirror
    ) {
        val op = opacity.coerceIn(0.30f, 1.00f)
        val sc = scale.coerceIn(0.85f, 1.15f)
        val ox = offsetXFrac.coerceIn(-0.15f, 0.15f)
        val oy = offsetYFrac.coerceIn(-0.15f, 0.15f)
        prefs.edit()
            .putFloat("controls_opacity", op)
            .putFloat("controls_scale", sc)
            .putFloat("controls_offset_x", ox)
            .putFloat("controls_offset_y", oy)
            .putBoolean("left_handed_mirror", leftHanded)
            .apply()
        _state.value = _state.value.copy(
            controlsOpacity = op,
            controlsScale = sc,
            controlsOffsetXFrac = ox,
            controlsOffsetYFrac = oy,
            leftHandedMirror = leftHanded
        )
    }

    fun resetControlsLayout() {
        updateControlsLayout(
            opacity = 0.90f,
            scale = 1.00f,
            offsetXFrac = 0f,
            offsetYFrac = 0f,
            leftHanded = false
        )
    }

    fun updateShowMinimap(enabled: Boolean) {
        prefs.edit().putBoolean("show_minimap", enabled).apply()
        _state.value = _state.value.copy(showMinimap = enabled)
    }

    fun updateShowHudDebugZones(enabled: Boolean) {
        prefs.edit().putBoolean("show_hud_debug_zones", enabled).apply()
        _state.value = _state.value.copy(showHudDebugZones = enabled)
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

    fun updatePostEffects(
        fxaa: Boolean = _state.value.fxaaEnabled,
        bloom: Boolean = _state.value.bloomEnabled,
        tiltShiftDof: Boolean = _state.value.tiltShiftDofEnabled,
        filmGrain: Boolean = _state.value.filmGrainEnabled,
        reducedMotion: Boolean = _state.value.reducedMotion
    ) {
        prefs.edit()
            .putBoolean("fxaa_enabled", fxaa)
            .putBoolean("bloom_enabled", bloom)
            .putBoolean("tilt_shift_dof", tiltShiftDof)
            .putBoolean("film_grain", filmGrain)
            .putBoolean("reduced_motion", reducedMotion)
            .apply()
        _state.value = _state.value.copy(
            fxaaEnabled = fxaa,
            bloomEnabled = bloom,
            tiltShiftDofEnabled = tiltShiftDof,
            filmGrainEnabled = filmGrain,
            reducedMotion = reducedMotion
        )
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

    fun addRaceRewards(coinsEarned: Int, xpEarned: Int) {
        val cur = _state.value
        val proMult = if (cur.hasProUpgrade) 1.10f else 1.0f
        val newCoins = (cur.coinsBalance + (coinsEarned * proMult).toInt()).coerceAtLeast(0)
        val newXp = (cur.deskPassXp + xpEarned).coerceAtLeast(0)
        saveBalancesWithHmac(newCoins, cur.gemsBalance, newXp, cur.hasProUpgrade, cur.hasDeskPassPremium)
        _state.value = cur.copy(
            coinsBalance = newCoins,
            deskPassXp = newXp,
            soloRacesSinceLastAd = cur.soloRacesSinceLastAd + 1
        )
    }

    fun addCoinsAndGems(deltaCoins: Int, deltaGems: Int): Boolean {
        val cur = _state.value
        val nextCoins = cur.coinsBalance + deltaCoins
        val nextGems = cur.gemsBalance + deltaGems
        if (nextCoins < 0 || nextGems < 0) return false
        saveBalancesWithHmac(nextCoins, nextGems, cur.deskPassXp, cur.hasProUpgrade, cur.hasDeskPassPremium)
        _state.value = cur.copy(coinsBalance = nextCoins, gemsBalance = nextGems)
        return true
    }

    fun unlockShopItem(itemId: String, costCoins: Int = 0, costGems: Int = 0): Boolean {
        val cur = _state.value
        if (itemId in cur.ownedShopIds) return true
        if (cur.coinsBalance < costCoins || cur.gemsBalance < costGems) return false
        val nextCoins = cur.coinsBalance - costCoins
        val nextGems = cur.gemsBalance - costGems
        val nextOwned = cur.ownedShopIds + itemId
        saveBalancesWithHmac(nextCoins, nextGems, cur.deskPassXp, cur.hasProUpgrade, cur.hasDeskPassPremium)
        prefs.edit().putStringSet("owned_shop_ids", nextOwned).apply()
        _state.value = cur.copy(
            coinsBalance = nextCoins,
            gemsBalance = nextGems,
            ownedShopIds = nextOwned
        )
        return true
    }

    fun grantEntitlement(proUpgrade: Boolean = false, deskPassPremium: Boolean = false, bonusGems: Int = 0, unlockId: String? = null) {
        val cur = _state.value
        val nextPro = cur.hasProUpgrade || proUpgrade
        val nextPass = cur.hasDeskPassPremium || deskPassPremium
        val nextGems = cur.gemsBalance + bonusGems
        val nextOwned = if (unlockId != null) cur.ownedShopIds + unlockId else cur.ownedShopIds
        saveBalancesWithHmac(cur.coinsBalance, nextGems, cur.deskPassXp, nextPro, nextPass)
        prefs.edit().putStringSet("owned_shop_ids", nextOwned).apply()
        _state.value = cur.copy(
            hasProUpgrade = nextPro,
            hasDeskPassPremium = nextPass,
            gemsBalance = nextGems,
            ownedShopIds = nextOwned
        )
    }

    fun claimDeskPassTier(tier: Int, isPremiumTrack: Boolean, rewardCoins: Int, rewardGems: Int, rewardShopId: String?) {
        val cur = _state.value
        if (tier > cur.deskPassLevel) return
        if (isPremiumTrack && !cur.hasDeskPassPremium) return
        if (!isPremiumTrack && tier in cur.claimedFreePassTiers) return
        if (isPremiumTrack && tier in cur.claimedPremiumPassTiers) return

        val nextCoins = cur.coinsBalance + rewardCoins
        val nextGems = cur.gemsBalance + rewardGems
        val nextOwned = if (rewardShopId != null) cur.ownedShopIds + rewardShopId else cur.ownedShopIds
        val nextFree = if (!isPremiumTrack) cur.claimedFreePassTiers + tier else cur.claimedFreePassTiers
        val nextPrem = if (isPremiumTrack) cur.claimedPremiumPassTiers + tier else cur.claimedPremiumPassTiers

        saveBalancesWithHmac(nextCoins, nextGems, cur.deskPassXp, cur.hasProUpgrade, cur.hasDeskPassPremium)
        prefs.edit()
            .putStringSet("owned_shop_ids", nextOwned)
            .putStringSet("claimed_free_tiers", nextFree.map { it.toString() }.toSet())
            .putStringSet("claimed_prem_tiers", nextPrem.map { it.toString() }.toSet())
            .apply()

        _state.value = cur.copy(
            coinsBalance = nextCoins,
            gemsBalance = nextGems,
            ownedShopIds = nextOwned,
            claimedFreePassTiers = nextFree,
            claimedPremiumPassTiers = nextPrem
        )
    }

    fun updateKartLivery(index: Int) = updateToyCarLivery(index)
    fun updateSwapHands(swap: Boolean) = updateControlsLayout(leftHanded = swap)
    fun updateButtonScale(scale: Float) = updateControlsLayout(scale = scale)
    fun updateHudOpacity(opacity: Float) = updateControlsLayout(opacity = opacity)
    fun updatePostFxEnabled(enabled: Boolean) = updatePostEffects(fxaa = enabled, bloom = enabled)
    fun updateContactShadowsEnabled(enabled: Boolean) = updatePostEffects(fxaa = enabled)
    fun updateShowDebugHudZones(enabled: Boolean) = updateShowHudDebugZones(enabled)

    fun addCoins(amount: Int) {
        if (amount > 0) addCoinsAndGems(amount, 0)
    }

    fun addGems(amount: Int) {
        if (amount > 0) addCoinsAndGems(0, amount)
    }

    fun spendCoins(amount: Int): Boolean = addCoinsAndGems(-amount, 0)

    fun spendGems(amount: Int): Boolean = addCoinsAndGems(0, -amount)

    fun addDeskPassXp(xp: Int) {
        if (xp > 0) addRaceRewards(0, xp)
    }

    fun currentDeskPassTier(): Int = _state.value.deskPassLevel

    fun unlockBody(bodyId: Int) {
        unlockShopItem("body_$bodyId", 0, 0)
    }

    fun unlockCosmetic(id: String) {
        unlockShopItem(id, 0, 0)
    }

    fun grantEntitlement(
        starterPack: Boolean = false,
        proUnlocked: Boolean = false,
        trackPackUnlocked: Boolean = false,
        deskPassPremium: Boolean = false,
        bonusCoins: Int = 0,
        bonusGems: Int = 0
    ) {
        if (bonusCoins > 0) addCoinsAndGems(bonusCoins, 0)
        val unlockKey = when {
            starterPack -> "starter_pack"
            trackPackUnlocked -> "track_pack"
            else -> null
        }
        grantEntitlement(
            proUpgrade = proUnlocked,
            deskPassPremium = deskPassPremium,
            bonusGems = bonusGems,
            unlockId = unlockKey
        )
    }

    fun claimDeskPassTier(tier: Int): Boolean {
        val cur = _state.value
        if (tier > cur.deskPassLevel) return false
        if (tier in cur.claimedFreePassTiers) return false
        claimDeskPassTier(tier, isPremiumTrack = false, rewardCoins = 0, rewardGems = 0, rewardShopId = null)
        return true
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
        _state.value = _state.value.copy(hasSeenHowToPlay = true, completedInteractiveTutorial = true)
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

    fun saveTimeTrialGhost(ghost: StoredGhostLap) {
        try {
            val file = File(context.filesDir, "ghost_track_${ghost.trackId}.bin")
            DataOutputStream(file.outputStream().buffered()).use { out ->
                out.writeInt(0x44524732) // "DRG2"
                out.writeInt(ghost.trackId)
                out.writeLong(ghost.lapTimeMs)
                out.writeInt(ghost.bodyType.id)
                out.writeLong(ghost.colorHex)
                out.writeInt(ghost.samples.size)
                for (s in ghost.samples) {
                    out.writeFloat(s.x)
                    out.writeFloat(s.y)
                    out.writeFloat(s.z)
                    out.writeFloat(s.headingRad)
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
            DataInputStream(file.inputStream().buffered()).use { inp ->
                val magic = inp.readInt()
                if (magic != 0x44524732) return null
                val tid = inp.readInt()
                val lapMs = inp.readLong()
                val body = KartBodyType.fromId(inp.readInt())
                val color = inp.readLong()
                val count = inp.readInt().coerceIn(0, 10_000)
                val list = ArrayList<GhostPoseSample>(count)
                for (i in 0 until count) {
                    list.add(
                        GhostPoseSample(
                            x = inp.readFloat(),
                            y = inp.readFloat(),
                            z = inp.readFloat(),
                            headingRad = inp.readFloat()
                        )
                    )
                }
                StoredGhostLap(tid, lapMs, body, color, list)
            }
        } catch (e: Exception) {
            Log.w("Prefs", "Failed to load ghost for track $trackId: ${e.message}")
            null
        }
    }
}
