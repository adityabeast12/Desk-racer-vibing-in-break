@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package java.nio

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import platform.posix.memset
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

class BufferUnderflowException : RuntimeException("buffer underflow")
class BufferOverflowException : RuntimeException("buffer overflow")
class ReadOnlyBufferException : UnsupportedOperationException("read-only buffer")

class ByteOrder private constructor(private val n: String) {
    override fun toString(): String = n
    companion object {
        val BIG_ENDIAN = ByteOrder("BIG_ENDIAN")
        val LITTLE_ENDIAN = ByteOrder("LITTLE_ENDIAN")
        fun nativeOrder(): ByteOrder = LITTLE_ENDIAN
    }
}

/** Bytes behind a buffer: a Kotlin array (allocate/wrap) or native memory (allocateDirect, visible to GL). */
class Storage private constructor(val size: Int, val heap: ByteArray?, val ptr: CPointer<ByteVar>?) {
    private val cleaner: Cleaner? = ptr?.let { p -> createCleaner(p) { nativeHeap.free(it.rawValue) } }

    fun getB(i: Int): Byte = if (heap != null) heap[i] else ptr!![i]
    fun setB(i: Int, b: Byte) {
        if (heap != null) heap[i] = b else ptr!![i] = b
    }

    fun address(byteOffset: Int): CPointer<ByteVar>? = ptr?.plus(byteOffset)

    companion object {
        fun heap(arr: ByteArray) = Storage(arr.size, arr, null)
        fun direct(n: Int): Storage {
            val p = nativeHeap.allocArray<ByteVar>(if (n < 1) 1 else n)
            memset(p, 0, n.convert())
            return Storage(n, null, p)
        }
    }
}

