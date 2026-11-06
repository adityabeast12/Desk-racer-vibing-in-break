@file:Suppress("unused")

package java.util.concurrent

import drshim.JRunnable
import drshim.JThread
import platform.Foundation.NSLock
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create

private inline fun <R> NSLock.locked(block: () -> R): R {
    lock()
    try { return block() } finally { unlock() }
}

fun interface ThreadFactory {
    fun newThread(r: JRunnable): JThread
}

fun interface Executor {
    fun execute(command: JRunnable)
}

interface ExecutorService : Executor {
    fun shutdown()
    fun shutdownNow(): List<JRunnable>
    val isShutdown: Boolean
}

/** Serial GCD queue: same ordering guarantee as a single-thread executor. */
private class SerialQueueExecutor(label: String) : ExecutorService {
    private val queue = dispatch_queue_create(label, null)
    private var shut = false
    override fun execute(command: JRunnable) {
        if (shut) return
        dispatch_async(queue) {
            try {
                command.run()
            } catch (t: Throwable) {
                println("drshim: executor task failed: $t")
            }
        }
    }
    override fun shutdown() { shut = true }
    override fun shutdownNow(): List<JRunnable> { shut = true; return emptyList() }
    override val isShutdown: Boolean get() = shut
}

object Executors {
    fun newSingleThreadExecutor(): ExecutorService = SerialQueueExecutor("drshim.serial")
    fun newSingleThreadExecutor(factory: ThreadFactory): ExecutorService = SerialQueueExecutor("drshim.serial")
}

class ConcurrentLinkedQueue<E> : AbstractMutableCollection<E>() {
    private val lock = NSLock()
    private val items = ArrayDeque<E>()

    override val size: Int get() = lock.locked { items.size }
    override fun add(element: E): Boolean = lock.locked { items.addLast(element); true }
    fun offer(e: E): Boolean = add(e)
    fun poll(): E? = lock.locked { items.removeFirstOrNull() }
    fun peek(): E? = lock.locked { items.firstOrNull() }
    override fun isEmpty(): Boolean = lock.locked { items.isEmpty() }
    override fun clear() = lock.locked { items.clear() }
    override fun remove(element: E): Boolean = lock.locked { items.remove(element) }
    override fun contains(element: E): Boolean = lock.locked { items.contains(element) }

    override fun iterator(): MutableIterator<E> {
        val snap = lock.locked { items.toList() }
        return object : MutableIterator<E> {
            var i = 0
            var last: Any? = null
            var hasLast = false
            override fun hasNext(): Boolean = i < snap.size
            override fun next(): E {
                val e = snap[i++]
                last = e; hasLast = true
                return e
            }
            override fun remove() {
                if (!hasLast) throw IllegalStateException()
                hasLast = false
                val target = last
                lock.locked {
                    val idx = items.indexOfFirst { it === target }
                    if (idx >= 0) items.removeAt(idx)
                }
            }
        }
    }
}

class CopyOnWriteArrayList<E>() : AbstractMutableList<E>() {
    constructor(src: Collection<E>) : this() { arr = src.toList() }

    private val lock = NSLock()
    @kotlin.concurrent.Volatile private var arr: List<E> = emptyList()

    override val size: Int get() = arr.size
    override fun get(index: Int): E = arr[index]
    override fun set(index: Int, element: E): E = lock.locked {
        val m = arr.toMutableList(); val old = m.set(index, element); arr = m; old
    }
    override fun add(index: Int, element: E) = lock.locked {
        val m = arr.toMutableList(); m.add(index, element); arr = m
    }
    override fun removeAt(index: Int): E = lock.locked {
        val m = arr.toMutableList(); val old = m.removeAt(index); arr = m; old
    }
    override fun remove(element: E): Boolean = lock.locked {
        val m = arr.toMutableList(); val r = m.remove(element); if (r) arr = m; r
    }
    override fun clear() = lock.locked { arr = emptyList() }
    override fun iterator(): MutableIterator<E> {
        val it = arr.iterator()
        return object : MutableIterator<E> {
            override fun hasNext() = it.hasNext()
            override fun next() = it.next()
            override fun remove() = throw UnsupportedOperationException()
        }
    }
    fun addIfAbsent(e: E): Boolean = lock.locked { if (arr.contains(e)) false else { arr = arr + e; true } }
}

class ConcurrentHashMap<K, V> : AbstractMutableMap<K, V>() {
    private val lock = NSLock()
    private val map = HashMap<K, V>()

    override val size: Int get() = lock.locked { map.size }
    override fun get(key: K): V? = lock.locked { map[key] }
    override fun containsKey(key: K): Boolean = lock.locked { map.containsKey(key) }
    override fun put(key: K, value: V): V? = lock.locked { map.put(key, value) }
    override fun remove(key: K): V? = lock.locked { map.remove(key) }
    override fun clear() = lock.locked { map.clear() }
    override fun isEmpty(): Boolean = lock.locked { map.isEmpty() }
    fun putIfAbsent(key: K, value: V): V? = lock.locked { val cur = map[key]; if (cur == null) map[key] = value; cur }
    fun computeIfAbsent(key: K, fn: (K) -> V): V = lock.locked { map.getOrPut(key) { fn(key) } }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = lock.locked { HashMap(map) }.entries
}
