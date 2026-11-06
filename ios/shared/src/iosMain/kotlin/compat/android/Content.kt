@file:Suppress("unused")

package android.content

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import platform.Foundation.NSUserDefaults

open class Context {
    open val applicationContext: Context get() = this
    val assets: AssetManager = AssetManager()
    val resources: Resources = Resources()
    val filesDir: java.io.File by lazy { java.io.File(drshim.Fs.documentsDir).also { it.mkdirs() } }
    val cacheDir: java.io.File by lazy { java.io.File(drshim.Fs.documentsDir + "/cache").also { it.mkdirs() } }
    val packageName: String = "com.aistudio.deskracerlan.ios"

    fun getAssets(): AssetManager = assets
    fun getExternalFilesDir(type: String?): java.io.File? = filesDir

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = UserDefaultsPrefs(name)

    open fun getSystemService(name: String): Any? = when (name) {
        VIBRATOR_SERVICE -> android.os.Vibrator.default
        VIBRATOR_MANAGER_SERVICE -> android.os.VibratorManager()
        ACTIVITY_SERVICE -> android.app.ActivityManager()
        else -> null
    }

    fun getString(id: Int): String = drshim.Res.strings[id] ?: ""
    fun getString(id: Int, vararg args: Any?): String = drshim.jformat(getString(id), args)

    /** iOS has no settings intents we can open from here; the call is a harmless no-op. */
    fun startActivity(intent: Intent) {
        drshim.Platform.openSettingsFor(intent.action)
    }

    companion object {
        const val MODE_PRIVATE = 0
        const val VIBRATOR_SERVICE = "vibrator"
        const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
        const val WIFI_SERVICE = "wifi"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val ACTIVITY_SERVICE = "activity"
        const val POWER_SERVICE = "power"
        const val SENSOR_SERVICE = "sensor"
        const val BATTERY_SERVICE = "batterymanager"

        /** The one app-wide context on iOS. */
        val app: Context by lazy { Context() }
    }
}

class Intent(val action: String? = null) {
    private var flags = 0
    fun addFlags(f: Int): Intent { flags = flags or f; return this }
    fun setFlags(f: Int): Intent { flags = f; return this }
    fun putExtra(name: String, value: Any?): Intent = this
    fun getBooleanExtra(name: String, def: Boolean): Boolean = def
    fun getIntExtra(name: String, def: Int): Int = def

    companion object {
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val ACTION_VIEW = "android.intent.action.VIEW"
    }
}

class IntentFilter(val action: String? = null)

class ContentValues {
    private val m = HashMap<String, Any?>()
    fun put(k: String, v: Any?) { m[k] = v }
    fun clear() = m.clear()
}

interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    val all: Map<String, *>

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

/** SharedPreferences on NSUserDefaults; keys are namespaced by the prefs file name. */
private class UserDefaultsPrefs(name: String) : SharedPreferences {
    private val d = NSUserDefaults.standardUserDefaults
    private val prefix = "$name."
    private fun k(key: String) = prefix + key
    private fun has(key: String) = d.objectForKey(k(key)) != null

    override fun getString(key: String, defValue: String?): String? = d.stringForKey(k(key)) ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        d.stringArrayForKey(k(key))?.map { it.toString() }?.toSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = if (has(key)) d.integerForKey(k(key)).toInt() else defValue
    override fun getLong(key: String, defValue: Long): Long = if (has(key)) d.integerForKey(k(key)) else defValue
    override fun getFloat(key: String, defValue: Float): Float = if (has(key)) d.floatForKey(k(key)) else defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = if (has(key)) d.boolForKey(k(key)) else defValue
    override fun contains(key: String): Boolean = has(key)
    override val all: Map<String, *>
        get() = d.dictionaryRepresentation().entries
            .mapNotNull { (key, v) -> (key as? String)?.takeIf { it.startsWith(prefix) }?.let { it.removePrefix(prefix) to v } }
            .toMap()

    override fun edit(): SharedPreferences.Editor = PrefsEditor()

    private inner class PrefsEditor : SharedPreferences.Editor {
        private val ops = ArrayList<() -> Unit>()
        private fun op(f: () -> Unit): SharedPreferences.Editor { ops.add(f); return this }
        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            op { if (value == null) d.removeObjectForKey(k(key)) else d.setObject(value, k(key)) }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor =
            op { if (values == null) d.removeObjectForKey(k(key)) else d.setObject(values.toList(), k(key)) }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = op { d.setInteger(value.toLong(), k(key)) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = op { d.setInteger(value, k(key)) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = op { d.setFloat(value, k(key)) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = op { d.setBool(value, k(key)) }
        override fun remove(key: String): SharedPreferences.Editor = op { d.removeObjectForKey(k(key)) }
        override fun clear(): SharedPreferences.Editor = op { all.keys.forEach { key -> d.removeObjectForKey(k(key)) } }
        override fun apply() {
            ops.forEach { it() }
            ops.clear()
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
    }
}