internal fun rdInt(s: Storage, at: Int, big: Boolean): Int {
    val b0 = s.getB(at).toInt() and 0xFF
    val b1 = s.getB(at + 1).toInt() and 0xFF
    val b2 = s.getB(at + 2).toInt() and 0xFF
    val b3 = s.getB(at + 3).toInt() and 0xFF
    return if (big) (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3 else (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
}

internal fun wrInt(s: Storage, at: Int, v: Int, big: Boolean) {
    if (big) {
        s.setB(at, (v ushr 24).toByte()); s.setB(at + 1, (v ushr 16).toByte()); s.setB(at + 2, (v ushr 8).toByte()); s.setB(at + 3, v.toByte())
    } else {
        s.setB(at, v.toByte()); s.setB(at + 1, (v ushr 8).toByte()); s.setB(at + 2, (v ushr 16).toByte()); s.setB(at + 3, (v ushr 24).toByte())
    }
}

internal fun rdShort(s: Storage, at: Int, big: Boolean): Short {
    val b0 = s.getB(at).toInt() and 0xFF
    val b1 = s.getB(at + 1).toInt() and 0xFF
    return (if (big) (b0 shl 8) or b1 else (b1 shl 8) or b0).toShort()
}

internal fun wrShort(s: Storage, at: Int, v: Short, big: Boolean) {
    val x = v.toInt()
    if (big) { s.setB(at, (x ushr 8).toByte()); s.setB(at + 1, x.toByte()) } else { s.setB(at, x.toByte()); s.setB(at + 1, (x ushr 8).toByte()) }
}

internal fun rdLong(s: Storage, at: Int, big: Boolean): Long {
    val a = rdInt(s, at, big).toLong() and 0xFFFFFFFFL
    val b = rdInt(s, at + 4, big).toLong() and 0xFFFFFFFFL
    return if (big) (a shl 32) or b else (b shl 32) or a
}

internal fun wrLong(s: Storage, at: Int, v: Long, big: Boolean) {
    val hi = (v ushr 32).toInt()
    val lo = v.toInt()
    if (big) { wrInt(s, at, hi, true); wrInt(s, at + 4, lo, true) } else { wrInt(s, at, lo, false); wrInt(s, at + 4, hi, false) }
}

abstract class Buffer internal constructor(internal val storage: Storage, internal val base: Int, capacity: Int) {
    internal var cap: Int = capacity
    internal var pos: Int = 0
    internal var lim: Int = capacity
    internal var mrk: Int = -1
    internal abstract val elemSize: Int

    fun capacity(): Int = cap
    fun position(): Int = pos
    open fun position(newPosition: Int): Buffer {
        if (newPosition < 0 || newPosition > lim) throw IllegalArgumentException("position $newPosition > limit $lim")
        pos = newPosition
        if (mrk > pos) mrk = -1
        return this
    }
    fun limit(): Int = lim
    open fun limit(newLimit: Int): Buffer {
        if (newLimit < 0 || newLimit > cap) throw IllegalArgumentException("limit $newLimit")
        lim = newLimit
        if (pos > lim) pos = lim
        if (mrk > lim) mrk = -1
        return this
    }
    open fun mark(): Buffer { mrk = pos; return this }
    open fun reset(): Buffer {
        if (mrk < 0) throw IllegalStateException("mark not set")
        pos = mrk
        return this
    }
    open fun clear(): Buffer { pos = 0; lim = cap; mrk = -1; return this }
    open fun flip(): Buffer { lim = pos; pos = 0; mrk = -1; return this }
    open fun rewind(): Buffer { pos = 0; mrk = -1; return this }
    fun remaining(): Int = lim - pos
    fun hasRemaining(): Boolean = pos < lim
    val isDirect: Boolean get() = storage.ptr != null
    fun hasArray(): Boolean = storage.heap != null
    val isReadOnly: Boolean get() = false

    /** Native address of the element at the current position, or null for heap buffers. */
    fun nativeAddress(): CPointer<ByteVar>? = storage.address(base + pos * elemSize)

    /** Runs [block] with a pointer to the current position, pinning heap storage for the call. */
    fun <R> withAddress(block: (CPointer<ByteVar>) -> R): R {
        val direct = nativeAddress()
        if (direct != null) return block(direct)
        val arr = storage.heap!!
        return arr.usePinned { pinned -> block(pinned.addressOf(base + pos * elemSize)) }
    }

    internal fun advanceGet(n: Int): Int {
        if (lim - pos < n) throw BufferUnderflowException()
        val p = pos
        pos += n
        return p
    }
    internal fun advancePut(n: Int): Int {
        if (lim - pos < n) throw BufferOverflowException()
        val p = pos
        pos += n
        return p
    }
    internal fun check(index: Int, n: Int) {
        if (index < 0 || index + n > lim) throw IndexOutOfBoundsException("index $index (limit $lim)")
    }
}

class ByteBuffer internal constructor(storage: Storage, base: Int, capacity: Int) : Buffer(storage, base, capacity) {
    override val elemSize: Int get() = 1
    private var big = true

    fun order(): ByteOrder = if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    fun order(bo: ByteOrder): ByteBuffer { big = bo === ByteOrder.BIG_ENDIAN; return this }

    private fun at(i: Int) = base + i

    fun get(): Byte = storage.getB(at(advanceGet(1)))
    fun get(index: Int): Byte { check(index, 1); return storage.getB(at(index)) }
    fun get(dst: ByteArray): ByteBuffer = get(dst, 0, dst.size)
    fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        val p = advanceGet(length)
        val h = storage.heap
        if (h != null) h.copyInto(dst, offset, at(p), at(p) + length)
        else if (length > 0) dst.usePinned { memcpy(it.addressOf(offset), storage.address(at(p)), length.convert()) }
        return this
    }

    fun put(b: Byte): ByteBuffer { storage.setB(at(advancePut(1)), b); return this }
    fun put(index: Int, b: Byte): ByteBuffer { check(index, 1); storage.setB(at(index), b); return this }
    fun put(src: ByteArray): ByteBuffer = put(src, 0, src.size)
    fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        val p = advancePut(length)
        val h = storage.heap
        if (h != null) src.copyInto(h, at(p), offset, offset + length)
        else if (length > 0) src.usePinned { memcpy(storage.address(at(p)), it.addressOf(offset), length.convert()) }
        return this
    }
    fun put(src: ByteBuffer): ByteBuffer {
        val n = src.remaining()
        val tmp = ByteArray(n)
        src.get(tmp)
        return put(tmp)
    }

    fun getShort(): Short = rdShort(storage, at(advanceGet(2)), big)
    fun getShort(index: Int): Short { check(index, 2); return rdShort(storage, at(index), big) }
    fun putShort(v: Short): ByteBuffer { wrShort(storage, at(advancePut(2)), v, big); return this }
    fun putShort(index: Int, v: Short): ByteBuffer { check(index, 2); wrShort(storage, at(index), v, big); return this }
    fun getChar(): Char = getShort().toInt().toChar()
    fun putChar(c: Char): ByteBuffer = putShort(c.code.toShort())

    fun getInt(): Int = rdInt(storage, at(advanceGet(4)), big)
    fun getInt(index: Int): Int { check(index, 4); return rdInt(storage, at(index), big) }
    fun putInt(v: Int): ByteBuffer { wrInt(storage, at(advancePut(4)), v, big); return this }
    fun putInt(index: Int, v: Int): ByteBuffer { check(index, 4); wrInt(storage, at(index), v, big); return this }

    fun getLong(): Long = rdLong(storage, at(advanceGet(8)), big)
    fun getLong(index: Int): Long { check(index, 8); return rdLong(storage, at(index), big) }
    fun putLong(v: Long): ByteBuffer { wrLong(storage, at(advancePut(8)), v, big); return this }
    fun putLong(index: Int, v: Long): ByteBuffer { check(index, 8); wrLong(storage, at(index), v, big); return this }

    fun getFloat(): Float = Float.fromBits(getInt())
    fun getFloat(index: Int): Float = Float.fromBits(getInt(index))
    fun putFloat(v: Float): ByteBuffer = putInt(v.toRawBits())
    fun putFloat(index: Int, v: Float): ByteBuffer = putInt(index, v.toRawBits())

    fun getDouble(): Double = Double.fromBits(getLong())
    fun getDouble(index: Int): Double = Double.fromBits(getLong(index))
    fun putDouble(v: Double): ByteBuffer = putLong(v.toRawBits())
    fun putDouble(index: Int, v: Double): ByteBuffer = putLong(index, v.toRawBits())

    // Kotlin's synthetic-property view of the Java getters (buf.int, buf.short, ...)
    val short: Short get() = getShort()
    val int: Int get() = getInt()
    val long: Long get() = getLong()
    val float: Float get() = getFloat()
    val double: Double get() = getDouble()
    val char: Char get() = getChar()

    fun array(): ByteArray = storage.heap ?: throw UnsupportedOperationException("direct buffer has no array")
    fun arrayOffset(): Int = base

    fun asFloatBuffer(): FloatBuffer = FloatBuffer(storage, at(pos), remaining() / 4, big)
    fun asShortBuffer(): ShortBuffer = ShortBuffer(storage, at(pos), remaining() / 2, big)
    fun asIntBuffer(): IntBuffer = IntBuffer(storage, at(pos), remaining() / 4, big)

    fun duplicate(): ByteBuffer {
        val d = ByteBuffer(storage, base, cap)
        d.pos = pos; d.lim = lim; d.mrk = mrk; d.big = big
        return d
    }
    fun slice(): ByteBuffer = ByteBuffer(storage, at(pos), remaining())
    fun compact(): ByteBuffer {
        val n = remaining()
        for (k in 0 until n) storage.setB(at(k), storage.getB(at(pos + k)))
        pos = n; lim = cap; mrk = -1
        return this
    }

    override fun position(newPosition: Int): ByteBuffer { super.position(newPosition); return this }
    override fun limit(newLimit: Int): ByteBuffer { super.limit(newLimit); return this }
    override fun mark(): ByteBuffer { super.mark(); return this }
    override fun reset(): ByteBuffer { super.reset(); return this }
    override fun clear(): ByteBuffer { super.clear(); return this }
    override fun flip(): ByteBuffer { super.flip(); return this }
    override fun rewind(): ByteBuffer { super.rewind(); return this }

    companion object {
        fun allocate(capacity: Int): ByteBuffer = ByteBuffer(Storage.heap(ByteArray(capacity)), 0, capacity)
        fun allocateDirect(capacity: Int): ByteBuffer = ByteBuffer(Storage.direct(capacity), 0, capacity)
        fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(Storage.heap(array), 0, array.size)
        fun wrap(array: ByteArray, offset: Int, length: Int): ByteBuffer {
            val b = ByteBuffer(Storage.heap(array), 0, array.size)
            b.pos = offset
            b.lim = offset + length
            return b
        }
    }
}

