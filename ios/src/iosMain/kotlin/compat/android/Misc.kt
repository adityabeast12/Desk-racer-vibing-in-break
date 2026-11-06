@file:Suppress("unused")

package android.util

object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    private fun out(level: String, tag: String?, msg: String?, tr: Throwable? = null): Int {
        println("$level/$tag: $msg" + (tr?.let { " ($it)" } ?: ""))
        return 0
    }
    fun v(tag: String?, msg: String): Int = 0
    fun d(tag: String?, msg: String): Int = 0
    fun d(tag: String?, msg: String, tr: Throwable?): Int = 0
    fun i(tag: String?, msg: String): Int = out("I", tag, msg)
    fun w(tag: String?, msg: String): Int = out("W", tag, msg)
    fun w(tag: String?, msg: String, tr: Throwable?): Int = out("W", tag, msg, tr)
    fun w(tag: String?, tr: Throwable?): Int = out("W", tag, "", tr)
    fun e(tag: String?, msg: String): Int = out("E", tag, msg)
    fun e(tag: String?, msg: String, tr: Throwable?): Int = out("E", tag, msg, tr)
    fun isLoggable(tag: String?, level: Int): Boolean = level >= INFO
}
