package com.example.rivalia

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Convierte una Task de Firebase en una función suspendida (corrutinas). */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val e = task.exception
        if (e != null) cont.resumeWithException(e)
        else if (task.isCanceled) cont.cancel()
        else cont.resume(task.result)
    }
}

/*
 * Conversión entre documentos de Firestore y los modelos de la app.
 * Se hace a mano (con mapas) para tener control total del formato guardado.
 */

private fun DocumentSnapshot.int(field: String, default: Int = 0): Int =
    getLong(field)?.toInt() ?: default

private fun Any?.asStringMap(): Map<String, String> =
    (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()

@Suppress("UNCHECKED_CAST")
private fun Any?.asStringList(): List<String> = (this as? List<Any?>)?.mapNotNull { it?.toString() } ?: emptyList()

fun DocumentSnapshot.toCrew(): Crew? {
    if (!exists()) return null
    val members = get("members").asStringMap()
    return Crew(
        id = id,
        name = getString("name") ?: "Grupo",
        code = getString("code") ?: "",
        ownerId = getString("ownerId") ?: "",
        memberIds = get("memberIds").asStringList(),
        members = members,
        season = int("season", 1)
    )
}

fun DocumentSnapshot.toRating(): Rating = Rating(
    uid = getString("uid") ?: "",
    alias = getString("alias") ?: "?",
    game = getString("game") ?: "",
    gameKey = getString("gameKey") ?: "",
    season = int("season", 1),
    elo = int("elo", Elo.INITIAL),
    wins = int("wins"),
    losses = int("losses"),
    draws = int("draws"),
    streak = int("streak"),
    bestStreak = int("bestStreak")
)

fun Rating.toMap(): Map<String, Any> = mapOf(
    "uid" to uid, "alias" to alias, "game" to game, "gameKey" to gameKey, "season" to season,
    "elo" to elo, "wins" to wins, "losses" to losses, "draws" to draws,
    "streak" to streak, "bestStreak" to bestStreak, "updatedAt" to System.currentTimeMillis()
)

fun DocumentSnapshot.toMatch(): Match = Match(
    id = id,
    game = getString("game") ?: "",
    gameKey = getString("gameKey") ?: "",
    playerA = getString("playerA") ?: "",
    playerB = getString("playerB") ?: "",
    aliasA = getString("aliasA") ?: "?",
    aliasB = getString("aliasB") ?: "?",
    result = runCatching { Result.valueOf(getString("result") ?: "DRAW") }.getOrDefault(Result.DRAW),
    scoreA = getLong("scoreA")?.toInt(),
    scoreB = getLong("scoreB")?.toInt(),
    eloBeforeA = int("eloBeforeA", Elo.INITIAL),
    eloBeforeB = int("eloBeforeB", Elo.INITIAL),
    deltaA = int("deltaA"),
    deltaB = int("deltaB"),
    season = int("season", 1),
    tournamentId = getString("tournamentId"),
    slot = getString("slot"),
    playedAt = getLong("playedAt") ?: 0L
)

fun BracketMatch.toMap(): Map<String, Any?> = mapOf(
    "slot" to slot, "round" to round, "index" to index, "a" to a, "b" to b,
    "winner" to winner, "draw" to draw, "bye" to bye
)

private fun Map<*, *>.toBracketMatch() = BracketMatch(
    slot = this["slot"].toString(),
    round = (this["round"] as? Number)?.toInt() ?: 1,
    index = (this["index"] as? Number)?.toInt() ?: 0,
    a = this["a"] as? String,
    b = this["b"] as? String,
    winner = this["winner"] as? String,
    draw = this["draw"] as? Boolean ?: false,
    bye = this["bye"] as? Boolean ?: false
)

fun DocumentSnapshot.toTournament(): Tournament? {
    if (!exists()) return null
    val rawMatches = (get("matches") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toBracketMatch() }
    return Tournament(
        id = id,
        name = getString("name") ?: "Torneo",
        game = getString("game") ?: "",
        gameKey = getString("gameKey") ?: "",
        format = runCatching { TournamentFormat.valueOf(getString("format") ?: "") }
            .getOrDefault(TournamentFormat.KNOCKOUT),
        status = runCatching { TournamentStatus.valueOf(getString("status") ?: "") }
            .getOrDefault(TournamentStatus.IN_PROGRESS),
        players = get("players").asStringList(),
        aliases = get("aliases").asStringMap(),
        matches = rawMatches,
        championId = getString("championId"),
        createdBy = getString("createdBy") ?: "",
        createdAt = getLong("createdAt") ?: 0L
    )
}

fun DocumentSnapshot.toTrophy(): Trophy = Trophy(
    id = id,
    uid = getString("uid") ?: "",
    alias = getString("alias") ?: "?",
    game = getString("game") ?: "",
    season = int("season", 1),
    kind = getString("kind") ?: "",
    title = getString("title") ?: "",
    at = getLong("at") ?: 0L
)
