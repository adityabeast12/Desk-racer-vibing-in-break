package com.example.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DRToast
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType

/** Picks an icon + semantic colour for a free-text game message. */
private fun toastStyle(message: String): Pair<ImageVector, Color> {
    val m = message.lowercase()
    return when {
        "join" in m -> Icons.Rounded.PersonAdd to DR.Ok
        "left" in m || "kick" in m -> Icons.Rounded.PersonRemove to DR.Amber
        "lost" in m || "disconnect" in m || "retry" in m || "host" in m && "gone" in m -> Icons.Rounded.WifiOff to DR.Bad
        "hit" in m || "spun" in m -> Icons.Rounded.Bolt to DR.You
        "finish" in m -> Icons.Rounded.Flag to DR.Amber
        else -> Icons.Rounded.Info to DR.Amber
    }
}

/** Menu-level toast (top-centre, one at a time). */
@Composable
fun Toasts(
    message: String,
    modifier: Modifier = Modifier
) {
    val (icon, color) = toastStyle(message)
    DRToast(text = message, icon = icon, color = color, modifier = modifier)
}

/** In-race event feed under the position block: compact strip with a coloured edge. */
@Composable
fun HudEventFeed(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = slideInHorizontally(tween(180)) { -it / 3 } + fadeIn(tween(140)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        val text = message.orEmpty()
        val (icon, color) = toastStyle(text)
        Row(
            modifier = Modifier
                .padding(start = 4.dp)
                .clip(DRShape.Control)
                .background(DR.HudPlate2)
                .drawBehind { drawRect(color, size = Size(2.dp.toPx(), size.height)) }
                .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                .testTag("hud_event_feed"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, style = DRType.BodyStrong.copy(fontSize = 12.sp, shadow = HudTextShadow), color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
