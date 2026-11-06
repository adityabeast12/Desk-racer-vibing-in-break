package drshim

import kotlinx.cinterop.convert
import platform.Foundation.NSDate
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSThread
import platform.Foundation.timeIntervalSince1970
import kotlin.math.pow
import kotlin.time.TimeSource

/** No-op stand-in for kotlin.jvm.Synchronized: on iOS the sim, renderer and UI share the main thread. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class SynchronizedNoop

val globalLock = NSRecursiveLock()

inline fun <R> globalSynchronized(block: () -> R): R {
    globalLock.lock()
    try {
        return block()
    } finally {
        globalLock.unlock()
    }
}

object JSystem {
    private val origin = TimeSource.Monotonic.markNow()
    fun nanoTime(): Long = origin.elapsedNow().inWholeNanoseconds + 1_000_000_000L
    fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    fun gc() {
        runCatching { kotlin.native.runtime.GC.collect() }
    }
    fun arraycopy(src: Any, srcPos: Int, dest: Any, destPos: Int, length: Int) {
        when (src) {
            is ByteArray -> src.copyInto(dest as ByteArray, destPos, srcPos, srcPos + length)
            is FloatArray -> src.copyInto(dest as FloatArray, destPos, srcPos, srcPos + length)
            is IntArray -> src.copyInto(dest as IntArray, destPos, srcPos, srcPos + length)
            is ShortArray -> src.copyInto(dest as ShortArray, destPos, srcPos, srcPos + length)
            is LongArray -> src.copyInto(dest as LongArray, destPos, srcPos, srcPos + length)
            is DoubleArray -> src.copyInto(dest as DoubleArray, destPos, srcPos, srcPos + length)
            is CharArray -> src.copyInto(dest as CharArray, destPos, srcPos, srcPos + length)
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                (src as Array<Any?>).copyInto(dest as Array<Any?>, destPos, srcPos, srcPos + length)
            }
            else -> throw IllegalArgumentException("arraycopy: unsupported type")
        }
    }
    fun getProperty(key: String): String? = null
    fun lineSeparator(): String = "\n"
}

object JMath {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E
    fun toRadians(deg: Double): Double = deg / 180.0 * kotlin.math.PI
    fun toDegrees(rad: Double): Double = rad * 180.0 / kotlin.math.PI
    fun abs(v: Float): Float = kotlin.math.abs(v)
    fun abs(v: Double): Double = kotlin.math.abs(v)
    fun abs(v: Int): Int = kotlin.math.abs(v)
    fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
    fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    fun sqrt(v: Double): Double = kotlin.math.sqrt(v)
    fun sin(v: Double): Double = kotlin.math.sin(v)
    fun cos(v: Double): Double = kotlin.math.cos(v)
    fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)
    fun pow(a: Double, b: Double): Double = a.pow(b)
    fun floor(v: Double): Double = kotlin.math.floor(v)
    fun ceil(v: Double): Double = kotlin.math.ceil(v)
    fun round(v: Float): Int = kotlin.math.floor(v + 0.5f).toInt()
    fun round(v: Double): Long = kotlin.math.floor(v + 0.5).toLong()
    fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b
    fun hypot(a: Double, b: Double): Double = kotlin.math.hypot(a, b)
    fun signum(v: Float): Float = kotlin.math.sign(v)
    fun random(): Double = kotlin.random.Random.nextDouble()
}

fun interface JRunnable {
    fun run()
}

class InterruptedExceptionShim(message: String? = null) : Exception(message)
class OutOfMemoryErrorShim(message: String? = null) : Error(message)

class JThread(private val target: JRunnable?, var name: String) {
    constructor(target: JRunnable) : this(target, "Thread")
    constructor() : this(null, "Thread")

    var isDaemon: Boolean = false
    var priority: Int = 5
    @kotlin.concurrent.Volatile private var started = false
    @kotlin.concurrent.Volatile private var finished = false
    @kotlin.concurrent.Volatile private var interruptedFlag = false

    fun start() {
        if (started) return
        started = true
        val t = NSThread {
            try {
                target?.run()
            } catch (t: Throwable) {
                println("drshim: thread '$name' died: $t")
            } finally {
                finished = true
            }
        }
        t.name = name
        t.start()
    }

    fun interrupt() { interruptedFlag = true }
    fun isInterrupted(): Boolean = interruptedFlag
    val isAlive: Boolean get() = started && !finished

    fun join(millis: Long = 0L) {
        val deadline = if (millis > 0) JSystem.currentTimeMillis() + millis else Long.MAX_VALUE
        while (isAlive && JSystem.currentTimeMillis() < deadline) {
            NSThread.sleepForTimeInterval(0.005)
        }
    }

    companion object {
        fun sleep(millis: Long) {
            if (millis > 0) NSThread.sleepForTimeInterval(millis / 1000.0)
        }
        fun sleep(millis: Long, nanos: Int) {
            val s = millis / 1000.0 + nanos / 1e9
            if (s > 0) NSThread.sleepForTimeInterval(s)
        }
        fun yield() { NSThread.sleepForTimeInterval(0.0) }
    }
}

object JCharsets {
    val UTF_8: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8
    val US_ASCII: java.nio.charset.Charset = java.nio.charset.StandardCharsets.US_ASCII
    val ISO_8859_1: java.nio.charset.Charset = java.nio.charset.StandardCharsets.ISO_8859_1
    val UTF_16: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_16
}

fun decodeBytes(bytes: ByteArray, offset: Int, length: Int, charset: java.nio.charset.Charset): String {
    return if (charset.isSingleByte) {
        val sb = StringBuilder(length)
        for (i in offset until offset + length) sb.append((bytes[i].toInt() and 0xFF).toChar())
        sb.toString()
    } else {
        bytes.decodeToString(offset, offset + length)
    }
}

fun encodeString(s: String, charset: java.nio.charset.Charset): ByteArray {
    return if (charset.isSingleByte) ByteArray(s.length) { s[it].code.coerceAtMost(255).toByte() } else s.encodeToByteArray()
}

/** Unified-log line (visible in Console.app / `log show`), unlike stdout. */
fun nslog(msg: String) {
    // no varargs: Kotlin -> ObjC variadic %@ isn't safe; escape % so the message is the format itself
    platform.Foundation.NSLog("DR " + msg.replace("%", "%%"))
}

/** Resources table installed at startup by the generated R.kt. */
object Res {
    var strings: Map<Int, String> = emptyMap()
    var fonts: Map<Int, String> = emptyMap()
}

@Suppress("unused")
private fun keepConvert(x: Int): UInt = x.convert()
