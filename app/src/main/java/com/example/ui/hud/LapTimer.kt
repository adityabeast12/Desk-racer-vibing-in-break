package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkSurface
import com.example.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun LapTimer(
    raceTimeMs: Long,
    bestLapMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(min = 108.dp, max = 188.dp)
            .background(InkSurface.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
            .border(1.dp, InkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("hud_lap_timer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatRaceTime(raceTimeMs),
            style = DRType.TabularSubtitle.copy(fontSize = 16.sp, fontWeight = FontWeight.W800),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (bestLapMs > 0L) {
            Text(
                text = "BEST ${formatRaceTime(bestLapMs)}",
                style = DRType.Caption.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                    fontFeatureSettings = "tnum"
                ),
                color = BoostCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatRaceTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val minutes = safe / 60000L
    val seconds = (safe % 60000L) / 1000L
    val millis = safe % 1000L
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
}
