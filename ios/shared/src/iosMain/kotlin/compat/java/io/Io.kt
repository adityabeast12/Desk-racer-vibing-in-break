@file:Suppress("unused")

package java.io

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.fflush
import kotlinx.cinterop.CPointer

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
open class EOFException(message: String? = null) : IOException(message)
open class FileNotFoundException(message: String? = null) : IOException(message)
open class InterruptedIOException(message: String? = null) : IOException(message)
class UncheckedIOException(message: String?, cause: IOException?) : RuntimeException(message, cause)

/** Path-only file descriptor: android.media players get the asset path through it. */
class FileDescriptor(val path: String = "") {
    fun valid(): Boolean = path.isNotEmpty()
}

abstract class InputStream : AutoCloseable {
    abstract fun read(): Int
    open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var i = 0
        while (i < len) {
            val c = read()
            if (c < 0) return if (i == 0) -1 else i
            b[off + i] = c.toByte()
            i++
        }
        return i
    }
    fun read(b: ByteArray): Int = read(b, 0, b.size)
    open fun available(): Int = 0
    open fun skip(n: Long): Long {
        var k = 0L
        while (k < n && read() >= 0) k++
        return k
    }
    override fun close() {}

    open fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
    fun readAllBytes(): ByteArray = readBytes()
    fun bufferedReader(): BufferedReader = BufferedReader(readBytes().decodeToString())
    fun bufferedReader(charset: java.nio.charset.Charset): BufferedReader = bufferedReader()
    fun reader(): BufferedReader = bufferedReader()
    fun buffered(): InputStream = this
    fun buffered(size: Int): InputStream = this
    fun copyTo(out: OutputStream, bufferSize: Int = 8192): Long {
        val buf = ByteArray(bufferSize)
        var total = 0L
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return total
    }
}

open class ByteArrayInputStream(private val buf: ByteArray, offset: Int = 0, length: Int = buf.size) : InputStream() {
    private var pos = offset
    private val end = (offset + length).coerceAtMost(buf.size)
    override fun read(): Int = if (pos < end) buf[pos++].toInt() and 0xFF else -1
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= end) return -1
        val n = minOf(len, end - pos)
        buf.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }
    override fun available(): Int = end - pos
    override fun readBytes(): ByteArray {
        val r = buf.copyOfRange(pos, end)
        pos = end
        return r
    }
}

class BufferedReader(private val text: String) : AutoCloseable {
    private var pos = 0
    fun readText(): String {
        val r = text.substring(pos)
        pos = text.length
        return r
    }
    fun readLine(): String? {
        if (pos >= text.length) return null
        val nl = text.indexOf('\n', pos)
        val line = if (nl < 0) text.substring(pos) else text.substring(pos, nl)
        pos = if (nl < 0) text.length else nl + 1
        return line.removeSuffix("\r")
    }
    fun readLines(): List<String> = readText().lines()
    fun lineSequence(): Sequence<String> = readText().lineSequence()
    fun <T> useLines(block: (Sequence<String>) -> T): T = block(lineSequence())
    override fun close() {}
}

abstract class OutputStream : AutoCloseable {
    abstract fun write(b: Int)
    open fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) write(b[i].toInt())
    }
    open fun write(b: ByteArray) = write(b, 0, b.size)
    open fun flush() {}
    override fun close() {}
    fun buffered(): OutputStream = this
    fun buffered(size: Int): OutputStream = this
}

open class ByteArrayOutputStream(initial: Int = 64) : OutputStream() {
    private var buf = ByteArray(maxOf(16, initial))
    private var count = 0
    private fun ensure(n: Int) {
        if (count + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, count + n))
    }
    override fun write(b: Int) { ensure(1); buf[count++] = b.toByte() }
    override fun write(b: ByteArray, off: Int, len: Int) {
        ensure(len)
        b.copyInto(buf, count, off, off + len)
        count += len
    }
    fun toByteArray(): ByteArray = buf.copyOf(count)
    fun size(): Int = count
    fun reset() { count = 0 }
    override fun toString(): String = buf.decodeToString(0, count)
}

class FileInputStream(file: File) : InputStream() {
    constructor(path: String) : this(File(path))
    private val data: ByteArray = file.readBytesOrNull() ?: throw FileNotFoundException(file.path)
    private val inner = ByteArrayInputStream(data)
    override fun read(): Int = inner.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
    override fun available(): Int = inner.available()
    override fun readBytes(): ByteArray = inner.readBytes()
}

class FileOutputStream(file: File, append: Boolean = false) : OutputStream() {
    constructor(path: String) : this(File(path))
    private val fp: CPointer<FILE>? = fopen(file.path, if (append) "ab" else "wb")
    init {
        if (fp == null) throw FileNotFoundException(file.path)
    }
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        b.usePinned { fwrite(it.addressOf(off), 1.convert(), len.convert(), fp) }
    }
    override fun flush() { fflush(fp) }
    override fun close() { if (fp != null) fclose(fp) }
}

