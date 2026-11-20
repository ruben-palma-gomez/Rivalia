package com.example.rivalia

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {

    private fun partida(t: Long, a: String, b: String, r: Result, eloA: Int = 1000, eloB: Int = 1000): Match {
        val (da, db) = Elo.deltas(eloA, eloB, r)
        return Match(
            id = "m$t", game = "Mario Kart", gameKey = "mario_kart", playerA = a, playerB = b,
            aliasA = a, aliasB = b, result = r, scoreA = null, scoreB = null,
            eloBeforeA = eloA, eloBeforeB = eloB, deltaA = da, deltaB = db, season = 1,
            tournamentId = null, slot = null, playedAt = t
        )
    }

    @Test
    fun caraACara_cuentaVictoriasYRacha() {
        val partidas = listOf(
            partida(1, "laura", "pablo", Result.A),
            partida(2, "pablo", "laura", Result.A),
            partida(3, "laura", "pablo", Result.B),
            partida(4, "laura", "otro", Result.A)
        )
        val h = Stats.headToHead("laura", "pablo", partidas)
        assertEquals(1, h.wins)
        assertEquals(2, h.losses)
        assertEquals(-2, h.currentStreak)
    }

    @Test
    fun historialDeElo_empiezaEnElValorInicial() {
        val p1 = partida(1, "laura", "pablo", Result.A)
        val p2 = partida(2, "pablo", "laura", Result.A, eloA = 1000 + p1.deltaB, eloB = 1000 + p1.deltaA)
        val h = Stats.eloHistory("laura", "mario_kart", 1, listOf(p2, p1))
        assertEquals(listOf(1000, 1016, 1016 + p2.deltaB), h)
    }
}
