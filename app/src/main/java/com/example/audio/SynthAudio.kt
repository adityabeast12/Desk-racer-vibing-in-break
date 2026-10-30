package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SynthAudio(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile
    var soundEnabled: Boolean = true

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

    init {
        startSynthThread()
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

                    val baseEngineFreq = 58.0 + (engineSpeedRatio.coerceIn(0f, 1.5f) * 145.0)
                    val driftActive = isDriftingActive && isEngineActive
                    val engineOn = isEngineActive

                    for (i in 0 until bufferSamples) {
                        var sampleVal = 0f

                        // 1. Continuous Kart Engine Hum (Sawtooth + Sub-harmonic)
                        if (engineOn) {
                            enginePhase += baseEngineFreq / sampleRate
                            if (enginePhase >= 1.0) enginePhase -= 1.0
                            subPhase += (baseEngineFreq * 0.5) / sampleRate
                            if (subPhase >= 1.0) subPhase -= 1.0

                            val saw = (enginePhase.toFloat() * 2f - 1f) * 0.07f
                            val sub = sin(subPhase * 2.0 * PI).toFloat() * 0.06f
                            sampleVal += (saw + sub)

                            if (driftActive) {
                                // Filtered tire screech noise
                                val screech = (Random.nextFloat() * 2f - 1f) * 0.055f *
                                    sin(enginePhase * 8.0 * PI).toFloat()
                                sampleVal += screech
                            }
                        }

                        // 2. Active One-Shot Effects
                        val iter = activeTones.iterator()
                        while (iter.hasNext()) {
                            val t = iter.next()
                            val progress = t.elapsedSamples.toFloat() / t.durationSamples.coerceAtLeast(1)
                            if (progress >= 1f) {
                                iter.remove()
                                continue
                            }
                            val env = (1f - progress)
                            val freq = t.startFreqHz + (t.endFreqHz - t.startFreqHz) * progress
                            t.phase += freq / sampleRate
                            val osc = if (t.isNoise) {
                                (Random.nextFloat() * 2f - 1f) * 0.6f + sin(t.phase * 2.0 * PI).toFloat() * 0.4f
                            } else {
                                sin(t.phase * 2.0 * PI).toFloat()
                            }
                            sampleVal += osc * t.volume * env
                            t.elapsedSamples++
                        }

                        val clamped = sampleVal.coerceIn(-0.95f, 0.95f)
                        pcm[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                    }

                    track.write(pcm, 0, pcm.size)
                }
            } catch (_: Throwable) {
                // Safe fallback in unit test / headless environments without audio hardware
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }, "DeskRacerSynthAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun playCountdownBeep(isGo: Boolean) {
        if (!soundEnabled) return
        if (isGo) {
            activeTones.add(OneShotTone(880f, 1180f, 6500, 0.28f))
            vibrateTick(45L)
        } else {
            activeTones.add(OneShotTone(440f, 440f, 3800, 0.22f))
            vibrateTick(20L)
        }
    }

    fun playBoostWhoosh() {
        if (soundEnabled) {
            activeTones.add(OneShotTone(220f, 760f, 7500, 0.25f, isNoise = true))
        }
        vibrateTick(35L)
    }

    fun playItemPickupBlip() {
        if (soundEnabled) {
            activeTones.add(OneShotTone(520f, 1040f, 3500, 0.22f))
        }
        vibrateTick(18L)
    }

    fun playItemHitSound() {
        if (soundEnabled) {
            activeTones.add(OneShotTone(340f, 95f, 6800, 0.28f, isNoise = true))
        }
        vibrateTick(60L)
    }

    fun playWallBumpTick() {
        if (soundEnabled) {
            activeTones.add(OneShotTone(150f, 80f, 1800, 0.18f, isNoise = true))
        }
        vibrateTick(14L)
    }

    fun playFinishFanfare() {
        if (!soundEnabled) return
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
    }
}
