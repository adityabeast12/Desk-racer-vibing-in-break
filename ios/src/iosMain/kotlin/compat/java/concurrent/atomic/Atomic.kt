@file:Suppress("unused")

package java.util.concurrent.atomic

import platform.Foundation.NSLock

private inline fun <R> NSLock.locked(block: () -> R): R {
    lock()
    try { return block() } finally { unlock() }
}

class AtomicBoolean(initial: Boolean = false) {
    private val lock = NSLock()
    private var v = initial
    fun get(): Boolean = lock.locked { v }
    fun set(value: Boolean) = lock.locked { v = value }
    fun lazySet(value: Boolean) = set(value)
    fun getAndSet(value: Boolean): Boolean = lock.locked { val o = v; v = value; o }
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean = lock.locked {
        if (v == expect) { v = update; true } else false
    }
    override fun toString(): String = get().toString()
}

class AtomicInteger(initial: Int = 0) {
    private val lock = NSLock()
    private var v = initial
    fun get(): Int = lock.locked { v }
    fun set(value: Int) = lock.locked { v = value }
    fun getAndSet(value: Int): Int = lock.locked { val o = v; v = value; o }
    fun incrementAndGet(): Int = lock.locked { ++v }
    fun decrementAndGet(): Int = lock.locked { --v }
    fun getAndIncrement(): Int = lock.locked { v++ }
    fun getAndDecrement(): Int = lock.locked { v-- }
    fun addAndGet(d: Int): Int = lock.locked { v += d; v }
    fun getAndAdd(d: Int): Int = lock.locked { val o = v; v += d; o }
    fun compareAndSet(expect: Int, update: Int): Boolean = lock.locked { if (v == expect) { v = update; true } else false }
    fun toInt(): Int = get()
    override fun toString(): String = get().toString()
}

class AtomicLong(initial: Long = 0L) {
    private val lock = NSLock()
    private var v = initial
    fun get(): Long = lock.locked { v }
    fun set(value: Long) = lock.locked { v = value }
    fun getAndSet(value: Long): Long = lock.locked { val o = v; v = value; o }
    fun incrementAndGet(): Long = lock.locked { ++v }
    fun getAndIncrement(): Long = lock.locked { v++ }
    fun addAndGet(d: Long): Long = lock.locked { v += d; v }
    fun compareAndSet(expect: Long, update: Long): Boolean = lock.locked { if (v == expect) { v = update; true } else false }
    override fun toString(): String = get().toString()
}

class AtomicReference<V>(initial: V) {
    private val lock = NSLock()
    private var v: V = initial
    fun get(): V = lock.locked { v }
    fun set(value: V) = lock.locked { v = value }
    fun lazySet(value: V) = set(value)
    fun getAndSet(value: V): V = lock.locked { val o = v; v = value; o }
    fun compareAndSet(expect: V, update: V): Boolean = lock.locked {
        if (v === expect) { v = update; true } else false
    }
    fun updateAndGet(fn: (V) -> V): V = lock.locked { v = fn(v); v }
    fun getAndUpdate(fn: (V) -> V): V = lock.locked { val o = v; v = fn(v); o }
    override fun toString(): String = get().toString()
}
