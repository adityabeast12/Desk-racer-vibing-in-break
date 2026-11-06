package drshim

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

object Fs {
    fun isDirectory(path: String): Boolean = memScoped {
        val isDir = alloc<BooleanVar>()
        NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory = isDir.ptr) && isDir.value
    }

    fun mkdirs(path: String): Boolean =
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)

    fun remove(path: String): Boolean = NSFileManager.defaultManager.removeItemAtPath(path, error = null)

    fun move(from: String, to: String): Boolean = NSFileManager.defaultManager.moveItemAtPath(from, toPath = to, error = null)

    fun list(path: String): List<String>? =
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error = null)?.map { it.toString() }

    /** Writable per-app directory (Android's Context.filesDir). */
    val documentsDir: String by lazy {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        (dirs.firstOrNull() as? String) ?: "/tmp"
    }

    /** Folder in the .app bundle that holds the Android assets (app/src/main/assets + asset-pack). */
    val assetsDir: String by lazy { (NSBundle.mainBundle.resourcePath ?: "") + "/assets" }

    fun assetPath(rel: String): String = assetsDir + "/" + rel.trimStart('/')

    /** Android ships OGG; AVFoundation can't decode it, so the iOS bundle carries .m4a twins. */
    fun audioAlternative(path: String): String? {
        val fm = NSFileManager.defaultManager
        if (path.endsWith(".ogg")) {
            val m4a = path.removeSuffix(".ogg") + ".m4a"
            if (fm.fileExistsAtPath(m4a)) return m4a
            val caf = path.removeSuffix(".ogg") + ".caf"
            if (fm.fileExistsAtPath(caf)) return caf
            return null
        }
        return if (fm.fileExistsAtPath(path)) path else null
    }
}