class FloatBuffer internal constructor(storage: Storage, base: Int, capacity: Int, private val big: Boolean) : Buffer(storage, base, capacity) {
    override val elemSize: Int get() = 4
    private val fast = storage.ptr != null && !big

    private fun rd(i: Int): Float =
        if (fast) storage.address(base + i * 4)!!.reinterpret<FloatVar>()[0] else Float.fromBits(rdInt(storage, base + i * 4, big))
    private fun wr(i: Int, v: Float) {
        if (fast) storage.address(base + i * 4)!!.reinterpret<FloatVar>()[0] = v else wrInt(storage, base + i * 4, v.toRawBits(), big)
    }

    fun get(): Float = rd(advanceGet(1))
    fun get(index: Int): Float { check(index, 1); return rd(index) }
    fun get(dst: FloatArray): FloatBuffer = get(dst, 0, dst.size)
    fun get(dst: FloatArray, offset: Int, length: Int): FloatBuffer {
        val p = advanceGet(length)
        if (fast && length > 0) dst.usePinned { memcpy(it.addressOf(offset), storage.address(base + p * 4), (length * 4).convert()) }
        else for (k in 0 until length) dst[offset + k] = rd(p + k)
        return this
    }
    fun put(v: Float): FloatBuffer { wr(advancePut(1), v); return this }
    fun put(index: Int, v: Float): FloatBuffer { check(index, 1); wr(index, v); return this }
    fun put(src: FloatArray): FloatBuffer = put(src, 0, src.size)
    fun put(src: FloatArray, offset: Int, length: Int): FloatBuffer {
        val p = advancePut(length)
        if (fast && length > 0) src.usePinned { memcpy(storage.address(base + p * 4), it.addressOf(offset), (length * 4).convert()) }
        else for (k in 0 until length) wr(p + k, src[offset + k])
        return this
    }
    fun put(src: FloatBuffer): FloatBuffer {
        val n = src.remaining()
        val tmp = FloatArray(n)
        src.get(tmp)
        return put(tmp)
    }
    fun array(): FloatArray = throw UnsupportedOperationException("direct buffer has no array")
    fun duplicate(): FloatBuffer {
        val d = FloatBuffer(storage, base, cap, big)
        d.pos = pos; d.lim = lim; d.mrk = mrk
        return d
    }
    fun slice(): FloatBuffer = FloatBuffer(storage, base + pos * 4, remaining(), big)
    fun order(): ByteOrder = if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

