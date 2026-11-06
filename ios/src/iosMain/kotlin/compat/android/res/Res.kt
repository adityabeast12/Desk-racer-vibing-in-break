@file:Suppress("unused")

package android.content.res

import java.io.ByteArrayInputStream
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.InputStream

class AssetManager {
    fun open(path: String): InputStream {
        val f = java.io.File(drshim.Fs.assetPath(path))
        val bytes = f.readBytesOrNull() ?: throw FileNotFoundException("asset $path")
        return ByteArrayInputStream(bytes)
    }
    fun open(path: String, mode: Int): InputStream = open(path)

    fun openFd(path: String): AssetFileDescriptor {
        val full = drshim.Fs.assetPath(path)
        if (!java.io.File(full).exists() && drshim.Fs.audioAlternative(full) == null) throw FileNotFoundException("asset $path")
        return AssetFileDescriptor(FileDescriptor(full), 0L, java.io.File(full).length())
    }

    fun list(path: String): Array<String>? = drshim.Fs.list(drshim.Fs.assetPath(path))?.toTypedArray() ?: emptyArray()

    companion object {
        const val ACCESS_STREAMING = 2
    }
}

class AssetFileDescriptor(val fileDescriptor: FileDescriptor, val startOffset: Long, val length: Long) : AutoCloseable {
    val declaredLength: Long get() = length
    override fun close() {}
}

class Configuration {
    var screenWidthDp: Int = 0
    var screenHeightDp: Int = 0
    var densityDpi: Int = 480
    var orientation: Int = ORIENTATION_LANDSCAPE
    var fontScale: Float = 1f

    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
    }
}

class Resources {
    val configuration: Configuration get() = drshim.Platform.configuration
    val displayMetrics: DisplayMetricsShim get() = DisplayMetricsShim()
    fun getString(id: Int): String = drshim.Res.strings[id] ?: ""
}

class DisplayMetricsShim {
    val density: Float get() = drshim.Platform.screenScale
    val widthPixels: Int get() = (drshim.Platform.configuration.screenWidthDp * density).toInt()
    val heightPixels: Int get() = (drshim.Platform.configuration.screenHeightDp * density).toInt()
}
