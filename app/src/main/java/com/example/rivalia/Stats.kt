package com.example.rivalia

/** Estadísticas del cara a cara entre dos jugadores. */
data class HeadToHead(
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val currentStreak: Int,
    val lastMatches: List<Match>
) {
    val total: Int get() = wins + losses + draws
}

data class Achievement(val title: String, val description: String, val unlocked: Boolean)

/** Cálculos de estadísticas a partir del historial de partidas (lógica pura). */
object Stats {

    fun headToHead(me: String, rival: String, matches: List<Match>): HeadToHead {
        val between = matches
            .filter { !it.pending && it.involves(me) && it.involves(rival) && me != rival }
            .sortedByDescending { it.playedAt }
        var wins = 0
        var losses = 0
        var draws = 0
        between.forEach {
            when (it.winnerId()) {
                me -> wins++
                null -> draws++
                else -> losses++
            }
        }
        // Racha actual: positiva si gano seguidas, negativa si pierdo seguidas
        var streak = 0
        for (m in between) {
            val w = m.winnerId()
            if (w == null) break
            val gano = w == me
            if (streak == 0) streak = if (gano) 1 else -1
            else if (gano && streak > 0) streak++
            else if (!gano && streak < 0) streak--
            else break
        }
        return HeadToHead(wins, losses, draws, streak, between.take(10))
    }

    /** Evolución del ELO de un jugador en un juego (puntos después de cada partida). */
    fun eloHistory(uid: String, gameKey: String, season: Int, matches: List<Match>): List<Int> {
        val mine = matches
            .filter { !it.pending && it.gameKey == gameKey && it.season == season && it.involves(uid) }
            .sortedBy { it.playedAt }
        if (mine.isEmpty()) return emptyList()
        val points = mutableListOf<Int>()
        val first = mine.first()
        points += if (first.playerA == uid) first.eloBeforeA else first.eloBeforeB
        mine.forEach {
            points += if (it.playerA == uid) it.eloBeforeA + it.deltaA else it.eloBeforeB + it.deltaB
        }
        return points
    }

    fun achievements(uid: String, matches: List<Match>, ratings: List<Rating>, trophies: List<Trophy>): List<Achievement> {
        val mine = matches.filter { !it.pending && it.involves(uid) }
        val wins = mine.count { it.winnerId() == uid }
        val bestStreak = ratings.filter { it.uid == uid }.maxOfOrNull { it.bestStreak } ?: 0
        val maxElo = ratings.filter { it.uid == uid }.maxOfOrNull { it.elo } ?: Elo.INITIAL
        val games = mine.map { it.gameKey }.toSet().size
        val myTrophies = trophies.count { it.uid == uid }
        return listOf(
            Achievement("Primera sangre", "Gana tu primera partida", wins >= 1),
            Achievement("Veterano", "Juega 25 partidas", mine.size >= 25),
            Achievement("Imparable", "Consigue una racha de 5 victorias", bestStreak >= 5),
            Achievement("Todoterreno", "Juega a 3 juegos distintos", games >= 3),
            Achievement("Élite", "Llega a 1.100 de ELO en un juego", maxElo >= 1100),
            Achievement("Campeón", "Gana un torneo o una temporada", myTrophies >= 1)
        )
    }
}