    override fun position(newPosition: Int): FloatBuffer { super.position(newPosition); return this }
    override fun limit(newLimit: Int): FloatBuffer { super.limit(newLimit); return this }
    override fun mark(): FloatBuffer { super.mark(); return this }
    override fun reset(): FloatBuffer { super.reset(); return this }
    override fun clear(): FloatBuffer { super.clear(); return this }
    override fun flip(): FloatBuffer { super.flip(); return this }
    override fun rewind(): FloatBuffer { super.rewind(); return this }

    companion object {
        fun allocate(capacity: Int): FloatBuffer = FloatBuffer(Storage.direct(capacity * 4), 0, capacity, false)
        fun wrap(array: FloatArray): FloatBuffer = allocate(array.size).put(array).rewind()
    }
}

class ShortBuffer internal constructor(storage: Storage, base: Int, capacity: Int, private val big: Boolean) : Buffer(storage, base, capacity) {
    override val elemSize: Int get() = 2
    private val fast = storage.ptr != null && !big

    private fun rd(i: Int): Short =
        if (fast) storage.address(base + i * 2)!!.reinterpret<ShortVar>()[0] else rdShort(storage, base + i * 2, big)
    private fun wr(i: Int, v: Short) {
        if (fast) storage.address(base + i * 2)!!.reinterpret<ShortVar>()[0] = v else wrShort(storage, base + i * 2, v, big)
    }

