package drshim

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Subset of java.util.Formatter: %[idx$][flags][width][.prec](d|s|S|f|e|g|x|X|o|c|b|%|n). */
fun jformat(fmt: String, args: Array<out Any?>): String {
    val out = StringBuilder()
    var i = 0
    var argIdx = 0
    while (i < fmt.length) {
        val c = fmt[i]
        if (c != '%') { out.append(c); i++; continue }
        i++
        if (i >= fmt.length) break
        // explicit argument index
        var explicitIdx = -1
        run {
            var j = i
            while (j < fmt.length && fmt[j].isDigit()) j++
            if (j < fmt.length && fmt[j] == '$' && j > i) {
                explicitIdx = fmt.substring(i, j).toInt() - 1
                i = j + 1
            }
        }
        var leftAlign = false; var zeroPad = false; var plus = false; var space = false; var group = false; var alt = false; var paren = false
        loop@ while (i < fmt.length) {
            when (fmt[i]) {
                '-' -> leftAlign = true
                '0' -> zeroPad = true
                '+' -> plus = true
                ' ' -> space = true
                ',' -> group = true
                '#' -> alt = true
                '(' -> paren = true
                else -> break@loop
            }
            i++
        }
        var width = -1
        val ws = i
        while (i < fmt.length && fmt[i].isDigit()) i++
        if (i > ws) width = fmt.substring(ws, i).toInt()
        var prec = -1
        if (i < fmt.length && fmt[i] == '.') {
            i++
            val ps = i
            while (i < fmt.length && fmt[i].isDigit()) i++
            prec = if (i > ps) fmt.substring(ps, i).toInt() else 0
        }
        if (i >= fmt.length) break
        val conv = fmt[i]; i++
        if (conv == '%') { out.append(pad("%", width, leftAlign, false)); continue }
        if (conv == 'n') { out.append('\n'); continue }
        val arg = if (explicitIdx >= 0) args.getOrNull(explicitIdx) else args.getOrNull(argIdx++)
        val body: String = when (conv) {
            'd' -> {
                val v = toLongArg(arg)
                signed(groupDigits(abs2(v), group), v < 0, plus, space, paren)
            }
            'x', 'X', 'o' -> {
                val raw = when (arg) {
                    is Byte -> (arg.toInt() and 0xFF).toLong()
                    is Short -> (arg.toInt() and 0xFFFF).toLong()
                    is Int -> arg.toLong() and 0xFFFFFFFFL
                    is Long -> arg
                    is Char -> arg.code.toLong()
                    else -> toLongArg(arg)
                }
                var s = if (conv == 'o') raw.toULong().toString(8) else raw.toULong().toString(16)
                if (alt) s = (if (conv == 'o') "0" else "0x") + s
                if (conv == 'X') s.uppercase() else s
            }
            'f', 'F' -> {
                val v = toDoubleArg(arg)
                if (v.isNaN()) "NaN" else if (v.isInfinite()) (if (v > 0) (if (plus) "+Infinity" else "Infinity") else "-Infinity")
                else {
                    val p = if (prec < 0) 6 else prec
                    val digits = fixed(abs(v), p)
                    val neg = v < 0 && digits.any { it in '1'..'9' }
                    val withGroup = if (group) {
                        val dot = digits.indexOf('.')
                        val ip = if (dot >= 0) digits.substring(0, dot) else digits
                        groupString(ip) + (if (dot >= 0) digits.substring(dot) else "")
                    } else digits
                    signed(withGroup, neg, plus, space, paren)
                }
            }
            'e', 'E' -> {
                val v = toDoubleArg(arg)
                val p = if (prec < 0) 6 else prec
                val s = sci(abs(v), p)
                signed(if (conv == 'E') s.uppercase() else s, v < 0, plus, space, paren)
            }
            'g', 'G' -> {
                val v = toDoubleArg(arg)
                val p = if (prec < 0) 6 else if (prec == 0) 1 else prec
                val a = abs(v)
                val s = if (a != 0.0 && (a < 1e-4 || a >= 10.0.pow(p))) sci(a, p - 1) else fixed(a, (p - 1 - (if (a == 0.0) 0 else floor(log10(a)).toInt())).coerceAtLeast(0))
                signed(if (conv == 'G') s.uppercase() else s, v < 0, plus, space, paren)
            }
            'c', 'C' -> when (arg) {
                is Char -> arg.toString()
                is Number -> arg.toInt().toChar().toString()
                else -> arg.toString()
            }.let { if (conv == 'C') it.uppercase() else it }
            'b', 'B' -> (if (arg == null) "false" else if (arg is Boolean) arg.toString() else "true").let { if (conv == 'B') it.uppercase() else it }
            's', 'S' -> {
                var s = arg.toString()
                if (prec >= 0 && s.length > prec) s = s.substring(0, prec)
                if (conv == 'S') s.uppercase() else s
            }
            else -> arg.toString()
        }
        val numeric = conv in "dxXofFeEgG"
        out.append(pad(body, width, leftAlign, zeroPad && numeric))
    }
    return out.toString()
}

