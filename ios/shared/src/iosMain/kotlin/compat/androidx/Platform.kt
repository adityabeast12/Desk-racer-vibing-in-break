@file:Suppress("unused")

package androidx.compose.ui.platform

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf<Context> { Context.app }
val LocalView = staticCompositionLocalOf<View> { View(Context.app) }
val LocalConfiguration = compositionLocalOf<Configuration> { drshim.Platform.configuration }
