package com.example.rivalia

import java.text.Normalizer

/*
 * Modelos de datos de Rivalia (capa de dominio).
 * Son clases Kotlin puras: no dependen de Android ni de Firebase,
 * así se pueden probar con tests unitarios.
 */

/** Resultado de una partida desde el punto de vista del jugador A. */
enum class Result { A, B, DRAW }

enum class TournamentFormat(val label: String) {
    KNOCKOUT("Eliminatoria directa"),
    LEAGUE("Liguilla (todos contra todos)")
}

enum class TournamentStatus { IN_PROGRESS, FINISHED }

data class UserProfile(
    val uid: String,
    val alias: String,
    val email: String
)

/** Grupo de amigos ("crew"). */
data class Crew(
    val id: String,
    val name: String,
    val code: String,
    val ownerId: String,
    val memberIds: List<String>,
    /** uid -> alias de cada miembro. */
    val members: Map<String, String>,
    val season: Int
) {
    fun aliasOf(uid: String?): String = members[uid] ?: "?"
}

/** Puntuación ELO de un jugador en un juego y temporada. */
data class Rating(
    val uid: String,
    val alias: String,
    val game: String,
    val gameKey: String,
    val season: Int,
    val elo: Int = Elo.INITIAL,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0
) {
    val played: Int get() = wins + losses + draws
}

data class Match(
    val id: String,
    val game: String,
    val gameKey: String,
    val playerA: String,
    val playerB: String,
    val aliasA: String,
    val aliasB: String,
    val result: Result,
    val scoreA: Int?,
    val scoreB: Int?,
    val eloBeforeA: Int,
    val eloBeforeB: Int,
    val deltaA: Int,
    val deltaB: Int,
    val season: Int,
    val tournamentId: String?,
    val slot: String?,
    val playedAt: Long,
    /** true si está guardada solo en el móvil y aún no se ha sincronizado. */
    val pending: Boolean = false
) {
    fun winnerId(): String? = when (result) {
        Result.A -> playerA
        Result.B -> playerB
        Result.DRAW -> null
    }

    fun involves(uid: String) = playerA == uid || playerB == uid
}

/** Partida de un cuadro o de una liguilla. */
data class BracketMatch(
    val slot: String,
    val round: Int,
    val index: Int,
    val a: String?,
    val b: String?,
    val winner: String? = null,
    val draw: Boolean = false,
    /** true si "a" pasa de ronda sin jugar porque no tiene rival. */
    val bye: Boolean = false
) {
    val played: Boolean get() = winner != null || draw
    val playable: Boolean get() = a != null && b != null && !played
}

data class Tournament(
    val id: String,
    val name: String,
    val game: String,
    val gameKey: String,
    val format: TournamentFormat,
    val status: TournamentStatus,
    val players: List<String>,
    val aliases: Map<String, String>,
    val matches: List<BracketMatch>,
    val championId: String?,
    val createdBy: String,
    val createdAt: Long
)

data class Trophy(
    val id: String,
    val uid: String,
    val alias: String,
    val game: String,
    val season: Int,
    val kind: String,
    val title: String,
    val at: Long
)

/** Partida apuntada en el móvil pendiente de enviar a Firebase. */
data class PendingMatch(
    val localId: Long = 0,
    /** Id del documento en Firestore: evita duplicados si se reintenta. */
    val uuid: String,
    val crewId: String,
    val game: String,
    val playerA: String,
    val playerB: String,
    val aliasA: String,
    val aliasB: String,
    val result: Result,
    val scoreA: Int?,
    val scoreB: Int?,
    val tournamentId: String?,
    val slot: String?,
    val playedAt: Long,
    val createdBy: String,
    val error: String? = null
) {
    fun toMatch(): Match = Match(
        id = uuid, game = game, gameKey = gameKey(game), playerA = playerA, playerB = playerB,
        aliasA = aliasA, aliasB = aliasB, result = result, scoreA = scoreA, scoreB = scoreB,
        eloBeforeA = 0, eloBeforeB = 0, deltaA = 0, deltaB = 0, season = 0,
        tournamentId = tournamentId, slot = slot, playedAt = playedAt, pending = true
    )
}

/** Clave normalizada de un juego: "EA FC 26" y "ea fc 26" son el mismo juego. */
fun gameKey(game: String): String {
    val sinTildes = Normalizer.normalize(game.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return sinTildes.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "juego" }
}
