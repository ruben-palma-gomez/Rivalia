package com.example.rivalia

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Sistema de puntuación ELO (el del ajedrez).
 *
 * Probabilidad esperada de que A puntúe:  E(A) = 1 / (1 + 10^((Rb - Ra) / 400))
 * Nueva puntuación:                       Ra' = Ra + K · (S - E(A))
 * donde S = 1 si gana, 0,5 si empata y 0 si pierde.
 *
 * Ganar a alguien con más puntos da más ELO que ganar a alguien con menos.
 */
object Elo {
    const val INITIAL = 1000
    const val K = 32

    fun expected(ratingA: Int, ratingB: Int): Double =
        1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / 400.0))

    fun score(result: Result): Double = when (result) {
        Result.A -> 1.0
        Result.DRAW -> 0.5
        Result.B -> 0.0
    }

    /** Devuelve (cambioA, cambioB). Lo que gana uno lo pierde el otro. */
    fun deltas(ratingA: Int, ratingB: Int, result: Result, k: Int = K): Pair<Int, Int> {
        val deltaA = (k * (score(result) - expected(ratingA, ratingB))).roundToInt()
        return deltaA to -deltaA
    }
}
