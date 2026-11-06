@file:Suppress("unused")

package androidx.compose.ui.text.font

/** Android's resource-id Font(): loads the TTF from the app bundle's fonts/ folder. */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font {
    val name = drshim.Res.fonts[resId] ?: "poppins_regular.ttf"
    val path = (platform.Foundation.NSBundle.mainBundle.resourcePath ?: "") + "/fonts/" + name
    val bytes = runCatching { java.io.File(path).readBytes() }.getOrNull() ?: ByteArray(0)
    return androidx.compose.ui.text.platform.Font(identity = name, data = bytes, weight = weight, style = style)
}
