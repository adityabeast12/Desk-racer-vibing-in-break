package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

@Composable
fun PositionBadge(
    position: Int,
    totalRacers: Int,
    lap: Int,
    totalLaps: Int,
    modifier: Modifier = Modifier
) {
    val rankColor = when (position) {
        1 -> Color(0xFFFACC15) // Gold
        2 -> Color(0xFFE2E8F0) // Silver
        3 -> Color(0xFFF97316) // Bronze
        else -> Color(0xFF38BDF8)
    }
    val suffix = when (position) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }

    Column(
        modifier = modifier
            .background(Color(0xB30F172A), RoundedCornerShape(14.dp))
            .border(1.5.dp, rankColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("hud_position_badge"),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$position$suffix",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = rankColor
            )
            Text(
                text = " /$totalRacers",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Text(
            text = "LAP $lap/$totalLaps",
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier.testTag("hud_lap_counter")
        )
    }
}
