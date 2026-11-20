package com.example.rivalia

import kotlin.random.Random

/** Fila de la clasificación de una liguilla. */
data class Standing(
    val uid: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int
) {
    val points: Int get() = won * 3 + drawn
}

/**
 * Generación y avance de cuadros de torneo (lógica pura, sin Android).
 *
 * Eliminatoria directa: los jugadores se sortean; si no son potencia de 2,
 * algunos pasan la primera ronda sin jugar ("bye").
 * Liguilla: todos contra todos (método del círculo).
 */
object Brackets {

    fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p *= 2
        return p
    }

    fun roundsFor(players: Int): Int {
        var r = 0
        var p = nextPowerOfTwo(players)
        while (p > 1) {
            p /= 2
            r++
        }
        return r
    }

    fun knockoutRoundName(round: Int, totalRounds: Int): String = when (totalRounds - round) {
        0 -> "Final"
        1 -> "Semifinales"
        2 -> "Cuartos de final"
        3 -> "Octavos de final"
        else -> "Ronda $round"
    }

    fun createKnockout(players: List<String>, random: Random = Random.Default): List<BracketMatch> {
        require(players.size >= 2) { "Hacen falta al menos 2 jugadores" }
        val shuffled = players.shuffled(random)
        val size = nextPowerOfTwo(shuffled.size)
        val rounds = roundsFor(shuffled.size)
        val half = size / 2

        var matches = mutableListOf<BracketMatch>()
        for (i in 0 until half) {
            val a = shuffled[i]
            val b = shuffled.getOrNull(i + half)
            matches += BracketMatch(
                slot = "r1m$i", round = 1, index = i, a = a, b = b,
                winner = if (b == null) a else null, bye = b == null
            )
        }
        for (r in 2..rounds) {
            val count = size shr r
            for (i in 0 until count) {
                matches += BracketMatch(slot = "r${r}m$i", round = r, index = i, a = null, b = null)
            }
        }
        // Los que pasan sin jugar avanzan directamente a la segunda ronda
        matches.filter { it.bye }.forEach { m ->
            matches = propagate(matches, m.round, m.index, m.winner!!, rounds).toMutableList()
        }
        return matches
    }

    fun createLeague(players: List<String>): List<BracketMatch> {
        require(players.size >= 2) { "Hacen falta al menos 2 jugadores" }
        val list: MutableList<String?> = players.toMutableList()
        if (list.size % 2 == 1) list += null // descansa quien juegue contra "null"
        val n = list.size
        val matches = mutableListOf<BracketMatch>()
        var counter = 0
        for (round in 1 until n) {
            for (i in 0 until n / 2) {
                val a = list[i]
                val b = list[n - 1 - i]
                if (a != null && b != null) {
                    matches += BracketMatch(slot = "l$counter", round = round, index = i, a = a, b = b)
                    counter++
                }
            }
            // Rotación: el primero queda fijo, el resto gira
            val last = list.removeAt(n - 1)
            list.add(1, last)
        }
        return matches
    }

    /**
     * Apunta el resultado de una partida del cuadro.
     * En eliminatoria, el ganador pasa a la siguiente ronda.
     */
    fun applyResult(
        format: TournamentFormat,
        matches: List<BracketMatch>,
        slot: String,
        result: Result
    ): List<BracketMatch> {
        val m = matches.firstOrNull { it.slot == slot } ?: return matches
        if (!m.playable) return matches
        require(!(format == TournamentFormat.KNOCKOUT && result == Result.DRAW)) {
            "En eliminatoria no puede haber empate"
        }
        val winner = when (result) {
            Result.A -> m.a
            Result.B -> m.b
            Result.DRAW -> null
        }
        val updated = matches.map {
            if (it.slot == slot) it.copy(winner = winner, draw = result == Result.DRAW) else it
        }
        if (format == TournamentFormat.LEAGUE || winner == null) return updated
        val rounds = updated.maxOf { it.round }
        return propagate(updated, m.round, m.index, winner, rounds)
    }

    private fun propagate(
        matches: List<BracketMatch>,
        round: Int,
        index: Int,
        winner: String,
        totalRounds: Int
    ): List<BracketMatch> {
        if (round >= totalRounds) return matches
        val nextSlot = "r${round + 1}m${index / 2}"
        return matches.map {
            if (it.slot != nextSlot) it
            else if (index % 2 == 0) it.copy(a = winner) else it.copy(b = winner)
        }
    }

    fun isFinished(format: TournamentFormat, matches: List<BracketMatch>): Boolean = when (format) {
        TournamentFormat.KNOCKOUT -> matches.maxByOrNull { it.round }?.winner != null
        TournamentFormat.LEAGUE -> matches.isNotEmpty() && matches.all { it.played }
    }

    fun champion(format: TournamentFormat, players: List<String>, matches: List<BracketMatch>): String? {
        if (!isFinished(format, matches)) return null
        return when (format) {
            TournamentFormat.KNOCKOUT -> matches.maxByOrNull { it.round }?.winner
            TournamentFormat.LEAGUE -> standings(players, matches).firstOrNull()?.uid
        }
    }

    fun standings(players: List<String>, matches: List<BracketMatch>): List<Standing> {
        val rows = players.associateWith { Standing(it, 0, 0, 0, 0) }.toMutableMap()
        for (m in matches.filter { it.played && it.a != null && it.b != null }) {
            val a = rows.getValue(m.a!!)
            val b = rows.getValue(m.b!!)
            when {
                m.draw -> {
                    rows[m.a] = a.copy(played = a.played + 1, drawn = a.drawn + 1)
                    rows[m.b] = b.copy(played = b.played + 1, drawn = b.drawn + 1)
                }
                m.winner == m.a -> {
                    rows[m.a] = a.copy(played = a.played + 1, won = a.won + 1)
                    rows[m.b] = b.copy(played = b.played + 1, lost = b.lost + 1)
                }
                else -> {
                    rows[m.a] = a.copy(played = a.played + 1, lost = a.lost + 1)
                    rows[m.b] = b.copy(played = b.played + 1, won = b.won + 1)
                }
            }
        }
        return rows.values.sortedWith(compareByDescending<Standing> { it.points }.thenByDescending { it.won })
    }
}
