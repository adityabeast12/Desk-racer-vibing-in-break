@file:Suppress("unused")

package androidx.activity.compose

import androidx.compose.runtime.Composable

/** iPhones have no system back button; screens keep their on-screen back controls. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
}
