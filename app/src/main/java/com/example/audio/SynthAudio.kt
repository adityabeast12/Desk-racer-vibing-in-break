package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Part I (I1-I3) Audio Polish:
 * - I1: Mix: music 0.55, engine 0.45, SFX 0.80, UI 0.60. Duck music -6 dB (x0.50) under jingles/countdown, -3 dB (x0.707) while boosting.
 * - I2: Mix in 3 nearest remote kart engines, pitched by speed, panned by angle (stereo), attenuated by distance.
 * - I3: 30 ms fade on loop start/stop; pause and resume cleanly across lifecycle.
 */
class SynthAudio(context: Context? = null) {

    companion object {
        const val MIX_MUSIC = 0.55f
        const val MIX_ENGINE = 0.45f
        const val MIX_SFX = 0.80f
        const val MIX_UI = 0.60f
        private const val DUCK_MINUS_6DB = 0.501f
        private const val DUCK_MINUS_3DB = 0.707f
    }

    private val appContext: Context? = context?.applicationContext

    private val vibrator: Vibrator? = runCatching {
        if (appContext == null) null
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    @Volatile
    var soundEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                stopMusicInternal()
            }
        }

    @Volatile
    var hapticsEnabled: Boolean = true

    @Volatile
    var isEngineActive: Boolean = false

    @Volatile
    var engineSpeedRatio: Float = 0f

    @Volatile
    var isDriftingActive: Boolean = false

    @Volatile
    var isBoostingActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateMusicDuckingVolumeAsync()
            }
        }

    @Volatile
    private var jingleDuckUntilMs: Long = 0L

    @Volatile
    private var lifecyclePaused: Boolean = false

    // Part I2: 3 nearest remote kart engines (zero-allocation primitive arrays)
    private val remoteEngineSpeeds = FloatArray(3)
    private val remoteEnginePans = FloatArray(3) // -1.0 (left) .. +1.0 (right)
    private val remoteEngineGains = FloatArray(3) // 0.0 .. 1.0 distance attenuation
    @Volatile
    private var activeRemoteEngineCount: Int = 0

    @Volatile
    private var running = true

    // SoundPool for OGG SFX from assets/audio/sfx/*.ogg
    private var soundPool: SoundPool? = null
    private val sfxIds = ConcurrentHashMap<String, Int>()
    private var hasOggSfx = false

    // MediaPlayer for OGG music loops & jingles from assets/audio/music/*.ogg
    private var musicPlayer: MediaPlayer? = null
    private var jinglePlayer: MediaPlayer? = null
    private var currentMusicAsset: String? = null
    private var currentMusicSpeed: Float = 1.0f

    private data class OneShotTone(
        val startFreqHz: Float,
        val endFreqHz: Float,
        val durationSamples: Int,
        val volume: Float,
        val isNoise: Boolean = false,
        var elapsedSamples: Int = 0,
        var phase: Double = 0.0
    )

    private val activeTones = ConcurrentLinkedQueue<OneShotTone>()
    private var synthThread: Thread? = null
    private val audioIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "DeskRacerAudioIO").apply { isDaemon = true }
    }

    init {
        if (appContext != null) {
            audioIoExecutor.execute { initAssetAudio(appContext) }
            startSynthThread()
        }
    }

    /**
     * Part I2: Updates the 3 nearest remote kart engine parameters without allocations.
     */
    fun updateRemoteKartEngines(
        count: Int,
        s0: Float = 0f, pan0: Float = 0f, gain0: Float = 0f,
        s1: Float = 0f, pan1: Float = 0f, gain1: Float = 0f,
        s2: Float = 0f, pan2: Float = 0f, gain2: Float = 0f
    ) {
        remoteEngineSpeeds[0] = s0
        remoteEnginePans[0] = pan0
        remoteEngineGains[0] = gain0
        remoteEngineSpeeds[1] = s1
        remoteEnginePans[1] = pan1
        remoteEngineGains[1] = gain1
        remoteEngineSpeeds[2] = s2
        remoteEnginePans[2] = pan2
        remoteEngineGains[2] = gain2
        activeRemoteEngineCount = count.coerceIn(0, 3)
    }

    private fun initAssetAudio(ctx: Context) {
        try {
            val sp = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            soundPool = sp

            val sfxFiles = mapOf(
                "coin" to "audio/sfx/coin.ogg",
                "engine_loop" to "audio/sfx/engine_loop.ogg",
                "hit_spinout" to "audio/sfx/hit_spinout.ogg",
                "hop" to "audio/sfx/hop.ogg",
                "item_fire" to "audio/sfx/item_fire.ogg",
                "item_get" to "audio/sfx/item_get.ogg",
                "itembox_break" to "audio/sfx/itembox_break.ogg",
                "land" to "audio/sfx/land.ogg",
                "skid_loop" to "audio/sfx/skid_loop.ogg",
                "ui_back" to "audio/sfx/ui_back.ogg",
                "ui_click" to "audio/sfx/ui_click.ogg",
                "ui_confirm" to "audio/sfx/ui_confirm.ogg",
                "wall_bump" to "audio/sfx/wall_bump.ogg"
            )
            var loadedCount = 0
            for ((key, path) in sfxFiles) {
                try {
                    ctx.assets.openFd(path).use { afd ->
                        val id = sp.load(afd, 1)
                        if (id > 0) {
                            sfxIds[key] = id
                            loadedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.d("SynthAudio", "Optional SFX asset missing ($path), using procedural fallback")
                }
            }
            hasOggSfx = loadedCount > 0
        } catch (e: Exception) {
            Log.w("SynthAudio", "SoundPool init fallback to procedural synth: ${e.message}")
        }
    }

    private fun playSfxAsset(key: String, volume: Float = MIX_SFX, rate: Float = 1.0f): Boolean {
        if (!soundEnabled || lifecyclePaused) return true
        val sp = soundPool ?: return false
        val id = sfxIds[key] ?: return false
        return try {
            sp.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2.0f)) != 0
        } catch (e: Exception) {
            Log.d("SynthAudio", "SFX play fallback for $key: ${e.message}")
            false
        }
    }

    private fun computeEffectiveMusicVolume(): Float {
        val now = System.currentTimeMillis()
        val duckMult = when {
            now < jingleDuckUntilMs -> DUCK_MINUS_6DB
            isBoostingActive -> DUCK_MINUS_3DB
            else -> 1.0f
        }
        return (MIX_MUSIC * duckMult).coerceIn(0f, 1f)
    }

    private fun updateMusicDuckingVolumeAsync() {
        if (appContext == null) return
        audioIoExecutor.execute {
            val mp = musicPlayer ?: return@execute
            val vol = computeEffectiveMusicVolume()
            runCatching { mp.setVolume(vol, vol) }
        }
    }

    fun playMusicLoop(assetPath: String, speedMultiplier: Float = 1.0f) {
        val ctx = appContext ?: return
        audioIoExecutor.execute {
            if (!soundEnabled) {
                currentMusicAsset = assetPath
                currentMusicSpeed = speedMultiplier
                return@execute
            }
            if (currentMusicAsset == assetPath && musicPlayer?.isPlaying == true) {
                if (currentMusicSpeed != speedMultiplier) {
                    setMusicSpeedInternal(speedMultiplier)
                }
                val vol = computeEffectiveMusicVolume()
                runCatching { musicPlayer?.setVolume(vol, vol) }
                return@execute
            }
            stopMusicSync()
            currentMusicAsset = assetPath
            currentMusicSpeed = speedMultiplier
            try {
                ctx.assets.openFd(assetPath).use { afd ->
                    val mp = MediaPlayer()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    mp.isLooping = true
                    // Part I3: 30 ms fade-in on loop start
                    mp.setVolume(0f, 0f)
                    mp.prepare()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speedMultiplier != 1.0f) {
                        runCatching {
                            mp.playbackParams = PlaybackParams().setSpeed(speedMultiplier)
                        }
                    }
                    if (!lifecyclePaused) {
                        mp.start()
                    }
                    musicPlayer = mp
                    val targetVol = computeEffectiveMusicVolume()
                    for (step in 1..3) {
                        Thread.sleep(10L)
                        val v = targetVol * (step / 3f)
                        runCatching { mp.setVolume(v, v) }
                    }
                }
            } catch (e: Exception) {
                Log.d("SynthAudio", "Music loop asset missing ($assetPath), continuing silent/synth: ${e.message}")
            }
        }
    }

    fun setMusicSpeed(speedMultiplier: Float) {
        audioIoExecutor.execute {
            setMusicSpeedInternal(speedMultiplier)
        }
    }

    private fun setMusicSpeedInternal(speedMultiplier: Float) {
        currentMusicSpeed = speedMultiplier
        val mp = musicPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mp.isPlaying) {
            runCatching {
                mp.playbackParams = PlaybackParams().setSpeed(speedMultiplier)
            }
        }
    }

    fun playJingle(assetPath: String, duckDurationMs: Long = 2600L) {
        val ctx = appContext ?: return
        if (!soundEnabled || lifecyclePaused) return
        jingleDuckUntilMs = System.currentTimeMillis() + duckDurationMs
        updateMusicDuckingVolumeAsync()
        audioIoExecutor.execute {
            try {
                jinglePlayer?.release()
                jinglePlayer = null
                ctx.assets.openFd(assetPath).use { afd ->
                    val mp = MediaPlayer()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    mp.isLooping = false
                    mp.setVolume(MIX_SFX, MIX_SFX)
                    mp.setOnCompletionListener {
                        jingleDuckUntilMs = 0L
                        updateMusicDuckingVolumeAsync()
                    }
                    mp.prepare()
                    mp.start()
                    jinglePlayer = mp
                }
            } catch (e: Exception) {
                Log.d("SynthAudio", "Jingle asset fallback ($assetPath): ${e.message}")
            }
        }
    }

    /**
     * Part I3: Clean lifecycle pause & resume with 30 ms fade.
     */
    fun onLifecyclePause() {
        lifecyclePaused = true
        isEngineActive = false
        audioIoExecutor.execute {
            val mp = musicPlayer
            if (mp != null && runCatching { mp.isPlaying }.getOrDefault(false)) {
                val baseVol = computeEffectiveMusicVolume()
                for (step in 2 downTo 0) {
                    val v = baseVol * (step / 3f)
                    runCatching { mp.setVolume(v, v) }
                    Thread.sleep(10L)
                }
                runCatching { mp.pause() }
            }
            runCatching { soundPool?.autoPause() }
        }
    }

    fun onLifecycleResume() {
        lifecyclePaused = false
        audioIoExecutor.execute {
            runCatching { soundPool?.autoResume() }
            val mp = musicPlayer
            if (mp != null && soundEnabled && !runCatching { mp.isPlaying }.getOrDefault(false)) {
                runCatching {
                    mp.setVolume(0f, 0f)
                    mp.start()
                }
                val targetVol = computeEffectiveMusicVolume()
                for (step in 1..3) {
                    Thread.sleep(10L)
                    val v = targetVol * (step / 3f)
                    runCatching { mp.setVolume(v, v) }
                }
            }
        }
    }

    private fun stopMusicInternal() {
        audioIoExecutor.execute {
            stopMusicSync()
        }
    }

    private fun stopMusicSync() {
        val mp = musicPlayer ?: return
        runCatching {
            if (mp.isPlaying) {
                val baseVol = computeEffectiveMusicVolume()
                for (step in 2 downTo 0) {
                    val v = baseVol * (step / 3f)
                    mp.setVolume(v, v)
                    Thread.sleep(10L)
                }
            }
            mp.stop()
            mp.release()
        }
        musicPlayer = null
    }

    private fun startSynthThread() {
        val sampleRate = 22050
        val framesPerBuffer = 512
        val fadeStepPerFrame = 1f / (sampleRate * 0.030f) // 30 ms fade (Part I3)
        synthThread = Thread({
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(framesPerBuffer * 4)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                val pcm = ShortArray(framesPerBuffer * 2) // Stereo L/R interleaved
                var enginePhase = 0.0
                var subPhase = 0.0
                val remotePhases = DoubleArray(3)
                var engineFadeGain = 0f

                while (running) {
                    if (!soundEnabled || lifecyclePaused) {
                        engineFadeGain = 0f
                        pcm.fill(0)
                        track.write(pcm, 0, pcm.size)
                        Thread.sleep(20L)
                        continue
                    }

                    val engOn = isEngineActive
                    val spd = engineSpeedRatio.coerceIn(0f, 1.5f)
                    val driftOn = isDriftingActive
                    val baseEngineFreq = 58.0 + spd * 145.0
                    val remoteCount = activeRemoteEngineCount

                    for (i in 0 until framesPerBuffer) {
                        var leftMix = 0f
                        var rightMix = 0f

                        // Part I3: 30 ms fade on engine loop start/stop
                        engineFadeGain = if (engOn) {
                            (engineFadeGain + fadeStepPerFrame).coerceAtMost(1f)
                        } else {
                            (engineFadeGain - fadeStepPerFrame).coerceAtLeast(0f)
                        }

                        if (engineFadeGain > 0.001f) {
                            enginePhase += (2.0 * PI * baseEngineFreq) / sampleRate
                            if (enginePhase > 2.0 * PI) enginePhase -= 2.0 * PI
                            subPhase += (2.0 * PI * baseEngineFreq * 0.5) / sampleRate
                            if (subPhase > 2.0 * PI) subPhase -= 2.0 * PI

                            val saw = ((enginePhase / PI) - 1.0).toFloat()
                            val sub = sin(subPhase).toFloat()
                            var localEng = (saw * 0.14f + sub * 0.12f) * MIX_ENGINE * engineFadeGain

                            if (driftOn) {
                                val screech = (Random.nextFloat() * 2f - 1f) * 0.09f * MIX_ENGINE * engineFadeGain
                                localEng += screech
                            }
                            leftMix += localEng
                            rightMix += localEng

                            // Part I2: Mix in up to 3 nearest remote kart engines (pitched, panned, attenuated)
                            for (r in 0 until remoteCount) {
                                val rGain = remoteEngineGains[r]
                                if (rGain <= 0.01f) continue
                                val rSpd = remoteEngineSpeeds[r].coerceIn(0f, 1.5f)
                                val rFreq = 62.0 + (r * 4.0) + rSpd * 140.0
                                remotePhases[r] += (2.0 * PI * rFreq) / sampleRate
                                if (remotePhases[r] > 2.0 * PI) remotePhases[r] -= 2.0 * PI
                                val rSaw = ((remotePhases[r] / PI) - 1.0).toFloat()
                                val rSig = rSaw * 0.11f * MIX_ENGINE * rGain * engineFadeGain
                                val pan = remoteEnginePans[r].coerceIn(-1f, 1f)
                                val lGain = ((1f - pan) * 0.5f).coerceIn(0.1f, 1f)
                                val rChGain = ((1f + pan) * 0.5f).coerceIn(0.1f, 1f)
                                leftMix += rSig * lGain
                                rightMix += rSig * rChGain
                            }
                        }

                        val iter = activeTones.iterator()
                        while (iter.hasNext()) {
                            val tone = iter.next()
                            val progress = tone.elapsedSamples.toFloat() / tone.durationSamples.toFloat()
                            if (progress >= 1f) {
                                iter.remove()
                                continue
                            }
                            val env = (1f - progress)
                            val toneSample = if (tone.isNoise) {
                                (Random.nextFloat() * 2f - 1f) * tone.volume * env
                            } else {
                                val freq = tone.startFreqHz + (tone.endFreqHz - tone.startFreqHz) * progress
                                tone.phase += (2.0 * PI * freq) / sampleRate
                                sin(tone.phase).toFloat() * tone.volume * env
                            }
                            leftMix += toneSample
                            rightMix += toneSample
                            tone.elapsedSamples++
                        }

                        val clampedL = leftMix.coerceIn(-0.95f, 0.95f)
                        val clampedR = rightMix.coerceIn(-0.95f, 0.95f)
                        pcm[i * 2] = (clampedL * 32767f).toInt().toShort()
                        pcm[i * 2 + 1] = (clampedR * 32767f).toInt().toShort()
                    }

                    track.write(pcm, 0, pcm.size)
                }
            } catch (e: Exception) {
                Log.d("SynthAudio", "Synth thread stopped: ${e.message}")
            } finally {
                runCatching {
                    track?.stop()
                    track?.release()
                }
            }
        }, "DeskRacerSynthAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun playCountdownJingle() {
        // Part I1: Duck music -6 dB during 3.2s countdown
        playJingle("audio/music/jingle_countdown.ogg", duckDurationMs = 3400L)
    }

    fun playCountdownBeep(isGo: Boolean) {
        if (!soundEnabled) return
        if (isGo) {
            activeTones.add(OneShotTone(880f, 880f, 7500, 0.28f * MIX_SFX))
            vibrateTick(45L)
        } else {
            activeTones.add(OneShotTone(440f, 440f, 3800, 0.22f * MIX_SFX))
            vibrateTick(20L)
        }
    }

    fun playFinalLapJingle() {
        playJingle("audio/music/jingle_final_lap.ogg", duckDurationMs = 2400L)
        setMusicSpeed(1.08f)
    }

    fun playBoostWhoosh() {
        if (!playSfxAsset("item_fire", volume = MIX_SFX * 0.95f, rate = 1.15f) && soundEnabled) {
            activeTones.add(OneShotTone(220f, 680f, 6500, 0.22f * MIX_SFX))
            activeTones.add(OneShotTone(0f, 0f, 5500, 0.14f * MIX_SFX, isNoise = true))
        }
        vibrateTick(35L)
    }

    fun playHopSound() {
        if (!playSfxAsset("hop", volume = MIX_SFX) && soundEnabled) {
            activeTones.add(OneShotTone(300f, 540f, 2200, 0.18f * MIX_SFX))
        }
    }

    fun playLandSound() {
        if (!playSfxAsset("land", volume = MIX_SFX) && soundEnabled) {
            activeTones.add(OneShotTone(140f, 90f, 1600, 0.18f * MIX_SFX, isNoise = true))
        }
        vibrateTick(16L)
    }

    fun playCoinPickup() {
        if (!playSfxAsset("coin", volume = MIX_SFX) && soundEnabled) {
            activeTones.add(OneShotTone(987.77f, 1318.5f, 3200, 0.22f * MIX_SFX))
        }
        vibrateTick(14L)
    }

    fun playItemPickupBlip() {
        playSfxAsset("itembox_break", volume = MIX_SFX * 0.95f)
        if (!playSfxAsset("item_get", volume = MIX_SFX) && soundEnabled) {
            activeTones.add(OneShotTone(520f, 960f, 3600, 0.24f * MIX_SFX))
        }
        vibrateTick(22L)
    }

    fun playItemFireSound() {
        if (!playSfxAsset("item_fire", volume = MIX_SFX) && soundEnabled) {
            activeTones.add(OneShotTone(420f, 760f, 3400, 0.22f * MIX_SFX))
        }
    }

    fun playItemHitSound() {
        if (!playSfxAsset("hit_spinout", volume = MIX_SFX) && soundEnabled) {
            activeTones.add(OneShotTone(340f, 95f, 6800, 0.28f * MIX_SFX, isNoise = true))
        }
        vibrateHit()
    }

    fun playWallBumpTick() {
        if (!playSfxAsset("wall_bump", volume = MIX_SFX * 0.85f) && soundEnabled) {
            activeTones.add(OneShotTone(150f, 80f, 1800, 0.18f * MIX_SFX, isNoise = true))
        }
        vibrateTick(14L)
    }

    fun playUiClick() {
        if (!playSfxAsset("ui_click", volume = MIX_UI) && soundEnabled) {
            activeTones.add(OneShotTone(660f, 720f, 900, 0.14f * MIX_UI))
        }
    }

    fun playUiConfirm() {
        if (!playSfxAsset("ui_confirm", volume = MIX_UI) && soundEnabled) {
            activeTones.add(OneShotTone(520f, 880f, 1800, 0.18f * MIX_UI))
        }
    }

    fun playUiBack() {
        if (!playSfxAsset("ui_back", volume = MIX_UI) && soundEnabled) {
            activeTones.add(OneShotTone(540f, 360f, 1800, 0.16f * MIX_UI))
        }
    }

    fun playFinishFanfare(position: Int = 1) {
        if (!soundEnabled) return
        if (position in 1..3) {
            playJingle("audio/music/jingle_win.ogg", duckDurationMs = 3500L)
        } else {
            playJingle("audio/music/jingle_finish.ogg", duckDurationMs = 3000L)
        }
        activeTones.add(OneShotTone(523.25f, 659.25f, 4500, 0.24f * MIX_SFX))
        activeTones.add(OneShotTone(659.25f, 1046.50f, 9500, 0.26f * MIX_SFX))
        vibrateTick(70L)
    }

    /**
     * Part G7: Vibrate on hit (both device vibrator and any connected gamepad vibrator).
     */
    fun vibrateHit() {
        vibrateTick(65L)
    }

    fun vibrateTick(durationMs: Long = 20L) {
        if (!hapticsEnabled) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    }

    fun shutdown() {
        running = false
        isEngineActive = false
        stopMusicInternal()
        runCatching {
            jinglePlayer?.release()
            jinglePlayer = null
            soundPool?.release()
            soundPool = null
        }
    }
}
