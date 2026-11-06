@file:Suppress("unused")

package android.media

import java.io.FileDescriptor
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioPlayerNode
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(u: Int): Builder = this
        fun setContentType(c: Int): Builder = this
        fun setFlags(f: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }
    companion object {
        const val USAGE_GAME = 14
        const val USAGE_MEDIA = 1
        const val CONTENT_TYPE_SONIFICATION = 4
        const val CONTENT_TYPE_MUSIC = 2
    }
}

class AudioFormat private constructor(val sampleRate: Int, val channelMask: Int, val encoding: Int) {
    class Builder {
        private var rate = 44100
        private var mask = CHANNEL_OUT_STEREO
        private var enc = ENCODING_PCM_16BIT
        fun setSampleRate(r: Int): Builder { rate = r; return this }
        fun setChannelMask(m: Int): Builder { mask = m; return this }
        fun setEncoding(e: Int): Builder { enc = e; return this }
        fun build(): AudioFormat = AudioFormat(rate, mask, enc)
    }
    val channelCount: Int get() = if (channelMask == CHANNEL_OUT_MONO) 1 else 2
    companion object {
        const val ENCODING_PCM_16BIT = 2
        const val ENCODING_PCM_FLOAT = 4
        const val CHANNEL_OUT_MONO = 4
        const val CHANNEL_OUT_STEREO = 12
    }
}

/**
 * Streaming PCM sink on AVAudioEngine. write() blocks while [MAX_IN_FLIGHT] buffers are queued,
 * which paces the synth thread exactly like a blocking Android AudioTrack.
 */
class AudioTrack private constructor(private val sampleRate: Int, private val channels: Int) {
    private val engine = AVAudioEngine()
    private val node = AVAudioPlayerNode()
    private val format: AVAudioFormat? = AVAudioFormat(AVAudioPCMFormatFloat32, sampleRate.toDouble(), channels.toUInt(), false)
    private val slots = dispatch_semaphore_create(MAX_IN_FLIGHT.toLong())
    private var ok = false
    private var volume = 1f
    var playState: Int = PLAYSTATE_STOPPED
        private set

    init {
        drshim.AudioSession.ensureActive()
        ok = runCatching {
            engine.attachNode(node)
            engine.connect(node, engine.mainMixerNode, format)
            engine.prepare()
            true
        }.getOrDefault(false)
    }

