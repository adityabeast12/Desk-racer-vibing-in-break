@file:Suppress("unused")

package android.graphics

import java.io.InputStream
import java.io.OutputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

class Matrix {
    var sx = 1f
    var sy = 1f
    fun postScale(x: Float, y: Float): Boolean { sx *= x; sy *= y; return true }
    fun setScale(x: Float, y: Float) { sx = x; sy = y }
}

/**
 * Decoded image. Holds the Skia image (for Compose) and lazily the RGBA bytes (for GL uploads,
 * matching Android's ARGB_8888 memory layout R,G,B,A).
 */
class Bitmap internal constructor(
    val width: Int,
    val height: Int,
    internal var skia: Image?,
    internal var rgba: ByteArray?,
    private val premultiplied: Boolean
) {
    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }
    enum class CompressFormat { JPEG, PNG, WEBP }

    val config: Config get() = Config.ARGB_8888
    var isRecycled: Boolean = false
        private set
    val byteCount: Int get() = width * height * 4

    fun getWidth(): Int = width
    fun getHeight(): Int = height
    fun hasAlpha(): Boolean = true

    internal fun pixels(): ByteArray {
        rgba?.let { return it }
        val img = skia ?: return ByteArray(width * height * 4)
        val info = ImageInfo(width, height, ColorType.RGBA_8888, if (premultiplied) ColorAlphaType.PREMUL else ColorAlphaType.UNPREMUL)
        val bmp = org.jetbrains.skia.Bitmap()
        bmp.allocPixels(info)
        img.readPixels(bmp, 0, 0)
        val bytes = bmp.readPixels(info, width * 4, 0, 0) ?: ByteArray(width * height * 4)
        bmp.close()
        rgba = bytes
        return bytes
    }

    fun copyPixelsToBuffer(dst: Buffer) {
        val bb = dst as ByteBuffer
        bb.put(pixels())
    }

    fun copyPixelsFromBuffer(src: Buffer) {
        val bb = src as ByteBuffer
        val out = ByteArray(width * height * 4)
        bb.get(out, 0, minOf(out.size, bb.remaining()))
        rgba = out
        skia = null
    }

    fun getPixel(x: Int, y: Int): Int {
        val p = pixels()
        val i = (y * width + x) * 4
        return ((p[i + 3].toInt() and 0xFF) shl 24) or ((p[i].toInt() and 0xFF) shl 16) or ((p[i + 1].toInt() and 0xFF) shl 8) or (p[i + 2].toInt() and 0xFF)
    }

    /** Compose view of this bitmap. */
    internal fun toSkiaImage(): Image? {
        skia?.let { return it }
        val px = rgba ?: return null
        val info = ImageInfo(width, height, ColorType.RGBA_8888, if (premultiplied) ColorAlphaType.PREMUL else ColorAlphaType.UNPREMUL)
        return Image.makeRaster(info, px, width * 4)
    }

    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean = false

    fun recycle() {
        isRecycled = true
        rgba = null
    }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap =
            Bitmap(width, height, null, ByteArray(width * height * 4), true)
        fun createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int, m: Matrix?, filter: Boolean): Bitmap =
            Bitmap(width, height, src.skia, src.rgba, src.premultiplied)
        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap = src

        internal fun decode(bytes: ByteArray, premultiplied: Boolean): Bitmap? {
            val img = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
            return Bitmap(img.width, img.height, img, null, premultiplied)
        }
    }
}

object BitmapFactory {
    class Options {
        var inScaled: Boolean = true
        var inSampleSize: Int = 1
        var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
        var inPremultiplied: Boolean = true
        var inJustDecodeBounds: Boolean = false
        var inMutable: Boolean = false
        var outWidth: Int = 0
        var outHeight: Int = 0
    }

    fun decodeStream(input: InputStream?, outPadding: Rect? = null, opts: Options? = null): Bitmap? {
        val bytes = input?.readBytes() ?: return null
        return decodeByteArray(bytes, 0, bytes.size, opts)
    }
    fun decodeStream(input: InputStream?): Bitmap? = decodeStream(input, null, null)

    fun decodeFile(path: String, opts: Options? = null): Bitmap? {
        val bytes = runCatching { java.io.File(path).readBytes() }.getOrNull() ?: return null
        return decodeByteArray(bytes, 0, bytes.size, opts)
    }

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options? = null): Bitmap? {
        val b = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        val bmp = Bitmap.decode(b, opts?.inPremultiplied ?: true) ?: return null
        opts?.outWidth = bmp.width
        opts?.outHeight = bmp.height
        return if (opts?.inJustDecodeBounds == true) null else bmp
    }
}
