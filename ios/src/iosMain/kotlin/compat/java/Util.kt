@file:Suppress("unused")

package java.util

import platform.Foundation.NSUUID

class Locale(val language: String, val country: String = "", val variant: String = "") {
    override fun toString(): String = if (country.isEmpty()) language else "${language}_$country"
    override fun equals(other: Any?): Boolean = other is Locale && other.language == language && other.country == country
    override fun hashCode(): Int = language.hashCode() * 31 + country.hashCode()

    companion object {
        val US = Locale("en", "US")
        val UK = Locale("en", "GB")
        val ENGLISH = Locale("en")
        val ROOT = Locale("")
        val GERMANY = Locale("de", "DE")
        fun getDefault(): Locale = US
    }
}

class UUID private constructor(private val s: String) {
    override fun toString(): String = s
    override fun equals(other: Any?): Boolean = other is UUID && other.s == s
    override fun hashCode(): Int = s.hashCode()

    companion object {
        fun randomUUID(): UUID = UUID(NSUUID().UUIDString.lowercase())
        fun fromString(s: String): UUID = UUID(s.lowercase())
        fun nameUUIDFromBytes(b: ByteArray): UUID {
            val h = drshim.Sha256.digest(b)
            val hex = h.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            return UUID("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}")
        }
    }
}

class Date(val time: Long = drshim.JSystem.currentTimeMillis()) {
    fun getTime(): Long = time
}
