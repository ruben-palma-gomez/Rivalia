package com.example.rivalia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EloTest {

    @Test
    fun mismosPuntos_probabilidadDel50porciento() {
        assertEquals(0.5, Elo.expected(1000, 1000), 0.0001)
    }

    @Test
    fun diferenciaDe200_esperado76porciento() {
        assertEquals(0.76, Elo.expected(1200, 1000), 0.01)
    }

    @Test
    fun victoriaEntreIguales_suma16() {
        val (a, b) = Elo.deltas(1000, 1000, Result.A)
        assertEquals(16, a)
        assertEquals(-16, b)
    }

    @Test
    fun empateEntreIguales_noCambia() {
        assertEquals(0 to 0, Elo.deltas(1000, 1000, Result.DRAW))
    }

    @Test
    fun ganarAlFavorito_daMasPuntosQueGanarAlDebil() {
        val contraFuerte = Elo.deltas(1000, 1200, Result.A).first
        val contraDebil = Elo.deltas(1000, 800, Result.A).first
        assertTrue(contraFuerte > contraDebil)
    }

    @Test
    fun loQueGanaUnoLoPierdeElOtro() {
        for (ra in listOf(800, 1000, 1350)) for (rb in listOf(900, 1100)) for (r in Result.values()) {
            val (a, b) = Elo.deltas(ra, rb, r)
            assertEquals(0, a + b)
        }
    }

    @Test
    fun claveDeJuego_ignoraMayusculasYTildes() {
        assertEquals("ea_fc_26", gameKey("  EA FC 26 "))
        assertEquals(gameKey("Fútbol"), gameKey("futbol"))
    }
}
