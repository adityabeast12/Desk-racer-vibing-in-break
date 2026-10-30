package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.RacerStanding
import com.example.ui.hud.formatRaceTime
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface

@Composable
fun ResultsScreen(
    trackName: String,
    standings: List<RacerStanding>,
    isMultiplayer: Boolean,
    isHost: Boolean,
    onRematch: () -> Unit,
    onBackToLobby: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC090D16))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("results_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Column: Podium 1st / 2nd / 3rd + Action Buttons
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "RACE RESULTS",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = RulerYellow
                    )
                    Text(
                        text = trackName,
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SkyCyan
                    )
                }

                // Visual 3-Step Podium (2nd, 1st, 3rd)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val first = standings.getOrNull(0)
                    val second = standings.getOrNull(1)
                    val third = standings.getOrNull(2)

                    PodiumStep(
                        place = "2ND",
                        racer = second,
                        stepHeight = 82.dp,
                        medalColor = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )
                    PodiumStep(
                        place = "1ST",
                        racer = first,
                        stepHeight = 110.dp,
                        medalColor = RulerYellow,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumStep(
                        place = "3RD",
                        racer = third,
                        stepHeight = 66.dp,
                        medalColor = DriftAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bottom Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRematch,
                        colors = ButtonDefaults.buttonColors(containerColor = HostEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_results_rematch")
                    ) {
                        Text(
                            text = if (!isMultiplayer || isHost) "REMATCH" else "READY FOR REMATCH",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isMultiplayer) {
                            Button(
                                onClick = onBackToLobby,
                                colors = ButtonDefaults.buttonColors(containerColor = JoinBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_results_lobby")
                            ) {
                                Text("LOBBY", fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = onMainMenu,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                                .testTag("btn_results_menu")
                        ) {
                            Text("MAIN MENU", fontFamily = OrbitronFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Right Column: Full Standings Table (Position, Name, Total Time, Best Lap, NEW BEST! Badge)
            Surface(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = SlateAbyss,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(standings) { r ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (r.isLocal) SkyCyan.copy(alpha = 0.18f) else SlateSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (r.isLocal) SkyCyan else Color(0xFF334155)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "#${r.position}",
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (r.position == 1) RulerYellow else Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(r.colorHex), CircleShape)
                                    )
                                    Text(
                                        text = r.name,
                                        fontFamily = OrbitronFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    if (r.isNewRecord) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = RulerYellow
                                        ) {
                                            Text(
                                                text = "NEW BEST!",
                                                fontFamily = OrbitronFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = SlateAbyss,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatRaceTime(r.totalTimeMs),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    if (r.bestLapMs > 0L) {
                                        Text(
                                            text = "BEST ${formatRaceTime(r.bestLapMs)}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = SkyCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumStep(
    place: String,
    racer: RacerStanding?,
    stepHeight: androidx.compose.ui.unit.Dp,
    medalColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (racer != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color(racer.colorHex), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )
            Text(
                text = racer.name,
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(stepHeight),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = medalColor.copy(alpha = 0.24f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, medalColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = place,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = medalColor
                )
            }
        }
    }
}
