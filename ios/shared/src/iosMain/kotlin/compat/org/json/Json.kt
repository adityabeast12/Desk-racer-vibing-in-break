@file:Suppress("unused")

package org.json

class JSONException(message: String) : RuntimeException(message)

private object Null {
    override fun toString(): String = "null"
}

class JSONObject internal constructor(internal val map: LinkedHashMap<String, Any?>) {
    constructor() : this(LinkedHashMap())
    constructor(json: String) : this(
        (JsonParser(json).parseTop() as? JSONObject)?.map ?: throw JSONException("not a JSON object")
    )

    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun length(): Int = map.size
    fun has(name: String): Boolean = map.containsKey(name)
    fun isNull(name: String): Boolean = map[name] == null || map[name] === Null
    fun opt(name: String): Any? = map[name]?.takeUnless { it === Null }
    fun get(name: String): Any = opt(name) ?: throw JSONException("no value for $name")

    fun optString(name: String): String = optString(name, "")
    fun optString(name: String, fallback: String): String {
        val v = opt(name) ?: return fallback
        return if (v is String) v else v.toString()
    }
    fun getString(name: String): String = opt(name)?.let { if (it is String) it else it.toString() } ?: throw JSONException("no string $name")

    fun optDouble(name: String): Double = optDouble(name, Double.NaN)
    fun optDouble(name: String, fallback: Double): Double = toDouble(opt(name)) ?: fallback
    fun getDouble(name: String): Double = toDouble(opt(name)) ?: throw JSONException("no number $name")

    fun optInt(name: String): Int = optInt(name, 0)
    fun optInt(name: String, fallback: Int): Int = toDouble(opt(name))?.toInt() ?: fallback
    fun getInt(name: String): Int = toDouble(opt(name))?.toInt() ?: throw JSONException("no int $name")

    fun optLong(name: String, fallback: Long = 0L): Long = toDouble(opt(name))?.toLong() ?: fallback
    fun getLong(name: String): Long = toDouble(opt(name))?.toLong() ?: throw JSONException("no long $name")

    fun optBoolean(name: String): Boolean = optBoolean(name, false)
    fun optBoolean(name: String, fallback: Boolean): Boolean = when (val v = opt(name)) {
        is Boolean -> v
        is String -> if (v.equals("true", true)) true else if (v.equals("false", true)) false else fallback
        else -> fallback
    }
    fun getBoolean(name: String): Boolean = opt(name) as? Boolean ?: throw JSONException("no boolean $name")

    fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject
    fun getJSONObject(name: String): JSONObject = optJSONObject(name) ?: throw JSONException("no object $name")
    fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray
    fun getJSONArray(name: String): JSONArray = optJSONArray(name) ?: throw JSONException("no array $name")

    fun put(name: String, value: Any?): JSONObject { map[name] = value; return this }
    fun remove(name: String): Any? = map.remove(name)

    override fun toString(): String = JsonWriter.write(this)

    companion object {
        val NULL: Any = Null
    }
}

class JSONArray internal constructor(internal val list: ArrayList<Any?>) {
    constructor() : this(ArrayList())
    constructor(json: String) : this((JsonParser(json).parseTop() as? JSONArray)?.list ?: throw JSONException("not a JSON array"))

    fun length(): Int = list.size
    fun opt(index: Int): Any? = list.getOrNull(index)?.takeUnless { it === Null }
    fun get(index: Int): Any = opt(index) ?: throw JSONException("no value at $index")
    fun isNull(index: Int): Boolean = opt(index) == null

    fun optString(index: Int): String = optString(index, "")
    fun optString(index: Int, fallback: String): String = opt(index)?.let { if (it is String) it else it.toString() } ?: fallback
    fun getString(index: Int): String = opt(index)?.toString() ?: throw JSONException("no string at $index")
    fun optDouble(index: Int): Double = optDouble(index, Double.NaN)
    fun optDouble(index: Int, fallback: Double): Double = toDouble(opt(index)) ?: fallback
    fun getDouble(index: Int): Double = toDouble(opt(index)) ?: throw JSONException("no number at $index")
    fun optInt(index: Int): Int = optInt(index, 0)
    fun optInt(index: Int, fallback: Int): Int = toDouble(opt(index))?.toInt() ?: fallback
    fun getInt(index: Int): Int = toDouble(opt(index))?.toInt() ?: throw JSONException("no int at $index")
    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = opt(index) as? Boolean ?: fallback
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
    fun getJSONObject(index: Int): JSONObject = optJSONObject(index) ?: throw JSONException("no object at $index")
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray
    fun getJSONArray(index: Int): JSONArray = optJSONArray(index) ?: throw JSONException("no array at $index")
    fun put(value: Any?): JSONArray { list.add(value); return this }

