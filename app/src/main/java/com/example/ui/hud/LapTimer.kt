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
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Part F2, F4, F6:
 * - Timer: 15 sp tabular; best lap 11 sp below.
 * - 45% black glass (#73000000) with 1 dp white 12% border (#1FFFFFFF) and drop shadow on text.
 */
@Composable
fun LapTimer(
    raceTimeMs: Long,
    bestLapMs: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.testTag("hud_lap_timer"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatRaceTime(raceTimeMs),
            style = DRType.NumL.copy(shadow = HudTextShadow),
            color = TextPrimary,
            maxLines = 1
        )
        if (bestLapMs > 0L) {
            Text(
                text = "BEST ${formatRaceTime(bestLapMs)}",
                style = DRType.NumS.copy(shadow = HudTextShadow),
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * Part C4: Times formatted `m:ss.mmm` (or `mm:ss.mmm` when >= 10 minutes).
 */
fun formatRaceTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val minutes = safe / 60000L
    val seconds = (safe % 60000L) / 1000L
    val millis = safe % 1000L
    return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
}
