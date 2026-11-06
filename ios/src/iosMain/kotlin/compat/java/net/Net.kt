@file:Suppress("unused")

package java.net

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EINPROGRESS
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.IPPROTO_TCP
import platform.posix.O_NONBLOCK
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_DGRAM
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_BROADCAST
import platform.posix.SO_ERROR
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_REUSEADDR
import platform.posix.SO_REUSEPORT
import platform.posix.TCP_NODELAY
import platform.posix.accept as posix_accept
import platform.posix.bind as posix_bind
import platform.posix.close as posix_close
import platform.posix.connect as posix_connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.getsockopt
import platform.posix.listen as posix_listen
import platform.posix.poll as posix_poll
import platform.posix.pollfd
import platform.posix.recv as posix_recv
import platform.posix.recvfrom as posix_recvfrom
import platform.posix.send as posix_send
import platform.posix.sendto as posix_sendto
import platform.posix.setsockopt
import platform.posix.shutdown as posix_shutdown
import platform.posix.sockaddr_in
import platform.posix.socket as posix_socket
import platform.posix.socklen_tVar
import platform.posix.strerror
import platform.posix.timeval
import kotlinx.cinterop.toKString

open class SocketException(message: String? = null) : IOException(message)
open class SocketTimeoutException(message: String? = null) : InterruptedIOException(message)
open class ConnectException(message: String? = null) : SocketException(message)
open class UnknownHostException(message: String? = null) : IOException(message)

private fun errStr(): String = strerror(errno)?.toKString() ?: "errno $errno"

open class InetAddress internal constructor(internal val bytes: ByteArray) {
    val address: ByteArray get() = bytes.copyOf()
    val hostAddress: String get() = bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    val hostName: String get() = hostAddress
    val isLoopbackAddress: Boolean get() = (bytes[0].toInt() and 0xFF) == 127
    val isAnyLocalAddress: Boolean get() = bytes.all { it.toInt() == 0 }
    val isSiteLocalAddress: Boolean get() {
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }
    /** IPv4 in network byte order as it sits in sin_addr.s_addr on a little-endian CPU. */
    internal val sAddr: UInt get() =
        (bytes[0].toUInt() and 0xFFu) or ((bytes[1].toUInt() and 0xFFu) shl 8) or
            ((bytes[2].toUInt() and 0xFFu) shl 16) or ((bytes[3].toUInt() and 0xFFu) shl 24)

    override fun equals(other: Any?): Boolean = other is InetAddress && other.bytes.contentEquals(bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "/$hostAddress"

    companion object {
        fun getByName(host: String?): InetAddress {
            val h = (host ?: "127.0.0.1").trim()
            if (h.equals("localhost", ignoreCase = true)) return Inet4Address(byteArrayOf(127, 0, 0, 1))
            val parts = h.split('.')
            if (parts.size == 4) {
                val nums = parts.map { it.toIntOrNull() }
                if (nums.all { it != null && it in 0..255 }) {
                    return Inet4Address(ByteArray(4) { nums[it]!!.toByte() })
                }
            }
            throw UnknownHostException("cannot resolve '$h' (IPv4 literal expected)")
        }
        fun getByAddress(addr: ByteArray): InetAddress {
            if (addr.size != 4) throw UnknownHostException("IPv4 only")
            return Inet4Address(addr.copyOf())
        }
        fun getLocalHost(): InetAddress = Inet4Address(byteArrayOf(127, 0, 0, 1))
        internal fun fromSAddr(s: UInt): InetAddress = Inet4Address(
            byteArrayOf(s.toByte(), (s shr 8).toByte(), (s shr 16).toByte(), (s shr 24).toByte())
        )
    }
}

class Inet4Address internal constructor(bytes: ByteArray) : InetAddress(bytes)

open class SocketAddress

class InetSocketAddress private constructor(val address: InetAddress?, val port: Int, private val hostString: String?) : SocketAddress() {
    constructor(port: Int) : this(null, port, null)
    constructor(addr: InetAddress?, port: Int) : this(addr, port, null)
    constructor(hostname: String, port: Int) : this(resolveOrNull(hostname), port, hostname)

    val hostName: String? get() = hostString ?: address?.hostAddress

