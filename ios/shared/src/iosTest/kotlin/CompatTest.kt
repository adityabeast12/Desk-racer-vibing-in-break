import com.example.game.KartBodyType
import com.example.net.LanClient
import com.example.net.LanDiscovery
import com.example.net.LanHost
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS compat layer must behave byte-for-byte like the JVM classes the Android build uses. */
class CompatTest {
    @Test
    fun byteBufferIsJavaCompatible() {
        val b = ByteBuffer.allocate(32)
        b.putInt(0x01020304).putShort(0x0506.toShort()).putFloat(1f).putLong(0x0102030405060708L).put(7.toByte())
        assertEquals(19, b.position())
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 0x3F, -128, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 7),
            b.array().copyOf(19)
        )
        b.flip()
        assertEquals(0x01020304, b.int)
        assertEquals(0x0506.toShort(), b.short)
        assertEquals(1f, b.float)
        assertEquals(0x0102030405060708L, b.long)
        assertEquals(7.toByte(), b.get())
        assertTrue(!b.hasRemaining())

        val le = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        le.putInt(1)
        assertEquals(1.toByte(), le.get(0))
        val fb = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(floatArrayOf(1f, 2f, 3f)).position(1)
        assertEquals(2f, fb.get())
        assertEquals(3f, fb.get(2))

        val w = ByteBuffer.wrap(byteArrayOf(9, 0, 0, 0, 42), 1, 4)
        assertEquals(1, w.position())
        assertEquals(42, w.getInt())
    }

    @Test
    fun formatterMatchesJava() {
        assertEquals("01:05.250", drshim.jformat("%02d:%02d.%03d", arrayOf(1, 5, 250)))
        assertEquals("3.1", drshim.jformat("%.1f", arrayOf(3.14159f)))
        assertEquals("12,345", drshim.jformat("%,d", arrayOf(12345)))
        assertEquals("ff", drshim.jformat("%02x", arrayOf((-1).toByte())))
        assertEquals("+1.500", drshim.jformat("+%d.%03d", arrayOf(1L, 500L)))
        assertEquals("50%", drshim.jformat("%.0f%%", arrayOf(49.6f)))
    }

    @Test
    fun hmacSha256() {
        val mac = drshim.Sha256.hmac("key".encodeToByteArray(), "The quick brown fox jumps over the lazy dog".encodeToByteArray())
        val hex = mac.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", hex)
    }

    /** A client joins a host over real sockets on loopback: TCP join + lobby frames through the shims. */
    @Test
    fun lanJoinOverLoopback() = runBlocking {
        val ctx = android.content.Context.app
        val host = LanHost(
            discovery = LanDiscovery(ctx),
            onClientDisconnectedDuringRace = { _, _ -> },
            onRemoteKartUdpUpdate = { _, _ -> },
            onClientRejoinedDuringRace = { _, _ -> },
            onClientReliableItemAction = { _, _, _, _ -> }
        )
        host.startHosting(hostName = "ANDROID", hostColorIdx = 0, hostBody = KartBodyType.fromId(0), hostPlayerId = "host-1")
        delay(400)
        val client = LanClient()
        client.connectToHost(hostIpRaw = "127.0.0.1", playerName = "IPHONE", colorIndex = 2, bodyType = KartBodyType.fromId(1), playerId = "ios-1")
        val joined = withTimeoutOrNull(8000) {
            while (!(client.isConnected.value && host.slots.value.size >= 2 && client.slots.value.size >= 2)) delay(50)
            true
        }
        val hostSlots = host.slots.value.map { it.playerName }
        val clientSlots = client.slots.value.map { it.playerName }
        client.disconnect()
        host.stopHosting()
        assertEquals(true, joined, "join timed out: host=$hostSlots client=$clientSlots status=${client.statusMessage.value}")
        assertTrue("IPHONE" in hostSlots && "ANDROID" in clientSlots, "host=$hostSlots client=$clientSlots")
    }
}
