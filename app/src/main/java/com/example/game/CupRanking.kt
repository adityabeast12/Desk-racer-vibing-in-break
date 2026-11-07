package com.example.game

/** Final Grand Prix table: total cup points, ties broken by the better finish in the last race. */
data class CupStanding(
    val rank: Int,
    val standing: RacerStanding
) {
    val points: Int get() = standing.cupTotalPoints
}

object CupRanking {
    fun finalOrder(lastRace: List<RacerStanding>): List<CupStanding> =
        lastRace
            .sortedWith(compareByDescending<RacerStanding> { it.cupTotalPoints }.thenBy { it.position })
            .mapIndexed { i, s -> CupStanding(rank = i + 1, standing = s) }

    fun localRank(lastRace: List<RacerStanding>): Int =
        finalOrder(lastRace).firstOrNull { it.standing.isLocal }?.rank ?: 0

    /** One-off coin bonus for a cup podium (on top of the per-race rewards). */
    fun podiumBonusCoins(rank: Int): Int = when (rank) {
        1 -> 300
        2 -> 200
        3 -> 120
        else -> 0
    }
}
