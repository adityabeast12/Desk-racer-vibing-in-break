@file:Suppress("unused")

package androidx.compose.ui.graphics

fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap {
    val img = toSkiaImage() ?: return ImageBitmap(1, 1)
    return img.toComposeImageBitmap()
}