    private companion object {
        fun resolveOrNull(h: String): InetAddress? = try { InetAddress.getByName(h) } catch (e: Exception) { null }
    }
    val isUnresolved: Boolean get() = address == null && hostString != null
    override fun toString(): String = "${address?.hostAddress ?: hostString ?: "0.0.0.0"}:$port"
}

private fun htons(p: Int): UShort = (((p and 0xFF) shl 8) or ((p ushr 8) and 0xFF)).toUShort()
private fun ntohs(p: UShort): Int = ((p.toInt() and 0xFF) shl 8) or ((p.toInt() ushr 8) and 0xFF)

private fun fillSockaddr(sa: sockaddr_in, addr: InetAddress?, port: Int) {
    sa.sin_len = sizeOf<sockaddr_in>().convert()
    sa.sin_family = AF_INET.convert()
    sa.sin_port = htons(port)
    sa.sin_addr.s_addr = addr?.sAddr ?: 0u
}

private fun setIntOpt(fd: Int, level: Int, name: Int, v: Int) = memScoped {
    val x = alloc<IntVar>()
    x.value = v
    setsockopt(fd, level, name, x.ptr, sizeOf<IntVar>().convert())
}

private fun setRecvTimeout(fd: Int, ms: Int) = memScoped {
    val tv = alloc<timeval>()
    tv.tv_sec = (ms / 1000).convert()
    tv.tv_usec = ((ms % 1000) * 1000).convert()
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())
}

private fun pollFd(fd: Int, events: Int, timeoutMs: Int): Int = memScoped {
    val p = alloc<pollfd>()
    p.fd = fd
    p.events = events.convert()
    p.revents = 0
    posix_poll(p.ptr, 1.convert(), timeoutMs)
}

private fun newSocket(type: Int): Int {
    val fd = posix_socket(AF_INET, type, 0)
    if (fd < 0) throw SocketException("socket(): ${errStr()}")
    // a write to a peer that already went away must not SIGPIPE-kill the app
    setIntOpt(fd, SOL_SOCKET, SO_NOSIGPIPE, 1)
    return fd
}

class Socket internal constructor(private var fd: Int, private var remote: InetAddress?, private var remotePort: Int) : AutoCloseable {
    constructor() : this(-1, null, 0)
    constructor(host: String, port: Int) : this() { connect(InetSocketAddress(host, port), 0) }

    @kotlin.concurrent.Volatile private var closed = false
    private var pendingNoDelay = false
    private var pendingTimeout = 0

    var tcpNoDelay: Boolean
        get() = pendingNoDelay
        set(v) {
            pendingNoDelay = v
            if (fd >= 0) setIntOpt(fd, IPPROTO_TCP, TCP_NODELAY, if (v) 1 else 0)
        }
    var soTimeout: Int
        get() = pendingTimeout
        set(v) {
            pendingTimeout = v
            if (fd >= 0) setRecvTimeout(fd, v)
        }
    var keepAlive: Boolean = false
    var trafficClass: Int = 0
    var receiveBufferSize: Int = 65536
    var sendBufferSize: Int = 65536
    var reuseAddress: Boolean = false

    val isClosed: Boolean get() = closed
    val isConnected: Boolean get() = fd >= 0 && remote != null && !closed
    val inetAddress: InetAddress get() = remote ?: InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
    val port: Int get() = remotePort

    fun connect(endpoint: SocketAddress) = connect(endpoint, 0)

    fun connect(endpoint: SocketAddress, timeout: Int) {
        val ep = endpoint as InetSocketAddress
        val addr = ep.address ?: throw UnknownHostException(ep.hostName)
        if (fd < 0) fd = newSocket(SOCK_STREAM)
        if (pendingNoDelay) setIntOpt(fd, IPPROTO_TCP, TCP_NODELAY, 1)
        val flags = fcntl(fd, F_GETFL, 0)
        fcntl(fd, F_SETFL, flags or O_NONBLOCK)
        val rc = memScoped {
            val sa = alloc<sockaddr_in>()
            fillSockaddr(sa, addr, ep.port)
            posix_connect(fd, sa.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
        if (rc != 0) {
            if (errno != EINPROGRESS) {
                val msg = errStr()
                close()
                throw ConnectException("connect ${addr.hostAddress}:${ep.port}: $msg")
            }
            val ready = pollFd(fd, POLLOUT, if (timeout > 0) timeout else 15000)
            if (ready <= 0) {
                close()
                throw SocketTimeoutException("connect timed out")
            }
            val soErr = memScoped {
                val e = alloc<IntVar>()
                val len = alloc<socklen_tVar>()
                len.value = sizeOf<IntVar>().convert()
                getsockopt(fd, SOL_SOCKET, SO_ERROR, e.ptr, len.ptr)
                e.value
            }
            if (soErr != 0) {
                val msg = strerror(soErr)?.toKString()
                close()
                throw ConnectException("connect ${addr.hostAddress}:${ep.port}: $msg")
            }
        }
        fcntl(fd, F_SETFL, flags and O_NONBLOCK.inv())
        if (pendingTimeout > 0) setRecvTimeout(fd, pendingTimeout)
        remote = addr
        remotePort = ep.port
    }

    private val inStream = object : InputStream() {
        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n <= 0) -1 else b[0].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (true) {
                if (closed) throw SocketException("Socket closed")
                val n = b.usePinned { posix_recv(fd, it.addressOf(off), len.convert(), 0) }.toInt()
                if (n > 0) return n
                if (n == 0) return -1
                val e = errno
                if (e == EINTR) continue
                if (e == EAGAIN || e == EWOULDBLOCK) throw SocketTimeoutException("Read timed out")
                throw SocketException("recv: ${strerror(e)?.toKString()}")
            }
        }
        override fun close() = this@Socket.close()
    }

