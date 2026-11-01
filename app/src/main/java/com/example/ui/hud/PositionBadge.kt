package com.example.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BoostCyan
import com.example.ui.theme.DRType
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.InkBorder
import com.example.ui.theme.InkSurface
import com.example.ui.theme.LampAmber
import com.example.ui.theme.SuccessLime
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PositionBadge(
    position: Int,
    totalRacers: Int,
    lap: Int,
    totalLaps: Int,
    coinsHeld: Int = 0,
    positionDeltaChip: String? = null,
    balloonsRemaining: Int = -1,
    modifier: Modifier = Modifier
) {
    val rankColor = when (position) {
        1 -> LampAmber
        2 -> Color(0xFFE2E8F0)
        3 -> Color(0xFFF97316)
        else -> BoostCyan
    }
    val suffix = when (position) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }

    Column(
        modifier = modifier
            .widthIn(min = 96.dp, max = 186.dp)
            .background(InkSurface.copy(alpha = 0.88f), RoundedCornerShape(14.dp))
            .border(1.5.dp, rankColor.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("hud_position_badge"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "$position$suffix",
                style = DRType.HudNumber.copy(fontSize = 26.sp, lineHeight = 28.sp),
                color = rankColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "/$totalRacers",
                style = DRType.TabularLabel,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            if (!positionDeltaChip.isNullOrBlank()) {
                val isGain = positionDeltaChip.startsWith("▲")
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isGain) SuccessLime else DangerCoral,
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Text(
                        text = positionDeltaChip,
                        style = DRType.Caption.copy(
                            fontWeight = FontWeight.W800,
                            fontFeatureSettings = "tnum"
                        ),
                        color = Color(0xFF0B1020),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (balloonsRemaining >= 0) "🎈 $balloonsRemaining" else "LAP $lap/$totalLaps",
                style = DRType.TabularLabel.copy(fontSize = 12.sp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hud_lap_counter")
            )
            Text(
                text = "🪙 ${coinsHeld.coerceIn(0, 10)}",
                style = DRType.TabularLabel.copy(fontSize = 12.sp),
                color = LampAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hud_coin_counter")
            )
        }
    }
}
