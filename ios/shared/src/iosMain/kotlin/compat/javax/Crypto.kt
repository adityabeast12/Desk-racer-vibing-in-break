@file:Suppress("unused")

package javax.crypto

import javax.crypto.spec.SecretKeySpec

class Mac private constructor(private val algorithm: String) {
    private var key: ByteArray = ByteArray(0)
    private val data = java.io.ByteArrayOutputStream()

    fun init(k: SecretKeySpec) { key = k.encoded; data.reset() }
    fun update(b: ByteArray) = data.write(b)
    fun doFinal(): ByteArray {
        val r = drshim.Sha256.hmac(key, data.toByteArray())
        data.reset()
        return r
    }
    fun doFinal(b: ByteArray): ByteArray { update(b); return doFinal() }

    companion object {
        fun getInstance(algorithm: String): Mac {
            if (!algorithm.equals("HmacSHA256", ignoreCase = true)) throw IllegalArgumentException("unsupported $algorithm")
            return Mac(algorithm)
        }
    }
}
