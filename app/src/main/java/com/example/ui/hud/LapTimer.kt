package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.SkyCyan
import java.util.Locale

@Composable
fun LapTimer(
    raceTimeMs: Long,
    bestLapMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xB30F172A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("hud_lap_timer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatRaceTime(raceTimeMs),
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
        if (bestLapMs > 0L) {
            Text(
                text = "BEST ${formatRaceTime(bestLapMs)}",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = SkyCyan
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