    private val outStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            var sent = 0
            while (sent < len) {
                if (closed) throw SocketException("Socket closed")
                val n = b.usePinned { posix_send(fd, it.addressOf(off + sent), (len - sent).convert(), 0) }.toInt()
                if (n < 0) {
                    if (errno == EINTR) continue
                    throw SocketException("send: ${errStr()}")
                }
                sent += n
            }
        }
        override fun close() = this@Socket.close()
    }

    fun getInputStream(): InputStream = inStream
    fun getOutputStream(): OutputStream = outStream

    fun shutdownInput() { if (fd >= 0) posix_shutdown(fd, SHUT_RDWR) }
    fun shutdownOutput() { if (fd >= 0) posix_shutdown(fd, SHUT_RDWR) }

    override fun close() {
        if (closed) return
        closed = true
        val f = fd
        if (f >= 0) {
            posix_shutdown(f, SHUT_RDWR) // wakes a thread blocked in recv()
            posix_close(f)
        }
    }
}

class ServerSocket() : AutoCloseable {
    constructor(port: Int) : this() { bind(InetSocketAddress(port)) }

    private var fd = -1
    @kotlin.concurrent.Volatile private var closed = false
    var reuseAddress: Boolean = false
    var soTimeout: Int = 0
    var localPort: Int = -1
        private set
    val isClosed: Boolean get() = closed
    val isBound: Boolean get() = localPort >= 0

    fun bind(endpoint: SocketAddress?) = bind(endpoint, 50)

