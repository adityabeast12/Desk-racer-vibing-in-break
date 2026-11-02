package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.GameMode
import com.example.game.RacerStanding
import com.example.ui.components.DRBadge
import com.example.ui.components.DRBadgeKind
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRCoin
import com.example.ui.components.DRKerbStrip
import com.example.ui.components.DRLabel
import com.example.ui.components.DRMarkerTag
import com.example.ui.components.DRPanel
import com.example.ui.components.DRProgress
import com.example.ui.components.KartThumb
import com.example.ui.hud.formatRaceTime
import com.example.ui.theme.DR
import com.example.ui.theme.DRType
import kotlinx.coroutines.delay

private fun ordinal(p: Int): String = when {
    p % 100 in 11..13 -> "${p}TH"
    p % 10 == 1 -> "${p}ST"
    p % 10 == 2 -> "${p}ND"
    p % 10 == 3 -> "${p}RD"
    else -> "${p}TH"
}

private fun gapText(r: RacerStanding, leaderMs: Long): String = when {
    r.isDnf -> ""
    r.position == 1 || leaderMs <= 0L -> "—"
    else -> {
        val d = (r.totalTimeMs - leaderMs).coerceAtLeast(0L)
        String.format(java.util.Locale.US, "+%d.%03d", d / 1000L, d % 1000L)
    }
}

