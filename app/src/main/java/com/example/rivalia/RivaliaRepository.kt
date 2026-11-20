package com.example.rivalia

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repositorio: punto único de acceso a los datos.
 * Los ViewModel no saben si los datos vienen de Firestore (remoto) o de SQLite (local).
 *
 * Estructura en Cloud Firestore:
 *   users/{uid}
 *   crews/{crewId}
 *       matches/{matchId}
 *       ratings/{temporada_juego_uid}
 *       tournaments/{tournamentId}
 *       trophies/{trophyId}
 */
class RivaliaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    val local: LocalDatabase
) {
    val uid: String? get() = auth.currentUser?.uid

    private fun crewRef(crewId: String): DocumentReference = db.collection("crews").document(crewId)
    private fun matchesRef(crewId: String): CollectionReference = crewRef(crewId).collection("matches")
    private fun ratingsRef(crewId: String): CollectionReference = crewRef(crewId).collection("ratings")
    private fun tournamentsRef(crewId: String): CollectionReference = crewRef(crewId).collection("tournaments")
    private fun trophiesRef(crewId: String): CollectionReference = crewRef(crewId).collection("trophies")

    fun ratingId(season: Int, gameKey: String, uid: String) = "${season}_${gameKey}_$uid"

    // ------------------------------------------------------------------ perfil

    suspend fun myAlias(): String {
        val id = uid ?: return ""
        return runCatching { db.collection("users").document(id).get().await().getString("alias") }
            .getOrNull() ?: auth.currentUser?.email?.substringBefore('@') ?: "Jugador"
    }

    /** Cambia el alias en el perfil y en todos los grupos del usuario. */
    suspend fun updateAlias(newAlias: String, crews: List<Crew>) {
        val id = uid ?: return
        val batch = db.batch()
        batch.update(db.collection("users").document(id), "alias", newAlias)
        crews.forEach { batch.update(crewRef(it.id), "members.$id", newAlias) }
        batch.commit().await()
    }

    // ------------------------------------------------------------------ grupos

    /** Grupos del usuario: primero la copia local y después las actualizaciones en tiempo real. */
    fun crewsFlow(): Flow<List<Crew>> = callbackFlow {
        val id = uid
        if (id == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val remote = AtomicBoolean(false)
        launch(Dispatchers.IO) { local.crews().let { if (!remote.get()) trySend(it) } }
        val reg = db.collection("crews").whereArrayContains("memberIds", id)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                remote.set(true)
                val crews = snap.documents.mapNotNull { it.toCrew() }.sortedBy { it.name.lowercase() }
                trySend(crews)
                launch(Dispatchers.IO) { local.saveCrews(crews) }
            }
        awaitClose { reg.remove() }
    }

    fun crewFlow(crewId: String): Flow<Crew?> = callbackFlow {
        val remote = AtomicBoolean(false)
        launch(Dispatchers.IO) { local.crew(crewId)?.let { if (!remote.get()) trySend(it) } }
        val reg = crewRef(crewId).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            remote.set(true)
            trySend(snap.toCrew())
        }
        awaitClose { reg.remove() }
    }

    suspend fun createCrew(name: String, alias: String): String {
        val id = uid ?: error("Sesión no iniciada")
        val code = uniqueCode()
        val ref = db.collection("crews").document()
        ref.set(
            mapOf(
                "name" to name.trim(),
                "code" to code,
                "ownerId" to id,
                "memberIds" to listOf(id),
                "members" to mapOf(id to alias),
                "season" to 1,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        return ref.id
    }

    private suspend fun uniqueCode(): String {
        val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O ni 1/I para no confundir
        repeat(5) {
            val code = (1..6).map { letters.random() }.joinToString("")
            val exists = db.collection("crews").whereEqualTo("code", code).limit(1).get().await()
            if (exists.isEmpty) return code
        }
        error("No se pudo generar un código. Inténtalo otra vez.")
    }

    /** Une al usuario a un grupo mediante su código de invitación. */
    suspend fun joinCrew(code: String, alias: String): String {
        val id = uid ?: error("Sesión no iniciada")
        val snap = db.collection("crews").whereEqualTo("code", code.trim().uppercase()).limit(1).get().await()
        val doc = snap.documents.firstOrNull() ?: error("No existe ningún grupo con ese código")
        val crew = doc.toCrew() ?: error("Grupo no válido")
        if (id !in crew.memberIds) {
            doc.reference.update(
                mapOf(
                    "memberIds" to FieldValue.arrayUnion(id),
                    "members.$id" to alias
                )
            ).await()
        }
        return doc.id
    }

    suspend fun renameCrew(crewId: String, name: String) {
        crewRef(crewId).update("name", name.trim()).await()
    }

    suspend fun leaveCrew(crew: Crew) {
        val id = uid ?: return
        crewRef(crew.id).update(
            mapOf(
                "memberIds" to FieldValue.arrayRemove(id),
                "members.$id" to FieldValue.delete()
            )
        ).await()
    }

    // ------------------------------------------------------------------ ranking e historial

    fun ratingsFlow(crewId: String, season: Int): Flow<List<Rating>> = callbackFlow {
        val remote = AtomicBoolean(false)
        launch(Dispatchers.IO) { local.ratings(crewId, season).let { if (!remote.get()) trySend(it) } }
        val reg = ratingsRef(crewId).whereEqualTo("season", season)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                remote.set(true)
                val list = snap.documents.map { it.toRating() }.sortedByDescending { it.elo }
                trySend(list)
                launch(Dispatchers.IO) { local.saveRatings(crewId, season, list) }
            }
        awaitClose { reg.remove() }
    }

    fun matchesFlow(crewId: String): Flow<List<Match>> = callbackFlow {
        val remote = AtomicBoolean(false)
        launch(Dispatchers.IO) { local.matches(crewId).let { if (!remote.get()) trySend(it) } }
        val reg = matchesRef(crewId).orderBy("playedAt", Query.Direction.DESCENDING).limit(300)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                remote.set(true)
                val list = snap.documents.map { it.toMatch() }
                trySend(list)
                launch(Dispatchers.IO) { local.saveMatches(crewId, list) }
            }
        awaitClose { reg.remove() }
    }

    /**
     * Apunta una partida. Siempre se guarda primero en SQLite (funciona sin conexión)
     * y después SyncManager la sube a Firestore y calcula el ELO.
     */
    suspend fun recordMatch(
        crewId: String, game: String, playerA: String, playerB: String,
        aliasA: String, aliasB: String, result: Result, scoreA: Int?, scoreB: Int?,
        tournamentId: String? = null, slot: String? = null
    ) = withContext(Dispatchers.IO) {
        local.insertPending(
            PendingMatch(
                uuid = UUID.randomUUID().toString(),
                crewId = crewId, game = game.trim(), playerA = playerA, playerB = playerB,
                aliasA = aliasA, aliasB = aliasB, result = result, scoreA = scoreA, scoreB = scoreB,
                tournamentId = tournamentId, slot = slot, playedAt = System.currentTimeMillis(),
                createdBy = uid ?: error("Sesión no iniciada")
            )
        )
    }

    /**
     * Sube una partida pendiente en una TRANSACCIÓN de Firestore:
     * lee los ELO actuales, guarda la partida, actualiza el ranking de los dos jugadores
     * y, si es de un torneo, avanza el cuadro. O se hace todo o no se hace nada.
     */
    suspend fun commitPending(p: PendingMatch) {
        val crewDoc = crewRef(p.crewId)
        val matchDoc = matchesRef(p.crewId).document(p.uuid)
        val gk = gameKey(p.game)
        val tournamentDoc = p.tournamentId?.let { tournamentsRef(p.crewId).document(it) }

        db.runTransaction { tx ->
            // 1) Lecturas (Firestore exige leer antes de escribir)
            if (tx.get(matchDoc).exists()) return@runTransaction // ya se subió antes
            val crew = tx.get(crewDoc)
            val season = crew.getLong("season")?.toInt() ?: 1
            val refA = ratingsRef(p.crewId).document(ratingId(season, gk, p.playerA))
            val refB = ratingsRef(p.crewId).document(ratingId(season, gk, p.playerB))
            val snapA = tx.get(refA)
            val snapB = tx.get(refB)
            val tournament = tournamentDoc?.let { tx.get(it).toTournament() }

            val oldA = if (snapA.exists()) snapA.toRating() else Rating(p.playerA, p.aliasA, p.game, gk, season)
            val oldB = if (snapB.exists()) snapB.toRating() else Rating(p.playerB, p.aliasB, p.game, gk, season)
            val (deltaA, deltaB) = Elo.deltas(oldA.elo, oldB.elo, p.result)

            // 2) Escrituras
            tx.set(
                matchDoc, mapOf(
                    "game" to p.game, "gameKey" to gk,
                    "playerA" to p.playerA, "playerB" to p.playerB,
                    "players" to listOf(p.playerA, p.playerB),
                    "aliasA" to p.aliasA, "aliasB" to p.aliasB,
                    "result" to p.result.name, "scoreA" to p.scoreA, "scoreB" to p.scoreB,
                    "eloBeforeA" to oldA.elo, "eloBeforeB" to oldB.elo,
                    "deltaA" to deltaA, "deltaB" to deltaB, "season" to season,
                    "tournamentId" to p.tournamentId, "slot" to p.slot,
                    "playedAt" to p.playedAt, "createdBy" to p.createdBy
                )
            )
            tx.set(refA, updatedRating(oldA, p.aliasA, p.game, deltaA, p.result, isA = true).toMap())
            tx.set(refB, updatedRating(oldB, p.aliasB, p.game, deltaB, p.result, isA = false).toMap())

            if (tournament != null && tournamentDoc != null && p.slot != null) {
                val updated = Brackets.applyResult(tournament.format, tournament.matches, p.slot, p.result)
                val champion = Brackets.champion(tournament.format, tournament.players, updated)
                val changes = mutableMapOf<String, Any?>("matches" to updated.map { it.toMap() })
                if (champion != null && tournament.championId == null) {
                    changes["championId"] = champion
                    changes["status"] = TournamentStatus.FINISHED.name
                    tx.set(
                        trophiesRef(p.crewId).document("torneo_${tournament.id}"), mapOf(
                            "uid" to champion,
                            "alias" to (tournament.aliases[champion] ?: "?"),
                            "game" to tournament.game, "season" to season,
                            "kind" to "TORNEO", "title" to "Campeón de ${tournament.name}",
                            "at" to System.currentTimeMillis()
                        )
                    )
                }
                tx.update(tournamentDoc, changes)
            }
        }.await()
    }

    private fun updatedRating(old: Rating, alias: String, game: String, delta: Int, result: Result, isA: Boolean): Rating {
        val won = (result == Result.A && isA) || (result == Result.B && !isA)
        val lost = (result == Result.B && isA) || (result == Result.A && !isA)
        val streak = if (won) (if (old.streak > 0) old.streak + 1 else 1) else 0
        return old.copy(
            alias = alias, game = game, elo = old.elo + delta,
            wins = old.wins + if (won) 1 else 0,
            losses = old.losses + if (lost) 1 else 0,
            draws = old.draws + if (!won && !lost) 1 else 0,
            streak = streak, bestStreak = maxOf(old.bestStreak, streak)
        )
    }

    // ------------------------------------------------------------------ torneos

    fun tournamentsFlow(crewId: String): Flow<List<Tournament>> = callbackFlow {
        val reg = tournamentsRef(crewId).orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { it.toTournament() })
            }
        awaitClose { reg.remove() }
    }

    fun tournamentFlow(crewId: String, tournamentId: String): Flow<Tournament?> = callbackFlow {
        val reg = tournamentsRef(crewId).document(tournamentId).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(snap.toTournament())
        }
        awaitClose { reg.remove() }
    }

    suspend fun createTournament(
        crewId: String, name: String, game: String, format: TournamentFormat,
        players: List<String>, aliases: Map<String, String>
    ): String {
        val matches = when (format) {
            TournamentFormat.KNOCKOUT -> Brackets.createKnockout(players)
            TournamentFormat.LEAGUE -> Brackets.createLeague(players)
        }
        val ref = tournamentsRef(crewId).document()
        ref.set(
            mapOf(
                "name" to name.trim(),
                "game" to game.trim(),
                "gameKey" to gameKey(game),
                "format" to format.name,
                "status" to TournamentStatus.IN_PROGRESS.name,
                "players" to players,
                "aliases" to aliases.filterKeys { it in players },
                "matches" to matches.map { it.toMap() },
                "championId" to null,
                "createdBy" to (uid ?: ""),
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        return ref.id
    }

    suspend fun deleteTournament(crewId: String, tournamentId: String) {
        tournamentsRef(crewId).document(tournamentId).delete().await()
    }

    // ------------------------------------------------------------------ temporadas y trofeos

    fun trophiesFlow(crewId: String): Flow<List<Trophy>> = callbackFlow {
        val reg = trophiesRef(crewId).orderBy("at", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.map { it.toTrophy() })
            }
        awaitClose { reg.remove() }
    }

    /**
     * Cierra la temporada: el primero del ranking de cada juego recibe un trofeo
     * y la temporada avanza (el ELO vuelve a empezar en 1000).
     */
    suspend fun closeSeason(crew: Crew): Int {
        val ratings = ratingsRef(crew.id).whereEqualTo("season", crew.season).get().await()
            .documents.map { it.toRating() }
        val batch = db.batch()
        var trophies = 0
        ratings.groupBy { it.gameKey }.values.forEach { list ->
            val best = list.filter { it.played > 0 }.maxByOrNull { it.elo } ?: return@forEach
            batch.set(
                trophiesRef(crew.id).document("temporada_${crew.season}_${best.gameKey}"), mapOf(
                    "uid" to best.uid, "alias" to best.alias, "game" to best.game,
                    "season" to crew.season, "kind" to "TEMPORADA",
                    "title" to "Campeón de la temporada ${crew.season}",
                    "at" to System.currentTimeMillis()
                )
            )
            trophies++
        }
        batch.update(crewRef(crew.id), "season", crew.season + 1)
        batch.commit().await()
        return trophies
    }
}