    fun bind(endpoint: SocketAddress?, backlog: Int) {
        val ep = endpoint as? InetSocketAddress ?: InetSocketAddress(0)
        fd = newSocket(SOCK_STREAM)
        if (reuseAddress) {
            setIntOpt(fd, SOL_SOCKET, SO_REUSEADDR, 1)
            setIntOpt(fd, SOL_SOCKET, SO_REUSEPORT, 1)
        }
        val rc = memScoped {
            val sa = alloc<sockaddr_in>()
            fillSockaddr(sa, ep.address, ep.port)
            posix_bind(fd, sa.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
        if (rc != 0) {
            val msg = errStr()
            close()
            throw SocketException("bind :${ep.port}: $msg")
        }
        if (posix_listen(fd, backlog) != 0) throw SocketException("listen: ${errStr()}")
        localPort = ep.port
    }

    fun accept(): Socket {
        val waitStart = drshim.JSystem.currentTimeMillis()
        while (true) {
            if (closed) throw SocketException("Socket closed")
            // poll in slices so close() from another thread ends accept() promptly (Darwin doesn't wake it)
            val r = pollFd(fd, POLLIN, 250)
            if (closed) throw SocketException("Socket closed")
            if (r > 0) break
            if (soTimeout > 0 && drshim.JSystem.currentTimeMillis() - waitStart >= soTimeout) throw SocketTimeoutException("Accept timed out")
        }
        return memScoped {
            val sa = alloc<sockaddr_in>()
            val len = alloc<socklen_tVar>()
            len.value = sizeOf<sockaddr_in>().convert()
            val cfd = posix_accept(fd, sa.ptr.reinterpret(), len.ptr)
            if (cfd < 0) throw SocketException("accept: ${errStr()}")
            setIntOpt(cfd, SOL_SOCKET, SO_NOSIGPIPE, 1)
            Socket(cfd, InetAddress.fromSAddr(sa.sin_addr.s_addr), ntohs(sa.sin_port))
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (fd >= 0) posix_close(fd)
    }
}

class DatagramPacket(buf: ByteArray, offset: Int, length: Int, address: InetAddress?, port: Int) {
    constructor(buf: ByteArray, length: Int) : this(buf, 0, length, null, -1)
    constructor(buf: ByteArray, offset: Int, length: Int) : this(buf, offset, length, null, -1)
    constructor(buf: ByteArray, length: Int, address: InetAddress?, port: Int) : this(buf, 0, length, address, port)

    var data: ByteArray = buf
        private set
    var offset: Int = offset
        private set
    var length: Int = length
    var address: InetAddress? = address
    var port: Int = port
    /** Capacity available for receive(), reset by setData/setLength like the JDK's bufLength. */
    internal var capacity: Int = length

    fun setData(buf: ByteArray) = setData(buf, 0, buf.size)
    fun setData(buf: ByteArray, offset: Int, length: Int) {
        data = buf; this.offset = offset; this.length = length; capacity = length
    }
    fun setLength(len: Int) { length = len; capacity = len }
    val socketAddress: SocketAddress get() = InetSocketAddress(address, port)
}

class DatagramSocket(bindaddr: SocketAddress?) : AutoCloseable {
    constructor() : this(InetSocketAddress(0))
    constructor(port: Int) : this(InetSocketAddress(port))

    private val fd: Int = newSocket(SOCK_DGRAM)
    @kotlin.concurrent.Volatile private var closed = false
    private var timeoutMs = 0
    var localPort: Int = -1
        private set

    var reuseAddress: Boolean = false
        set(v) {
            field = v
            setIntOpt(fd, SOL_SOCKET, SO_REUSEADDR, if (v) 1 else 0)
            setIntOpt(fd, SOL_SOCKET, SO_REUSEPORT, if (v) 1 else 0)
        }
    var broadcast: Boolean = false
        set(v) {
            field = v
            setIntOpt(fd, SOL_SOCKET, SO_BROADCAST, if (v) 1 else 0)
        }
    var soTimeout: Int
        get() = timeoutMs
        set(v) {
            timeoutMs = v
            setRecvTimeout(fd, v)
        }
    var receiveBufferSize: Int = 65536
    var sendBufferSize: Int = 65536
    var trafficClass: Int = 0

    val isClosed: Boolean get() = closed
    val isBound: Boolean get() = localPort >= 0

    init {
        if (bindaddr != null) bind(bindaddr)
    }

    fun bind(addr: SocketAddress?) {
        val ep = addr as? InetSocketAddress ?: InetSocketAddress(0)
        val rc = memScoped {
            val sa = alloc<sockaddr_in>()
            fillSockaddr(sa, ep.address, ep.port)
            posix_bind(fd, sa.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
        if (rc != 0) throw SocketException("udp bind :${ep.port}: ${errStr()}")
        localPort = ep.port
    }

    fun send(p: DatagramPacket) {
        if (closed) throw SocketException("Socket closed")
        val addr = p.address ?: throw IllegalArgumentException("packet has no address")
        val rc = memScoped {
            val sa = alloc<sockaddr_in>()
            fillSockaddr(sa, addr, p.port)
            p.data.usePinned { pin ->
                posix_sendto(fd, pin.addressOf(p.offset), p.length.convert(), 0, sa.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            }
        }.toInt()
        if (rc < 0) throw SocketException("sendto ${addr.hostAddress}: ${errStr()}")
    }

    fun receive(p: DatagramPacket) {
        while (true) {
            if (closed) throw SocketException("Socket closed")
            val n = memScoped {
                val sa = alloc<sockaddr_in>()
                val len = alloc<socklen_tVar>()
                len.value = sizeOf<sockaddr_in>().convert()
                val r = p.data.usePinned { pin ->
                    posix_recvfrom(fd, pin.addressOf(p.offset), p.capacity.convert(), 0, sa.ptr.reinterpret(), len.ptr)
                }.toInt()
                if (r >= 0) {
                    p.address = InetAddress.fromSAddr(sa.sin_addr.s_addr)
                    p.port = ntohs(sa.sin_port)
                }
                r
            }
            if (n >= 0) {
                p.length = n
                return
            }
            val e = errno
            if (e == EINTR) continue
            if (closed) throw SocketException("Socket closed")
            if (e == EAGAIN || e == EWOULDBLOCK) throw SocketTimeoutException("Receive timed out")
            throw SocketException("recvfrom: ${strerror(e)?.toKString()}")
        }
    }

    fun connect(addr: InetAddress, port: Int) {}
    fun disconnect() {}

    override fun close() {
        if (closed) return
        closed = true
        posix_shutdown(fd, SHUT_RDWR)
        posix_close(fd)
    }
}

