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

class SynthAudio(context: Context? = null) {

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
    private var running = true

    // SoundPool for OGG SFX from assets/audio/sfx/*.ogg (Part A6 & I)
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

    private fun playSfxAsset(key: String, volume: Float = 0.85f, rate: Float = 1.0f): Boolean {
        if (!soundEnabled) return true
        val sp = soundPool ?: return false
        val id = sfxIds[key] ?: return false
        return try {
            sp.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2.0f)) != 0
        } catch (e: Exception) {
            Log.d("SynthAudio", "SFX play fallback for $key: ${e.message}")
            false
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
                    mp.setVolume(0.46f, 0.46f)
                    mp.prepare()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speedMultiplier != 1.0f) {
                        runCatching {
                            mp.playbackParams = PlaybackParams().setSpeed(speedMultiplier)
                        }
                    }
                    mp.start()
                    musicPlayer = mp
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

    fun playJingle(assetPath: String) {
        val ctx = appContext ?: return
        if (!soundEnabled) return
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
                    mp.setVolume(0.78f, 0.78f)
                    mp.prepare()
                    mp.start()
                    jinglePlayer = mp
                }
            } catch (e: Exception) {
                Log.d("SynthAudio", "Jingle asset fallback ($assetPath): ${e.message}")
            }
        }
    }

    private fun stopMusicInternal() {
        audioIoExecutor.execute {
            stopMusicSync()
        }
    }

    private fun stopMusicSync() {
        runCatching {
            musicPlayer?.stop()
            musicPlayer?.release()
        }
        musicPlayer = null
    }

    private fun startSynthThread() {
        val sampleRate = 22050
        val bufferSamples = 512
        synthThread = Thread({
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(bufferSamples * 2)

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
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                val pcm = ShortArray(bufferSamples)
                var enginePhase = 0.0
                var subPhase = 0.0

                while (running) {
                    if (!soundEnabled) {
                        pcm.fill(0)
                        track.write(pcm, 0, pcm.size)
                        Thread.sleep(20L)
                        continue
                    }

                    val engOn = isEngineActive
                    val spd = engineSpeedRatio.coerceIn(0f, 1.5f)
                    val driftOn = isDriftingActive
                    val baseEngineFreq = 58.0 + spd * 145.0

                    for (i in 0 until bufferSamples) {
                        var sampleMix = 0f

                        if (engOn) {
                            enginePhase += (2.0 * PI * baseEngineFreq) / sampleRate
                            if (enginePhase > 2.0 * PI) enginePhase -= 2.0 * PI
                            subPhase += (2.0 * PI * baseEngineFreq * 0.5) / sampleRate
                            if (subPhase > 2.0 * PI) subPhase -= 2.0 * PI

                            val saw = ((enginePhase / PI) - 1.0).toFloat()
                            val sub = sin(subPhase).toFloat()
                            sampleMix += (saw * 0.07f + sub * 0.06f)

                            if (driftOn) {
                                val screech = (Random.nextFloat() * 2f - 1f) * 0.05f
                                sampleMix += screech
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
                            if (tone.isNoise) {
                                sampleMix += (Random.nextFloat() * 2f - 1f) * tone.volume * env
                            } else {
                                val freq = tone.startFreqHz + (tone.endFreqHz - tone.startFreqHz) * progress
                                tone.phase += (2.0 * PI * freq) / sampleRate
                                sampleMix += sin(tone.phase).toFloat() * tone.volume * env
                            }
                            tone.elapsedSamples++
                        }

                        val clamped = sampleMix.coerceIn(-0.95f, 0.95f)
                        pcm[i] = (clamped * 32767f).toInt().toShort()
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
        playJingle("audio/music/jingle_countdown.ogg")
    }

    fun playCountdownBeep(isGo: Boolean) {
        if (!soundEnabled) return
        if (isGo) {
            activeTones.add(OneShotTone(880f, 880f, 7500, 0.28f))
            vibrateTick(45L)
        } else {
            activeTones.add(OneShotTone(440f, 440f, 3800, 0.22f))
            vibrateTick(20L)
        }
    }

    fun playFinalLapJingle() {
        playJingle("audio/music/jingle_final_lap.ogg")
        // Part D3: Final lap speeds up background music loop by x1.08
        setMusicSpeed(1.08f)
    }

    fun playBoostWhoosh() {
        if (!playSfxAsset("item_fire", volume = 0.75f, rate = 1.15f) && soundEnabled) {
            activeTones.add(OneShotTone(220f, 680f, 6500, 0.22f))
            activeTones.add(OneShotTone(0f, 0f, 5500, 0.14f, isNoise = true))
        }
        vibrateTick(35L)
    }

    fun playHopSound() {
        if (!playSfxAsset("hop", volume = 0.80f) && soundEnabled) {
            activeTones.add(OneShotTone(300f, 540f, 2200, 0.18f))
        }
    }

    fun playLandSound() {
        if (!playSfxAsset("land", volume = 0.80f) && soundEnabled) {
            activeTones.add(OneShotTone(140f, 90f, 1600, 0.18f, isNoise = true))
        }
        vibrateTick(16L)
    }

    fun playCoinPickup() {
        if (!playSfxAsset("coin", volume = 0.85f) && soundEnabled) {
            activeTones.add(OneShotTone(987.77f, 1318.5f, 3200, 0.22f))
        }
        vibrateTick(14L)
    }

    fun playItemPickupBlip() {
        playSfxAsset("itembox_break", volume = 0.80f)
        if (!playSfxAsset("item_get", volume = 0.85f) && soundEnabled) {
            activeTones.add(OneShotTone(520f, 960f, 3600, 0.24f))
        }
        vibrateTick(22L)
    }

    fun playItemFireSound() {
        if (!playSfxAsset("item_fire", volume = 0.85f) && soundEnabled) {
            activeTones.add(OneShotTone(420f, 760f, 3400, 0.22f))
        }
    }

    fun playItemHitSound() {
        if (!playSfxAsset("hit_spinout", volume = 0.90f) && soundEnabled) {
            activeTones.add(OneShotTone(340f, 95f, 6800, 0.28f, isNoise = true))
        }
        vibrateTick(60L)
    }

    fun playWallBumpTick() {
        if (!playSfxAsset("wall_bump", volume = 0.70f) && soundEnabled) {
            activeTones.add(OneShotTone(150f, 80f, 1800, 0.18f, isNoise = true))
        }
        vibrateTick(14L)
    }

    fun playUiClick() {
        if (!playSfxAsset("ui_click", volume = 0.75f) && soundEnabled) {
            activeTones.add(OneShotTone(660f, 720f, 900, 0.14f))
        }
    }

    fun playUiConfirm() {
        if (!playSfxAsset("ui_confirm", volume = 0.80f) && soundEnabled) {
            activeTones.add(OneShotTone(520f, 880f, 1800, 0.18f))
        }
    }

    fun playUiBack() {
        if (!playSfxAsset("ui_back", volume = 0.75f) && soundEnabled) {
            activeTones.add(OneShotTone(540f, 360f, 1800, 0.16f))
        }
    }

    fun playFinishFanfare(position: Int = 1) {
        if (!soundEnabled) return
        if (position in 1..3) {
            playJingle("audio/music/jingle_win.ogg")
        } else {
            playJingle("audio/music/jingle_finish.ogg")
        }
        activeTones.add(OneShotTone(523.25f, 659.25f, 4500, 0.24f))
        activeTones.add(OneShotTone(659.25f, 1046.50f, 9500, 0.26f))
        vibrateTick(70L)
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
