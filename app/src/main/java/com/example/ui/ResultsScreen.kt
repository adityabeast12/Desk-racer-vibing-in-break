package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.GameMode
import com.example.game.RacerStanding
import com.example.ui.hud.formatRaceTime
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.DRType
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.HostEmerald
import com.example.ui.theme.JoinBlue
import com.example.ui.theme.Poppins
import com.example.ui.theme.RulerYellow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SlateAbyss
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface

/**
 * Part C4:
 * - DNF racers placed after finishers, ordered by `raceDistance`.
 * - Times formatted `m:ss.mmm` or `"DNF"`.
 * - Best lap marked with `★` per racer; `"NEW BEST"` only on a personal record.
 */
@Composable
fun ResultsScreen(
    trackName: String,
    standings: List<RacerStanding>,
    isMultiplayer: Boolean,
    isHost: Boolean,
    gameMode: GameMode = GameMode.QUICK_RACE,
    cupRaceIndex: Int = 0,
    coinsEarnedThisRace: Int = 60,
    xpEarnedThisRace: Int = 100,
    onWatchAdDoubleCoins: (() -> Unit)? = null,
    onRematch: () -> Unit,
    onNextRace: () -> Unit = onRematch,
    onBackToLobby: () -> Unit,
    onMainMenu: () -> Unit
) {
    val isGrandPrix = gameMode == GameMode.GRAND_PRIX
    val isCupFinale = isGrandPrix && cupRaceIndex >= 3
    var doubledCoinsClaimed by remember { mutableStateOf(false) }

    // Ensure finishers come first by position, followed by DNF racers ordered by position (which host sorted by raceDistance)
    val orderedStandings = remember(standings) {
        val finishers = standings.filter { !it.isDnf }.sortedBy { it.position }
        val dnfs = standings.filter { it.isDnf }.sortedBy { it.position }
        finishers + dnfs
    }
    val fastestLapOverallMs = remember(orderedStandings) {
        orderedStandings.mapNotNull { it.bestLapMs.takeIf { ms -> ms > 0L } }.minOrNull() ?: 0L
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90B1020))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("results_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Column: Podium 1st / 2nd / 3rd + Coins/XP Summary + Action Buttons
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isCupFinale) "CUP TROPHY CEREMONY" else "RACE RESULTS",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = RulerYellow
                        )
                        Text(
                            text = if (isGrandPrix) "$trackName • RACE ${cupRaceIndex + 1}/4" else trackName,
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = SkyCyan
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = SlateSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RulerYellow)
                    ) {
                        val effectiveCoins = if (doubledCoinsClaimed) coinsEarnedThisRace * 2 else coinsEarnedThisRace
                        Text(
                            text = "+$effectiveCoins COINS  •  +$xpEarnedThisRace XP",
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = RulerYellow,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Visual 3-Step Podium (2nd, 1st, 3rd)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val first = orderedStandings.getOrNull(0)
                    val second = orderedStandings.getOrNull(1)
                    val third = orderedStandings.getOrNull(2)

                    PodiumStep(
                        place = "2ND",
                        racer = second,
                        stepHeight = 76.dp,
                        medalColor = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )
                    PodiumStep(
                        place = "1ST",
                        racer = first,
                        stepHeight = 102.dp,
                        medalColor = RulerYellow,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumStep(
                        place = "3RD",
                        racer = third,
                        stepHeight = 62.dp,
                        medalColor = DriftAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bottom Action Buttons + Solo Opt-In 2x Coins Rewarded Button
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!isMultiplayer && onWatchAdDoubleCoins != null && !doubledCoinsClaimed) {
                        Button(
                            onClick = {
                                doubledCoinsClaimed = true
                                onWatchAdDoubleCoins()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RulerYellow),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_results_double_coins")
                        ) {
                            Text(
                                text = "WATCH CLIP FOR 2× COINS (+$coinsEarnedThisRace BONUS)",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = SlateAbyss
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRematch,
                            colors = ButtonDefaults.buttonColors(containerColor = HostEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_results_rematch")
                        ) {
                            Text(
                                text = if (!isMultiplayer || isHost) "REMATCH" else "READY",
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        if (!isMultiplayer || isHost) {
                            Button(
                                onClick = onNextRace,
                                colors = ButtonDefaults.buttonColors(containerColor = DriftAmber),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("btn_results_next_race")
                            ) {
                                Text(
                                    text = if (isGrandPrix && !isCupFinale) "NEXT CUP RACE ▶" else "NEXT TRACK ▶",
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = SlateAbyss
                                )
                            }
                        }
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
                                    .height(42.dp)
                                    .testTag("btn_results_lobby")
                            ) {
                                Text("LOBBY", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                        Button(
                            onClick = onMainMenu,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                                .testTag("btn_results_menu")
                        ) {
                            Text("MAIN MENU", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Right Column: Full 8-Racer Standings Table (Tabular Poppins tnum)
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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(orderedStandings) { r ->
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "#${r.position}",
                                        style = DRType.TabularTimer,
                                        color = if (r.position == 1) RulerYellow else Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(r.colorHex), CircleShape)
                                    )
                                    Text(
                                        text = r.name,
                                        fontFamily = Poppins,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    // Part C4: "NEW BEST" only on a personal record
                                    if (r.isNewRecord) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = RulerYellow
                                        ) {
                                            Text(
                                                text = "NEW BEST",
                                                fontFamily = Poppins,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp,
                                                color = SlateAbyss,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (r.isDnf) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ArcadeCrimson
                                        ) {
                                            Text(
                                                text = "DNF",
                                                fontFamily = Poppins,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (r.isDnf) "DNF" else formatRaceTime(r.totalTimeMs),
                                        style = DRType.TabularTimer,
                                        fontSize = 12.sp,
                                        color = if (r.isDnf) ArcadeCrimson else Color.White
                                    )
                                    // Part C4: Best lap marked with ★ per racer
                                    val bestLapPart = if (r.bestLapMs > 0L) {
                                        "★ ${formatRaceTime(r.bestLapMs)}"
                                    } else {
                                        "★ --:--.---"
                                    }
                                    val subText = if (isGrandPrix) {
                                        "$bestLapPart  •  +${r.cupPointsEarned} PTS (${r.cupTotalPoints})"
                                    } else {
                                        bestLapPart
                                    }
                                    val isOverallFastest = fastestLapOverallMs > 0L && r.bestLapMs == fastestLapOverallMs
                                    Text(
                                        text = subText,
                                        style = DRType.MicroTag,
                                        color = if (isOverallFastest || isGrandPrix) RulerYellow else SkyCyan
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
                fontFamily = Poppins,
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
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = medalColor
                )
            }
        }
    }
}