private fun toLongArg(a: Any?): Long = when (a) {
    is Long -> a
    is Int -> a.toLong()
    is Short -> a.toLong()
    is Byte -> a.toLong()
    is Double -> a.toLong()
    is Float -> a.toLong()
    is Char -> a.code.toLong()
    is Number -> a.toLong()
    else -> 0L
}

private fun toDoubleArg(a: Any?): Double = when (a) {
    is Double -> a
    is Float -> a.toDouble()
    is Number -> a.toDouble()
    else -> 0.0
}

private fun abs2(v: Long): String = if (v == Long.MIN_VALUE) "9223372036854775808" else abs(v).toString()

private fun groupDigits(s: String, group: Boolean) = if (group) groupString(s) else s

private fun groupString(s: String): String {
    if (s.length <= 3) return s
    val sb = StringBuilder()
    val first = s.length % 3
    if (first > 0) sb.append(s, 0, first)
    var k = first
    while (k < s.length) {
        if (sb.isNotEmpty()) sb.append(',')
        sb.append(s, k, k + 3)
        k += 3
    }
    return sb.toString()
}

private fun signed(body: String, neg: Boolean, plus: Boolean, space: Boolean, paren: Boolean): String = when {
    neg && paren -> "($body)"
    neg -> "-$body"
    plus -> "+$body"
    space -> " $body"
    else -> body
}

private fun pad(s: String, width: Int, left: Boolean, zero: Boolean): String {
    if (width <= s.length) return s
    val n = width - s.length
    if (left) return s + " ".repeat(n)
    if (zero) {
        val signLen = if (s.startsWith("-") || s.startsWith("+") || s.startsWith(" ")) 1 else 0
        return s.substring(0, signLen) + "0".repeat(n) + s.substring(signLen)
    }
    return " ".repeat(n) + s
}

/** Non-negative fixed-point with HALF_UP rounding (like java.util.Formatter). */
internal fun fixed(v: Double, prec: Int): String {
    if (v >= 1e17) {
        val ip = v.toLong().toString()
        return if (prec > 0) ip + "." + "0".repeat(prec) else ip
    }
    val p = prec.coerceIn(0, 12)
    val scale = 10.0.pow(p)
    // nudge by a relative epsilon so 0.125 -> 0.13 like Java's exact-decimal HALF_UP
    val scaled = floor(v * scale + 0.5 + v * scale * 1e-15)
    val whole = (scaled / scale).toLong()
    var frac = (scaled - whole.toDouble() * scale).toLong()
    if (frac < 0) frac = 0
    val sb = StringBuilder(whole.toString())
    if (p > 0) {
        val fs = frac.toString().padStart(p, '0')
        sb.append('.').append(fs.takeLast(p))
        if (prec > p) sb.append("0".repeat(prec - p))
    }
    return sb.toString()
}

private fun sci(v: Double, prec: Int): String {
    if (v == 0.0) return fixed(0.0, prec) + "e+00"
    var exp = floor(log10(v)).toInt()
    var mant = v / 10.0.pow(exp)
    var m = fixed(mant, prec)
    if (m.startsWith("10")) { exp++; mant = v / 10.0.pow(exp); m = fixed(mant, prec) }
    val es = abs(exp).toString().padStart(2, '0')
    return m + "e" + (if (exp < 0) "-" else "+") + es
}
