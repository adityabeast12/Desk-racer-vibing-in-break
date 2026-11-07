package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.CupRanking
import com.example.game.CupStanding
import com.example.game.CupType
import com.example.game.RacerStanding
import com.example.ui.components.DRBadge
import com.example.ui.components.DRBadgeKind
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonSize
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRCoin
import com.example.ui.components.DRKerbStrip
import com.example.ui.components.DRLabel
import com.example.ui.components.DRPanel
import com.example.ui.components.KartThumb
import com.example.ui.theme.DR
import com.example.ui.theme.DRShape
import com.example.ui.theme.DRType
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private fun placeWord(rank: Int): String = when (rank) {
    1 -> "1ST"
    2 -> "2ND"
    3 -> "3RD"
    else -> "${rank}TH"
}

private fun trophyColor(rank: Int): Color = when (rank) {
    1 -> DR.Gold
    2 -> DR.Silver
    3 -> DR.Bronze
    else -> DR.TextLo
}

/**
 * Grand Prix finale: the cup podium. Ranks everyone by total cup points, raises the top three on
 * a podium, awards the trophy and the podium bonus, and ends the cup — the old flow restarted
 * the same cup from "Next track", so a finished cup never actually ended.
 */
@Composable
fun CupPodiumScreen(
    cup: CupType,
    lastRaceStandings: List<RacerStanding>,
    bonusCoins: Int,
    nextCup: CupType?,
    onMainMenu: () -> Unit,
    onRaceCupAgain: () -> Unit,
    onNextCup: () -> Unit
) {
    val table: List<CupStanding> = remember(lastRaceStandings) { CupRanking.finalOrder(lastRaceStandings) }
    val me = table.firstOrNull { it.standing.isLocal }
    val myRank = me?.rank ?: 0
    val onPodium = myRank in 1..3

    val rise = remember { Animatable(0f) }
    val trophyPop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        trophyPop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DR.Bg.copy(alpha = 0.94f))
            .testTag("cup_podium_screen")
    ) {
        if (onPodium) Confetti(Modifier.fillMaxSize(), intensity = if (myRank == 1) 1f else 0.55f)
        DRKerbStrip(Modifier.align(Alignment.TopCenter))
        Row(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left: headline + podium
            Column(Modifier.weight(1f).fillMaxHeight()) {
                DRLabel("${cup.title} · Final standings", color = DR.Amber)
                Text(
                    text = when (myRank) {
                        1 -> "CUP CHAMPION!"
                        2 -> "SILVER TROPHY"
                        3 -> "BRONZE TROPHY"
                        0 -> "CUP COMPLETE"
                        else -> "${placeWord(myRank)} OVERALL"
                    },
                    style = DRType.Display.copy(fontSize = 36.sp, lineHeight = 38.sp),
                    color = if (onPodium) trophyColor(myRank) else DR.TextHi,
                    maxLines = 1,
                    modifier = Modifier.testTag("cup_podium_headline")
                )
                Text(
                    text = if (onPodium) "You finished ${placeWord(myRank).lowercase()} in the ${cup.title.lowercase()} with ${me?.points ?: 0} points."
                    else "Top three takes home a trophy — you scored ${me?.points ?: 0} points.",
                    style = DRType.Body,
                    color = DR.TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val h = maxHeight
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // classic order: 2nd · 1st · 3rd
                        listOf(2, 1, 3).forEach { rank ->
                            val entry = table.getOrNull(rank - 1)
                            if (entry != null) {
                                PodiumColumn(
                                    entry = entry,
                                    stepHeight = h * when (rank) { 1 -> 0.38f; 2 -> 0.27f; else -> 0.19f },
                                    thumbHeight = (h * 0.2f).coerceIn(28.dp, 52.dp),
                                    rise = rise.value,
                                    trophyScale = if (entry.standing.isLocal) trophyPop.value else trophyPop.value * 0.7f,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Right: full table + rewards + actions
            Column(Modifier.width(290.dp).fillMaxHeight()) {
                DRPanel(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    padding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DRLabel("Pos", color = DR.TextLo, modifier = Modifier.width(30.dp))
                        DRLabel("Driver", color = DR.TextLo, modifier = Modifier.weight(1f))
                        DRLabel("Pts", color = DR.TextLo)
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(table) { e -> CupTableRow(e) }
                    }
                }
                if (bonusCoins > 0) {
                    Spacer(Modifier.height(8.dp))
                    DRPanel(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DRCoin(18.dp)
                            Text("Cup podium bonus", style = DRType.BodyStrong.copy(fontSize = 13.sp), color = DR.TextHi, modifier = Modifier.weight(1f))
                            Text("+$bonusCoins", style = DRType.NumL, color = DR.Amber, modifier = Modifier.testTag("cup_bonus_coins"))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    DRButton("Menu", onClick = onMainMenu, variant = DRButtonVariant.GHOST, icon = Icons.Rounded.Home, testTag = "btn_cup_menu")
                    DRButton(
                        "Race cup again",
                        onClick = onRaceCupAgain,
                        variant = if (nextCup != null) DRButtonVariant.SECONDARY else DRButtonVariant.PRIMARY,
                        icon = Icons.Rounded.Replay,
                        testTag = "btn_cup_again"
                    )
                }
                if (nextCup != null) {
                    Spacer(Modifier.height(8.dp))
                    DRButton(
                        text = "Next: ${nextCup.title.lowercase().replaceFirstChar { it.uppercase() }}",
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        onClick = onNextCup,
                        size = DRButtonSize.L,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "btn_cup_next"
                    )
                }
            }
        }
    }
}

@Composable
private fun PodiumColumn(entry: CupStanding, stepHeight: Dp, thumbHeight: Dp, rise: Float, trophyScale: Float, modifier: Modifier) {
    val rank = entry.rank
    val s = entry.standing
    val color = trophyColor(rank)
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Icon(
            Icons.Rounded.EmojiEvents,
            contentDescription = "${placeWord(rank)} trophy",
            tint = color,
            modifier = Modifier
                .size(if (rank == 1) thumbHeight * 0.75f else thumbHeight * 0.55f)
                .scale(trophyScale.coerceAtLeast(0f))
        )
        KartThumb(s.bodyType, Modifier.size(width = thumbHeight * 1.6f, height = thumbHeight).graphicsLayer { alpha = rise })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                s.name,
                style = DRType.BodyStrong,
                color = DR.TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (s.isLocal) DRBadge("You", DRBadgeKind.YOU)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(stepHeight * rise)
                .clip(DRShape.Card)
                .background(DR.S3)
                .border(if (s.isLocal) 2.dp else 1.dp, if (s.isLocal) DR.You else color.copy(alpha = 0.7f), DRShape.Card)
                .drawBehind { drawRect(color, size = Size(size.width, 4.dp.toPx())) }
                .testTag("podium_step_$rank"),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                Text(placeWord(rank), style = DRType.Title.copy(fontSize = if (rank == 1) 26.sp else 20.sp), color = color)
                Text("${entry.points} pts", style = DRType.NumS, color = DR.TextMid, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CupTableRow(e: CupStanding) {
    val s = e.standing
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(if (s.isLocal) DR.YouWash else Color.Transparent)
            .drawBehind {
                drawRect(DR.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                if (s.isLocal) drawRect(DR.You, size = Size(3.dp.toPx(), size.height))
            }
            .padding(horizontal = 12.dp)
            .testTag("cup_row_${s.slotIndex}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${e.rank}", style = DRType.Title.copy(fontSize = 18.sp), color = DR.podium(e.rank), modifier = Modifier.width(30.dp))
        Text(s.name, style = DRType.BodyStrong, color = DR.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${e.points}", style = DRType.NumM, color = if (e.rank <= 3) trophyColor(e.rank) else DR.TextMid)
    }
}

/** Falling paper confetti in the podium colours. */
@Composable
private fun Confetti(modifier: Modifier, intensity: Float) {
    val count = (70 * intensity).toInt().coerceAtLeast(20)
    val pieces = remember(count) {
        val r = Random(7)
        List(count) {
            floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f, r.nextFloat() * 360f, r.nextInt(5).toFloat())
        }
    }
    val colors = listOf(DR.Gold, DR.You, DR.Boost, DR.Amber, DR.Ok)
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "confetti_t"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pw = 7.dp.toPx()
        val ph = 11.dp.toPx()
        for (p in pieces) {
            val y = (((p[1] + t * p[2]) % 1f) * (h + ph * 2)) - ph
            val x = p[0] * w + sin((t * p[2] * 6f + p[0] * 10f) * PI.toFloat()) * 14.dp.toPx()
            rotate(p[3] + t * 720f * p[2], pivot = Offset(x, y)) {
                drawRect(colors[p[4].toInt()], topLeft = Offset(x - pw / 2, y - ph / 2), size = Size(pw, ph), alpha = 0.85f)
            }
        }
    }
}
