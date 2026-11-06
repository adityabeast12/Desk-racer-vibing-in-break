@file:Suppress("unused")

package java.nio.charset

class Charset private constructor(val name: String, val isSingleByte: Boolean) {
    fun name(): String = name
    override fun toString(): String = name

    companion object {
        internal val UTF8 = Charset("UTF-8", false)
        internal val ASCII = Charset("US-ASCII", true)
        internal val LATIN1 = Charset("ISO-8859-1", true)
        internal val UTF16 = Charset("UTF-16", false)
        fun forName(name: String): Charset = when (name.uppercase()) {
            "US-ASCII", "ASCII" -> ASCII
            "ISO-8859-1", "LATIN1" -> LATIN1
            else -> UTF8
        }
        fun defaultCharset(): Charset = UTF8
    }
}

object StandardCharsets {
    val UTF_8: Charset = Charset.UTF8
    val US_ASCII: Charset = Charset.ASCII
    val ISO_8859_1: Charset = Charset.LATIN1
    val UTF_16: Charset = Charset.UTF16
}
