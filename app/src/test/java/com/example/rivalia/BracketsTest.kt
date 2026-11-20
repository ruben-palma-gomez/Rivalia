package com.example.rivalia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BracketsTest {

    private val ocho = (1..8).map { "p$it" }

    @Test
    fun ochoJugadores_tresRondasYSieteCruces() {
        val m = Brackets.createKnockout(ocho, Random(1))
        assertEquals(7, m.size)
        assertEquals(3, m.maxOf { it.round })
        assertEquals(4, m.count { it.round == 1 && it.playable })
    }

    @Test
    fun cincoJugadores_tresPasanSinJugar() {
        val m = Brackets.createKnockout((1..5).map { "p$it" }, Random(2))
        assertEquals(3, m.count { it.bye })
        // Ningún cruce de primera ronda queda con dos huecos
        assertTrue(m.filter { it.round == 1 }.all { it.a != null })
        // Los que pasan sin jugar ya están colocados en segunda ronda
        val segunda = m.filter { it.round == 2 }.flatMap { listOfNotNull(it.a, it.b) }
        assertEquals(3, segunda.size)
    }

    @Test
    fun elGanadorAvanzaHastaSerCampeon() {
        var m = Brackets.createKnockout(listOf("a", "b", "c", "d"), Random(3))
        val f = TournamentFormat.KNOCKOUT
        m.filter { it.round == 1 }.forEach { m = Brackets.applyResult(f, m, it.slot, Result.A) }
        val final = m.first { it.round == 2 }
        assertTrue(final.playable)
        m = Brackets.applyResult(f, m, final.slot, Result.B)
        assertTrue(Brackets.isFinished(f, m))
        assertEquals(final.b, Brackets.champion(f, listOf("a", "b", "c", "d"), m))
    }

    @Test(expected = IllegalArgumentException::class)
    fun enEliminatoriaNoHayEmpates() {
        val m = Brackets.createKnockout(listOf("a", "b"), Random(4))
        Brackets.applyResult(TournamentFormat.KNOCKOUT, m, m.first().slot, Result.DRAW)
    }

    @Test
    fun liguilla_todosContraTodosUnaVez() {
        for (n in 2..7) {
            val jugadores = (1..n).map { "j$it" }
            val m = Brackets.createLeague(jugadores)
            assertEquals(n * (n - 1) / 2, m.size)
            val parejas = m.map { setOf(it.a, it.b) }.toSet()
            assertEquals(m.size, parejas.size)
        }
    }

    @Test
    fun liguilla_clasificacionPorPuntos() {
        val jugadores = listOf("a", "b", "c")
        var m = Brackets.createLeague(jugadores)
        val f = TournamentFormat.LEAGUE
        assertNull(Brackets.champion(f, jugadores, m))
        m.forEach { partido ->
            val r = if (partido.a == "a" || partido.b == "a") {
                if (partido.a == "a") Result.A else Result.B
            } else Result.DRAW
            m = Brackets.applyResult(f, m, partido.slot, r)
        }
        val tabla = Brackets.standings(jugadores, m)
        assertEquals("a", tabla.first().uid)
        assertEquals(6, tabla.first().points)
        assertEquals("a", Brackets.champion(f, jugadores, m))
    }

    @Test
    fun noSePuedeApuntarDosVeces() {
        var m = Brackets.createKnockout(listOf("a", "b"), Random(5))
        val slot = m.first().slot
        m = Brackets.applyResult(TournamentFormat.KNOCKOUT, m, slot, Result.A)
        val otra = Brackets.applyResult(TournamentFormat.KNOCKOUT, m, slot, Result.B)
        assertEquals(m, otra)
        assertFalse(otra.first().playable)
    }
}