    fun play() {
        if (!ok) return
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            engine.startAndReturnError(err.ptr)
        }
        node.play()
        playState = PLAYSTATE_PLAYING
    }

    fun write(data: ShortArray, offset: Int, size: Int): Int {
        if (!ok || format == null || playState != PLAYSTATE_PLAYING) {
            drshim.JThread.sleep((size / channels * 1000L / sampleRate).coerceAtLeast(1))
            return size
        }
        val frames = size / channels
        val buf = AVAudioPCMBuffer(format, frames.toUInt()) ?: return 0
        buf.frameLength = frames.toUInt()
        val chans = buf.floatChannelData ?: return 0
        val inv = volume / 32768f
        for (c in 0 until channels) {
            val p = chans[c] ?: continue
            var src = offset + c
            for (f in 0 until frames) {
                p[f] = data[src] * inv
                src += channels
            }
        }
        dispatch_semaphore_wait(slots, DISPATCH_TIME_FOREVER)
        node.scheduleBuffer(buf) { dispatch_semaphore_signal(slots) }
        return size
    }

    fun write(data: FloatArray, offset: Int, size: Int, mode: Int): Int {
        val s = ShortArray(size) { (data[offset + it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        return write(s, 0, size)
    }

    fun setVolume(v: Float): Int { volume = v.coerceIn(0f, 1f); return 0 }
    fun pause() { node.pause(); playState = PLAYSTATE_PAUSED }
    fun stop() { node.stop(); playState = PLAYSTATE_STOPPED }
    fun flush() {}
    fun release() {
        runCatching { node.stop(); engine.stop() }
        playState = PLAYSTATE_STOPPED
    }

    class Builder {
        private var fmt: AudioFormat = AudioFormat.Builder().build()
        fun setAudioAttributes(a: AudioAttributes): Builder = this
        fun setAudioFormat(f: AudioFormat): Builder { fmt = f; return this }
        fun setBufferSizeInBytes(n: Int): Builder = this
        fun setTransferMode(m: Int): Builder = this
        fun setPerformanceMode(m: Int): Builder = this
        fun build(): AudioTrack = AudioTrack(fmt.sampleRate, fmt.channelCount)
    }

    companion object {
        const val MODE_STREAM = 1
        const val MODE_STATIC = 0
        const val PLAYSTATE_STOPPED = 1
        const val PLAYSTATE_PAUSED = 2
        const val PLAYSTATE_PLAYING = 3
        const val WRITE_BLOCKING = 0
        const val PERFORMANCE_MODE_LOW_LATENCY = 1
        private const val MAX_IN_FLIGHT = 3
        fun getMinBufferSize(sampleRate: Int, channelConfig: Int, audioFormat: Int): Int = 4096
    }
}

class PlaybackParams {
    var speed: Float = 1f
        private set
    fun setSpeed(s: Float): PlaybackParams { speed = s; return this }
    fun setPitch(p: Float): PlaybackParams = this
}

private fun loadPlayer(path: String): AVAudioPlayer? {
    val real = drshim.Fs.audioAlternative(path) ?: return null
    val url = NSURL.fileURLWithPath(real)
    return memScoped {
        val err = alloc<ObjCObjectVar<NSError?>>()
        AVAudioPlayer(contentsOfURL = url, error = err.ptr)
    }?.also {
        it.enableRate = true
        it.prepareToPlay()
    }
}

class MediaPlayer {
    private var player: AVAudioPlayer? = null
    private var path: String? = null
    private var completion: ((MediaPlayer) -> Unit)? = null
    private var looping = false
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            completion?.invoke(this@MediaPlayer)
        }
    }

    fun interface OnCompletionListener {
        fun onCompletion(mp: MediaPlayer)
    }

    fun setAudioAttributes(a: AudioAttributes) {}
    fun setDataSource(fd: FileDescriptor, offset: Long, length: Long) { path = fd.path }
    fun setDataSource(fd: FileDescriptor) { path = fd.path }
    fun setDataSource(p: String) { path = p }
    fun prepare() {
        val p = path ?: throw IllegalStateException("no data source")
        player = loadPlayer(p) ?: throw java.io.IOException("cannot decode $p")
        player?.delegate = delegate
        player?.numberOfLoops = if (looping) -1L else 0L
    }
    fun prepareAsync() = prepare()
    fun start() {
        drshim.AudioSession.ensureActive()
        player?.play()
    }
    fun pause() { player?.pause() }
    fun stop() { player?.stop(); player?.currentTime = 0.0 }
    fun release() { player?.stop(); player = null }
    fun reset() { release(); path = null }
    fun seekTo(ms: Int) { player?.currentTime = ms / 1000.0 }

    val isPlaying: Boolean get() = player?.playing == true
    var isLooping: Boolean
        get() = looping
        set(v) {
            looping = v
            player?.numberOfLoops = if (v) -1L else 0L
        }
    val duration: Int get() = ((player?.duration ?: 0.0) * 1000).toInt()
    val currentPosition: Int get() = ((player?.currentTime ?: 0.0) * 1000).toInt()

    fun setVolume(left: Float, right: Float) { player?.volume = ((left + right) * 0.5f).coerceIn(0f, 1f) }

    var playbackParams: PlaybackParams = PlaybackParams()
        set(v) {
            field = v
            player?.rate = v.speed.coerceIn(0.5f, 2f)
        }

    fun setOnCompletionListener(l: OnCompletionListener?) {
        completion = l?.let { lis -> { mp: MediaPlayer -> lis.onCompletion(mp) } }
    }
}

/** SoundPool on a few AVAudioPlayers per sample so overlapping one-shots don't cut each other off. */
class SoundPool private constructor(private val maxStreams: Int) {
    private class Sample(val players: List<AVAudioPlayer>) {
        var next = 0
    }
    private val samples = HashMap<Int, Sample>()
    private var nextId = 1
    private var paused = false

    class Builder {
        private var max = 8
        fun setMaxStreams(n: Int): Builder { max = n; return this }
        fun setAudioAttributes(a: AudioAttributes): Builder = this
        fun build(): SoundPool = SoundPool(max)
    }

    fun load(afd: android.content.res.AssetFileDescriptor, priority: Int): Int = load(afd.fileDescriptor.path)
    fun load(path: String, priority: Int): Int = load(path)

    private fun load(path: String): Int {
        val players = (0 until 3).mapNotNull { loadPlayer(path) }
        if (players.isEmpty()) return 0
        val id = nextId++
        drshim.globalSynchronized { samples[id] = Sample(players) }
        return id
    }

    fun play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float): Int {
        if (paused) return 0
        val s = drshim.globalSynchronized { samples[soundId] } ?: return 0
        val p = s.players[s.next % s.players.size]
        s.next++
        drshim.AudioSession.ensureActive()
        p.currentTime = 0.0
        p.volume = ((leftVolume + rightVolume) * 0.5f).coerceIn(0f, 1f)
        p.rate = rate.coerceIn(0.5f, 2f)
        p.numberOfLoops = if (loop < 0) -1L else loop.toLong()
        p.play()
        return soundId * 100 + (s.next % 100)
    }

    fun stop(streamId: Int) {
        val s = drshim.globalSynchronized { samples[streamId / 100] } ?: return
        s.players.forEach { it.stop() }
    }
    fun setVolume(streamId: Int, l: Float, r: Float) {}
    fun setRate(streamId: Int, rate: Float) {}
    fun autoPause() {
        paused = true
        drshim.globalSynchronized { samples.values.toList() }.forEach { s -> s.players.forEach { it.pause() } }
    }
    fun autoResume() { paused = false }
    fun release() {
        drshim.globalSynchronized { samples.values.toList().also { samples.clear() } }.forEach { s -> s.players.forEach { it.stop() } }
    }
}
