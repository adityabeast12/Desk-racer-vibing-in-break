@file:Suppress("unused")

package androidx.compose.ui.res

import androidx.compose.runtime.Composable

@Composable
fun stringResource(id: Int): String = drshim.Res.strings[id] ?: ""

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = drshim.jformat(drshim.Res.strings[id] ?: "", formatArgs)