    override fun toString(): String = JsonWriter.write(this)
}

private fun toDouble(v: Any?): Double? = when (v) {
    is Double -> v
    is Long -> v.toDouble()
    is Int -> v.toDouble()
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

private class JsonParser(private val s: String) {
    private var i = 0

    fun parseTop(): Any? {
        skipWs()
        val v = value()
        return v
    }

    private fun skipWs() {
        while (i < s.length) {
            val c = s[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++
            else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') { while (i < s.length && s[i] != '\n') i++ }
            else break
        }
    }

    private fun value(): Any? {
        skipWs()
        if (i >= s.length) throw JSONException("unexpected end")
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"', '\'' -> str(c)
            't' -> { expect("true"); true }
            'f' -> { expect("false"); false }
            'n' -> { expect("null"); Null }
            else -> num()
        }
    }

    private fun expect(w: String) {
        if (!s.startsWith(w, i)) throw JSONException("bad literal at $i")
        i += w.length
    }

    private fun obj(): JSONObject {
        i++ // {
        val m = LinkedHashMap<String, Any?>()
        skipWs()
        if (i < s.length && s[i] == '}') { i++; return JSONObject(m) }
        while (true) {
            skipWs()
            val k = str(s[i])
            skipWs()
            if (s[i] != ':') throw JSONException("expected ':' at $i")
            i++
            m[k] = value()
            skipWs()
            when (s[i]) {
                ',' -> { i++; skipWs(); if (s[i] == '}') { i++; return JSONObject(m) } }
                '}' -> { i++; return JSONObject(m) }
                else -> throw JSONException("expected ',' or '}' at $i")
            }
        }
    }

    private fun arr(): JSONArray {
        i++
        val l = ArrayList<Any?>()
        skipWs()
        if (i < s.length && s[i] == ']') { i++; return JSONArray(l) }
        while (true) {
            l.add(value())
            skipWs()
            when (s[i]) {
                ',' -> { i++; skipWs(); if (s[i] == ']') { i++; return JSONArray(l) } }
                ']' -> { i++; return JSONArray(l) }
                else -> throw JSONException("expected ',' or ']' at $i")
            }
        }
    }

    private fun str(q: Char): String {
        if (s[i] != '"' && s[i] != '\'') throw JSONException("expected string at $i")
        i++
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            if (c == q) return sb.toString()
            if (c == '\\') {
                val e = s[i++]
                when (e) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                    'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> sb.append(e)
                }
            } else sb.append(c)
        }
        throw JSONException("unterminated string")
    }

    private fun num(): Any {
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        val t = s.substring(st, i)
        if (t.isEmpty()) throw JSONException("unexpected '${s[st]}' at $st")
        if (!t.contains('.') && !t.contains('e') && !t.contains('E')) {
            t.toIntOrNull()?.let { return it }
            t.toLongOrNull()?.let { return it }
        }
        return t.toDoubleOrNull() ?: throw JSONException("bad number '$t'")
    }
}

private object JsonWriter {
    fun write(v: Any?): String = StringBuilder().also { w(it, v) }.toString()
    private fun w(sb: StringBuilder, v: Any?) {
        when (v) {
            null, Null -> sb.append("null")
            is String -> q(sb, v)
            is Boolean, is Int, is Long -> sb.append(v.toString())
            is Number -> sb.append(v.toDouble().let { d -> if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString() })
            is JSONObject -> {
                sb.append('{')
                var first = true
                for ((k, x) in v.map) { if (!first) sb.append(','); first = false; q(sb, k); sb.append(':'); w(sb, x) }
                sb.append('}')
            }
            is JSONArray -> {
                sb.append('[')
                v.list.forEachIndexed { idx, x -> if (idx > 0) sb.append(','); w(sb, x) }
                sb.append(']')
            }
            else -> q(sb, v.toString())
        }
    }
    private fun q(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
        }
        sb.append('"')
    }
}
