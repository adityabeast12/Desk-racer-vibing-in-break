@file:Suppress("unused")

package java.lang

/** Only the static helpers the game uses via fully-qualified java.lang.Float. */
object Float {
    fun floatToIntBits(value: kotlin.Float): Int = if (value.isNaN()) 0x7fc00000 else value.toBits()
    fun floatToRawIntBits(value: kotlin.Float): Int = value.toRawBits()
    fun intBitsToFloat(bits: Int): kotlin.Float = kotlin.Float.fromBits(bits)
    fun compare(a: kotlin.Float, b: kotlin.Float): Int = a.compareTo(b)
    fun isNaN(v: kotlin.Float): Boolean = v.isNaN()
}

object Double {
    fun doubleToLongBits(value: kotlin.Double): Long = value.toBits()
    fun longBitsToDouble(bits: Long): kotlin.Double = kotlin.Double.fromBits(bits)
}

object Integer {
    fun toHexString(i: Int): String = (i.toLong() and 0xFFFFFFFFL).toString(16)
    fun parseInt(s: String): Int = s.toInt()
    const val MAX_VALUE: Int = Int.MAX_VALUE
    const val MIN_VALUE: Int = Int.MIN_VALUE
}