    fun get(): Short = rd(advanceGet(1))
    fun get(index: Int): Short { check(index, 1); return rd(index) }
    fun get(dst: ShortArray): ShortBuffer = get(dst, 0, dst.size)
    fun get(dst: ShortArray, offset: Int, length: Int): ShortBuffer {
        val p = advanceGet(length)
        if (fast && length > 0) dst.usePinned { memcpy(it.addressOf(offset), storage.address(base + p * 2), (length * 2).convert()) }
        else for (k in 0 until length) dst[offset + k] = rd(p + k)
        return this
    }
    fun put(v: Short): ShortBuffer { wr(advancePut(1), v); return this }
    fun put(index: Int, v: Short): ShortBuffer { check(index, 1); wr(index, v); return this }
    fun put(src: ShortArray): ShortBuffer = put(src, 0, src.size)
    fun put(src: ShortArray, offset: Int, length: Int): ShortBuffer {
        val p = advancePut(length)
        if (fast && length > 0) src.usePinned { memcpy(storage.address(base + p * 2), it.addressOf(offset), (length * 2).convert()) }
        else for (k in 0 until length) wr(p + k, src[offset + k])
        return this
    }
    fun duplicate(): ShortBuffer {
        val d = ShortBuffer(storage, base, cap, big)
        d.pos = pos; d.lim = lim; d.mrk = mrk
        return d
    }
    fun slice(): ShortBuffer = ShortBuffer(storage, base + pos * 2, remaining(), big)

    override fun position(newPosition: Int): ShortBuffer { super.position(newPosition); return this }
    override fun limit(newLimit: Int): ShortBuffer { super.limit(newLimit); return this }
    override fun mark(): ShortBuffer { super.mark(); return this }
    override fun reset(): ShortBuffer { super.reset(); return this }
    override fun clear(): ShortBuffer { super.clear(); return this }
    override fun flip(): ShortBuffer { super.flip(); return this }
    override fun rewind(): ShortBuffer { super.rewind(); return this }

    companion object {
        fun allocate(capacity: Int): ShortBuffer = ShortBuffer(Storage.direct(capacity * 2), 0, capacity, false)
        fun wrap(array: ShortArray): ShortBuffer = allocate(array.size).put(array).rewind()
    }
}

class IntBuffer internal constructor(storage: Storage, base: Int, capacity: Int, private val big: Boolean) : Buffer(storage, base, capacity) {
    override val elemSize: Int get() = 4
    private val fast = storage.ptr != null && !big

    private fun rd(i: Int): Int =
        if (fast) storage.address(base + i * 4)!!.reinterpret<IntVar>()[0] else rdInt(storage, base + i * 4, big)
    private fun wr(i: Int, v: Int) {
        if (fast) storage.address(base + i * 4)!!.reinterpret<IntVar>()[0] = v else wrInt(storage, base + i * 4, v, big)
    }

    fun get(): Int = rd(advanceGet(1))
    fun get(index: Int): Int { check(index, 1); return rd(index) }
    fun get(dst: IntArray): IntBuffer { val p = advanceGet(dst.size); for (k in dst.indices) dst[k] = rd(p + k); return this }
    fun put(v: Int): IntBuffer { wr(advancePut(1), v); return this }
    fun put(index: Int, v: Int): IntBuffer { check(index, 1); wr(index, v); return this }
    fun put(src: IntArray): IntBuffer = put(src, 0, src.size)
    fun put(src: IntArray, offset: Int, length: Int): IntBuffer { val p = advancePut(length); for (k in 0 until length) wr(p + k, src[offset + k]); return this }

    override fun position(newPosition: Int): IntBuffer { super.position(newPosition); return this }
    override fun limit(newLimit: Int): IntBuffer { super.limit(newLimit); return this }
    override fun mark(): IntBuffer { super.mark(); return this }
    override fun reset(): IntBuffer { super.reset(); return this }
    override fun clear(): IntBuffer { super.clear(); return this }
    override fun flip(): IntBuffer { super.flip(); return this }
    override fun rewind(): IntBuffer { super.rewind(); return this }

    companion object {
        fun allocate(capacity: Int): IntBuffer = IntBuffer(Storage.direct(capacity * 4), 0, capacity, false)
    }
}
