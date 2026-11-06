@file:Suppress("unused")

package androidx.compose.ui.viewinterop

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Android's AndroidView. On iOS the only view the game embeds is its GLSurfaceView, which
 * drshim.GlHost places *behind* the transparent Compose layer; here we just create it once.
 */
@Composable
fun <T : View> AndroidView(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {}
) {
    val ctx = LocalContext.current
    val view = remember { factory(ctx) }
    SideEffect { update(view) }
    Box(modifier)
}