class DataInputStream(private val input: InputStream) : InputStream() {
    override fun read(): Int = input.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)
    override fun available(): Int = input.available()
    override fun close() = input.close()

    fun readFully(b: ByteArray) = readFully(b, 0, b.size)
    fun readFully(b: ByteArray, off: Int, len: Int) {
        var n = 0
        while (n < len) {
            val r = input.read(b, off + n, len - n)
            if (r < 0) throw EOFException()
            n += r
        }
    }
    private fun u8(): Int {
        val c = input.read()
        if (c < 0) throw EOFException()
        return c
    }
    fun readByte(): Byte = u8().toByte()
    fun readUnsignedByte(): Int = u8()
    fun readBoolean(): Boolean = u8() != 0
    fun readShort(): Short = ((u8() shl 8) or u8()).toShort()
    fun readUnsignedShort(): Int = (u8() shl 8) or u8()
    fun readChar(): Char = readUnsignedShort().toChar()
    fun readInt(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
    fun readLong(): Long = (readInt().toLong() shl 32) or (readInt().toLong() and 0xFFFFFFFFL)
    fun readFloat(): Float = Float.fromBits(readInt())
    fun readDouble(): Double = Double.fromBits(readLong())
    fun readUTF(): String {
        val n = readUnsignedShort()
        val b = ByteArray(n)
        readFully(b)
        return b.decodeToString()
    }
    fun skipBytes(n: Int): Int = skip(n.toLong()).toInt()
}

class DataOutputStream(private val out: OutputStream) : OutputStream() {
    private var written = 0
    override fun write(b: Int) { out.write(b); written++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); written += len }
    override fun write(b: ByteArray) = write(b, 0, b.size)
    override fun flush() = out.flush()
    override fun close() { out.flush(); out.close() }
    fun size(): Int = written

    fun writeByte(v: Int) = write(v)
    fun writeBoolean(v: Boolean) = write(if (v) 1 else 0)
    fun writeShort(v: Int) = write(byteArrayOf((v ushr 8).toByte(), v.toByte()))
    fun writeChar(v: Int) = writeShort(v)
    fun writeInt(v: Int) = write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))
    fun writeLong(v: Long) { writeInt((v ushr 32).toInt()); writeInt(v.toInt()) }
    fun writeFloat(v: Float) = writeInt(v.toRawBits())
    fun writeDouble(v: Double) = writeLong(v.toRawBits())
    fun writeUTF(s: String) {
        val b = s.encodeToByteArray()
        writeShort(b.size)
        write(b)
    }
    fun writeBytes(s: String) = write(ByteArray(s.length) { s[it].code.toByte() })
}

class File(pathname: String) {
    constructor(parent: File?, child: String) : this(if (parent == null) child else parent.path.trimEnd('/') + "/" + child)
    constructor(parent: String?, child: String) : this(if (parent == null) child else parent.trimEnd('/') + "/" + child)

    val path: String = resolve(pathname)
    val absolutePath: String get() = path
    val canonicalPath: String get() = path
    val absoluteFile: File get() = this
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = if (name.contains('.')) name.substringAfterLast('.') else ""
    val parent: String? get() = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let { File(it) }

    val isFile: Boolean get() = exists() && !isDirectory
    val isDirectory: Boolean get() = drshim.Fs.isDirectory(path)

    fun exists(): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)
    fun length(): Long = readBytesOrNull()?.size?.toLong() ?: 0L
    fun mkdirs(): Boolean = drshim.Fs.mkdirs(path)
    fun mkdir(): Boolean = mkdirs()
    fun delete(): Boolean = drshim.Fs.remove(path)
    fun deleteRecursively(): Boolean = drshim.Fs.remove(path)
    fun list(): Array<String>? = drshim.Fs.list(path)?.toTypedArray()
    fun listFiles(): Array<File>? = drshim.Fs.list(path)?.map { File(this, it) }?.toTypedArray()
    fun createNewFile(): Boolean {
        if (exists()) return false
        writeBytes(ByteArray(0))
        return true
    }
    fun renameTo(dest: File): Boolean = drshim.Fs.move(path, dest.path)

    internal fun readBytesOrNull(): ByteArray? {
        val fp = fopen(path, "rb") ?: return null
        try {
            fseek(fp, 0, SEEK_END)
            val size = ftell(fp).toInt()
            fseek(fp, 0, SEEK_SET)
            if (size <= 0) return ByteArray(0)
            val out = ByteArray(size)
            val n = out.usePinned { fread(it.addressOf(0), 1.convert(), size.convert(), fp) }.toInt()
            return if (n == size) out else out.copyOf(n)
        } finally {
            fclose(fp)
        }
    }

    fun readBytes(): ByteArray = readBytesOrNull() ?: throw FileNotFoundException(path)
    fun readText(): String = readBytes().decodeToString()
    fun readText(charset: java.nio.charset.Charset): String = readText()
    fun readLines(): List<String> = readText().lines()
    fun writeBytes(bytes: ByteArray) {
        FileOutputStream(this).use { it.write(bytes) }
    }
    fun writeText(text: String) = writeBytes(text.encodeToByteArray())
    fun writeText(text: String, charset: java.nio.charset.Charset) = writeText(text)
    fun appendText(text: String) {
        FileOutputStream(this, append = true).use { it.write(text.encodeToByteArray()) }
    }
    fun inputStream(): InputStream = FileInputStream(this)
    fun outputStream(): OutputStream = FileOutputStream(this)
    fun bufferedReader(): BufferedReader = BufferedReader(readText())

    override fun toString(): String = path
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()

    private companion object {
        fun resolve(p: String): String = when {
            p.startsWith("/") -> p
            // relative paths (Android unit-test fallbacks like "app/src/main/assets/x") resolve inside the app sandbox
            else -> NSHomeDirectory() + "/tmp/" + p
        }
    }
}

class StringWriter {
    private val sb = StringBuilder()
    fun write(s: String) { sb.append(s) }
    override fun toString(): String = sb.toString()
}

class PrintWriter(private val w: StringWriter) {
    fun println(s: Any?) = w.write("$s\n")
    fun print(s: Any?) = w.write("$s")
    fun flush() {}
}