/**
 * v6 Results: kerb strip on top, Display headline, standings table (rows enter 6th→1st),
 * rewards panel that counts up, actions bottom-right (one primary).
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

    val ordered = remember(standings) {
        standings.filter { !it.isDnf }.sortedBy { it.position } + standings.filter { it.isDnf }.sortedBy { it.position }
    }
    val leaderMs = ordered.firstOrNull { !it.isDnf }?.totalTimeMs ?: 0L
    val fastestLap = ordered.mapNotNull { it.bestLapMs.takeIf { ms -> ms > 0L } }.minOrNull() ?: 0L
    val me = ordered.firstOrNull { it.isLocal }
    val myPos = me?.position ?: 0

    var revealed by remember { mutableStateOf(0) }
    LaunchedEffect(ordered.size) {
        revealed = 0
        for (i in 1..ordered.size) {
            delay(60)
            revealed = i
        }
    }
    val coinsTarget = if (doubledCoinsClaimed) coinsEarnedThisRace * 2 else coinsEarnedThisRace
    val coinsShown by animateIntAsState(if (revealed >= ordered.size) coinsTarget else 0, tween(500), label = "coins")
    val xpShown by animateIntAsState(if (revealed >= ordered.size) xpEarnedThisRace else 0, tween(600), label = "xp")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DR.Bg.copy(alpha = 0.90f))
            .testTag("results_screen")
    ) {
        DRKerbStrip(Modifier.align(Alignment.TopCenter))
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Left: headline + table
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    DRLabel(
                        if (isGrandPrix) "$trackName · Race ${cupRaceIndex + 1}/4" else trackName,
                        color = DR.Amber
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = when {
                                isCupFinale -> "CUP COMPLETE"
                                myPos == 1 -> "WINNER!"
                                myPos > 0 -> "${ordinal(myPos)} PLACE"
                                else -> "RESULTS"
                            },
                            style = DRType.Display.copy(fontSize = if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 400) 32.sp else 40.sp, lineHeight = 40.sp),
                            color = if (myPos == 1) DR.Gold else DR.TextHi,
                            maxLines = 1
                        )
                        if (me != null && me.isNewRecord && me.bestLapMs > 0L) {
                            DRMarkerTag("New best ${formatRaceTime(me.bestLapMs)}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DRPanel(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        padding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DRLabel("Pos", color = DR.TextLo, modifier = Modifier.width(30.dp))
                            DRLabel("Driver", color = DR.TextLo, modifier = Modifier.weight(1f).padding(start = if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 400) 0.dp else 50.dp))
                            DRLabel(if (isGrandPrix) "Pts" else "Best lap", color = DR.TextLo, modifier = Modifier.width(72.dp))
                            DRLabel("Time", color = DR.TextLo, modifier = Modifier.width(76.dp))
                            DRLabel("Gap", color = DR.TextLo, modifier = Modifier.width(56.dp))
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(ordered) { idx, r ->
                                // rows reveal from last place up to the winner
                                val visible = revealed >= ordered.size - idx
                                AnimatedVisibility(visible = visible, enter = slideInHorizontally(tween(180)) { it / 8 } + fadeIn(tween(180))) {
                                    ResultRow(r, leaderMs, fastestLap, isGrandPrix)
                                }
                            }
                        }
                    }
                }

                // Right: rewards + actions
                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    Spacer(Modifier.height(54.dp))
                    DRPanel(Modifier.fillMaxWidth()) {
                        DRLabel("Rewards")
                        RewardRow("Finish · ${if (myPos > 0) ordinal(myPos).lowercase() else "—"}", "+$coinsShown")
                        Spacer(Modifier.height(12.dp))
                        DRProgress(value = (xpShown / 200f).coerceIn(0f, 1f), label = "Desk Pass XP", trailing = "+$xpShown XP")
                    }
                    if (!isMultiplayer && onWatchAdDoubleCoins != null && !doubledCoinsClaimed) {
                        Spacer(Modifier.height(8.dp))
                        DRButton(
                            text = "Watch clip · 2× coins",
                            icon = Icons.Rounded.OndemandVideo,
                            variant = DRButtonVariant.SECONDARY,
                            onClick = {
                                doubledCoinsClaimed = true
                                onWatchAdDoubleCoins()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "btn_results_double_coins"
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (isMultiplayer) {
                            DRButton("Lobby", onClick = onBackToLobby, variant = DRButtonVariant.GHOST, icon = Icons.Rounded.Groups, testTag = "btn_results_lobby")
                        } else {
                            DRButton("Menu", onClick = onMainMenu, variant = DRButtonVariant.GHOST, icon = Icons.Rounded.Home, testTag = "btn_results_menu")
                        }
                        DRButton(
                            text = if (!isMultiplayer || isHost) "Rematch" else "Ready",
                            onClick = onRematch,
                            variant = DRButtonVariant.SECONDARY,
                            icon = Icons.Rounded.Replay,
                            testTag = "btn_results_rematch"
                        )
                    }
                    if (!isMultiplayer || isHost) {
                        Spacer(Modifier.height(8.dp))
                        DRButton(
                            text = if (isGrandPrix && !isCupFinale) "Next cup race" else "Next track",
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            onClick = onNextRace,
                            size = DRButtonSize.L,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "btn_results_next_race"
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        DRButton(
                            text = "Main menu",
                            onClick = onMainMenu,
                            variant = DRButtonVariant.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "btn_results_menu_client"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .drawBehind { drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DRCoin(18.dp)
        Text(label, style = DRType.BodyStrong.copy(fontSize = 13.sp), color = DR.TextHi, modifier = Modifier.weight(1f))
        Text(value, style = DRType.NumL, color = DR.Amber)
    }
}

@Composable
private fun ResultRow(r: RacerStanding, leaderMs: Long, fastestLap: Long, isGrandPrix: Boolean) {
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 400
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 34.dp else 40.dp)
            .background(if (r.isLocal) DR.YouWash else Color.Transparent)
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                if (r.isLocal) drawRect(DR.You, size = Size(3.dp.toPx(), size.height))
            }
            .padding(horizontal = 14.dp)
            .testTag("result_row_${r.slotIndex}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("${r.position}", style = DRType.Title.copy(fontSize = 22.sp), color = DR.podium(r.position), modifier = Modifier.width(30.dp), textAlign = TextAlign.Start)
        if (!compact) KartThumb(r.bodyType, Modifier.size(width = 42.dp, height = 28.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.name, style = DRType.BodyStrong, color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (r.isLocal) DRBadge("You", DRBadgeKind.YOU)
            if (r.isDnf) DRBadge("DNF", DRBadgeKind.MUTED)
        }
        Text(
            text = when {
                isGrandPrix -> "+${r.cupPointsEarned} (${r.cupTotalPoints})"
                r.bestLapMs > 0L -> formatRaceTime(r.bestLapMs)
                else -> "—"
            },
            style = DRType.NumS.copy(fontSize = 12.5.sp),
            color = if (!isGrandPrix && r.bestLapMs > 0L && r.bestLapMs == fastestLap) DR.Amber else DR.TextMid,
            modifier = Modifier.width(72.dp),
            maxLines = 1
        )
        Text(
            text = if (r.isDnf) "DNF" else formatRaceTime(r.totalTimeMs),
            style = DRType.NumM,
            color = if (r.isDnf) DR.TextLo else DR.TextHi,
            modifier = Modifier.width(76.dp),
            maxLines = 1
        )
        Text(gapText(r, leaderMs), style = DRType.NumS, color = DR.TextLo, modifier = Modifier.width(56.dp), maxLines = 1)
    }
}
