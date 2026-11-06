@file:Suppress("unused")

package java.text

import java.util.Locale

open class NumberFormat private constructor(private val integerOnly: Boolean) {
    var isGroupingUsed: Boolean = true
    var maximumFractionDigits: Int = if (integerOnly) 0 else 3
    var minimumFractionDigits: Int = 0

    fun format(value: Long): String = drshim.jformat(if (isGroupingUsed) "%,d" else "%d", arrayOf(value))
    fun format(value: Int): String = format(value.toLong())
    fun format(value: Double): String {
        if (integerOnly || maximumFractionDigits == 0) return format(kotlin.math.round(value).toLong())
        var s = drshim.jformat(if (isGroupingUsed) "%,.${maximumFractionDigits}f" else "%.${maximumFractionDigits}f", arrayOf(value))
        if (s.contains('.')) {
            val keep = minimumFractionDigits
            while (s.endsWith('0') && s.substringAfter('.').length > keep) s = s.dropLast(1)
            if (s.endsWith('.')) s = s.dropLast(1)
        }
        return s
    }
    fun format(value: Any?): String = when (value) {
        is Int -> format(value)
        is Long -> format(value)
        is Number -> format(value.toDouble())
        else -> value.toString()
    }

    companion object {
        fun getIntegerInstance(locale: Locale? = null): NumberFormat = NumberFormat(true)
        fun getInstance(locale: Locale? = null): NumberFormat = NumberFormat(false)
        fun getNumberInstance(locale: Locale? = null): NumberFormat = NumberFormat(false)
    }
}

class SimpleDateFormat(private val pattern: String, private val locale: Locale? = null) {
    fun format(d: java.util.Date): String = d.time.toString()
}
