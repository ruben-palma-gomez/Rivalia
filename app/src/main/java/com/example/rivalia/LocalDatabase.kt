package com.example.rivalia

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Base de datos LOCAL de Rivalia (SQLite en el propio móvil).
 *
 * - pending_matches: partidas apuntadas sin conexión, pendientes de subir a Firestore.
 * - cached_crews / cached_ratings / cached_matches: copia de los datos remotos para
 *   que la app muestre grupos, ranking e historial al instante y sin internet.
 *
 * Todas las funciones son bloqueantes: se llaman desde Dispatchers.IO.
 */
class LocalDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    /** Se incrementa cada vez que cambian las partidas pendientes (para refrescar la UI). */
    private val _pendingVersion = MutableStateFlow(0)
    val pendingVersion: StateFlow<Int> = _pendingVersion

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE pending_matches (
                local_id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL UNIQUE,
                crew_id TEXT NOT NULL,
                game TEXT NOT NULL,
                player_a TEXT NOT NULL,
                player_b TEXT NOT NULL,
                alias_a TEXT NOT NULL,
                alias_b TEXT NOT NULL,
                result TEXT NOT NULL,
                score_a INTEGER,
                score_b INTEGER,
                tournament_id TEXT,
                slot TEXT,
                played_at INTEGER NOT NULL,
                created_by TEXT NOT NULL,
                error TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE cached_crews (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                code TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                members_json TEXT NOT NULL,
                season INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE cached_ratings (
                crew_id TEXT NOT NULL,
                season INTEGER NOT NULL,
                game_key TEXT NOT NULL,
                uid TEXT NOT NULL,
                alias TEXT NOT NULL,
                game TEXT NOT NULL,
                elo INTEGER NOT NULL,
                wins INTEGER NOT NULL,
                losses INTEGER NOT NULL,
                draws INTEGER NOT NULL,
                streak INTEGER NOT NULL,
                best_streak INTEGER NOT NULL,
                PRIMARY KEY (crew_id, season, game_key, uid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE cached_matches (
                id TEXT PRIMARY KEY,
                crew_id TEXT NOT NULL,
                played_at INTEGER NOT NULL,
                json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_matches_crew ON cached_matches(crew_id, played_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Las tablas cached_* se pueden regenerar desde Firebase; las pendientes se conservan.
        db.execSQL("DROP TABLE IF EXISTS cached_crews")
        db.execSQL("DROP TABLE IF EXISTS cached_ratings")
        db.execSQL("DROP TABLE IF EXISTS cached_matches")
        db.execSQL("DROP TABLE IF EXISTS pending_matches")
        onCreate(db)
    }

    // ---------------------------------------------------------------- pendientes

    fun insertPending(p: PendingMatch): Long {
        val v = ContentValues().apply {
            put("uuid", p.uuid)
            put("crew_id", p.crewId)
            put("game", p.game)
            put("player_a", p.playerA)
            put("player_b", p.playerB)
            put("alias_a", p.aliasA)
            put("alias_b", p.aliasB)
            put("result", p.result.name)
            if (p.scoreA != null) put("score_a", p.scoreA) else putNull("score_a")
            if (p.scoreB != null) put("score_b", p.scoreB) else putNull("score_b")
            put("tournament_id", p.tournamentId)
            put("slot", p.slot)
            put("played_at", p.playedAt)
            put("created_by", p.createdBy)
        }
        val id = writableDatabase.insertOrThrow("pending_matches", null, v)
        _pendingVersion.value++
        return id
    }

    fun pendingMatches(crewId: String? = null): List<PendingMatch> {
        val where = if (crewId != null) "crew_id = ?" else null
        val args = if (crewId != null) arrayOf(crewId) else null
        readableDatabase.query("pending_matches", null, where, args, null, null, "local_id ASC").use { c ->
            val list = mutableListOf<PendingMatch>()
            while (c.moveToNext()) list += c.toPending()
            return list
        }
    }

    fun deletePending(localId: Long) {
        writableDatabase.delete("pending_matches", "local_id = ?", arrayOf(localId.toString()))
        _pendingVersion.value++
    }

    fun markPendingError(localId: Long, message: String) {
        val v = ContentValues().apply { put("error", message) }
        writableDatabase.update("pending_matches", v, "local_id = ?", arrayOf(localId.toString()))
        _pendingVersion.value++
    }

    private fun Cursor.toPending() = PendingMatch(
        localId = getLong(getColumnIndexOrThrow("local_id")),
        uuid = str("uuid")!!,
        crewId = str("crew_id")!!,
        game = str("game")!!,
        playerA = str("player_a")!!,
        playerB = str("player_b")!!,
        aliasA = str("alias_a")!!,
        aliasB = str("alias_b")!!,
        result = Result.valueOf(str("result")!!),
        scoreA = intOrNull("score_a"),
        scoreB = intOrNull("score_b"),
        tournamentId = str("tournament_id"),
        slot = str("slot"),
        playedAt = getLong(getColumnIndexOrThrow("played_at")),
        createdBy = str("created_by")!!,
        error = str("error")
    )

    // ---------------------------------------------------------------- grupos

    fun saveCrews(crews: List<Crew>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cached_crews", null, null)
            crews.forEach { crew ->
                val members = JSONObject()
                crew.members.forEach { (uid, alias) -> members.put(uid, alias) }
                db.insert("cached_crews", null, ContentValues().apply {
                    put("id", crew.id)
                    put("name", crew.name)
                    put("code", crew.code)
                    put("owner_id", crew.ownerId)
                    put("members_json", members.toString())
                    put("season", crew.season)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun crews(): List<Crew> {
        readableDatabase.query("cached_crews", null, null, null, null, null, "name COLLATE NOCASE").use { c ->
            val list = mutableListOf<Crew>()
            while (c.moveToNext()) list += c.toCrew()
            return list
        }
    }

    fun crew(id: String): Crew? {
        readableDatabase.query("cached_crews", null, "id = ?", arrayOf(id), null, null, null).use { c ->
            return if (c.moveToFirst()) c.toCrew() else null
        }
    }

    private fun Cursor.toCrew(): Crew {
        val json = JSONObject(str("members_json")!!)
        val members = json.keys().asSequence().associateWith { json.getString(it) }
        return Crew(
            id = str("id")!!, name = str("name")!!, code = str("code")!!,
            ownerId = str("owner_id")!!, memberIds = members.keys.toList(),
            members = members, season = getInt(getColumnIndexOrThrow("season"))
        )
    }

    // ---------------------------------------------------------------- ranking

    fun saveRatings(crewId: String, season: Int, ratings: List<Rating>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cached_ratings", "crew_id = ? AND season = ?", arrayOf(crewId, season.toString()))
            ratings.forEach { r ->
                db.insert("cached_ratings", null, ContentValues().apply {
                    put("crew_id", crewId)
                    put("season", season)
                    put("game_key", r.gameKey)
                    put("uid", r.uid)
                    put("alias", r.alias)
                    put("game", r.game)
                    put("elo", r.elo)
                    put("wins", r.wins)
                    put("losses", r.losses)
                    put("draws", r.draws)
                    put("streak", r.streak)
                    put("best_streak", r.bestStreak)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun ratings(crewId: String, season: Int): List<Rating> {
        readableDatabase.query(
            "cached_ratings", null, "crew_id = ? AND season = ?",
            arrayOf(crewId, season.toString()), null, null, "elo DESC"
        ).use { c ->
            val list = mutableListOf<Rating>()
            while (c.moveToNext()) {
                list += Rating(
                    uid = c.str("uid")!!, alias = c.str("alias")!!, game = c.str("game")!!,
                    gameKey = c.str("game_key")!!, season = season,
                    elo = c.int("elo"), wins = c.int("wins"), losses = c.int("losses"),
                    draws = c.int("draws"), streak = c.int("streak"), bestStreak = c.int("best_streak")
                )
            }
            return list
        }
    }

    // ---------------------------------------------------------------- historial

    fun saveMatches(crewId: String, matches: List<Match>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cached_matches", "crew_id = ?", arrayOf(crewId))
            matches.forEach { m ->
                db.insert("cached_matches", null, ContentValues().apply {
                    put("id", m.id)
                    put("crew_id", crewId)
                    put("played_at", m.playedAt)
                    put("json", m.toJson().toString())
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun matches(crewId: String): List<Match> {
        readableDatabase.query(
            "cached_matches", arrayOf("json"), "crew_id = ?", arrayOf(crewId),
            null, null, "played_at DESC"
        ).use { c ->
            val list = mutableListOf<Match>()
            while (c.moveToNext()) list += matchFromJson(JSONObject(c.getString(0)))
            return list
        }
    }

    /** Al cerrar sesión se borra todo para no dejar datos de otra persona en el móvil. */
    fun clearAll() {
        val db = writableDatabase
        listOf("pending_matches", "cached_crews", "cached_ratings", "cached_matches").forEach {
            db.delete(it, null, null)
        }
        _pendingVersion.value++
    }

    private fun Match.toJson() = JSONObject().apply {
        put("id", id); put("game", game); put("gameKey", gameKey)
        put("playerA", playerA); put("playerB", playerB)
        put("aliasA", aliasA); put("aliasB", aliasB); put("result", result.name)
        put("scoreA", scoreA ?: JSONObject.NULL); put("scoreB", scoreB ?: JSONObject.NULL)
        put("eloBeforeA", eloBeforeA); put("eloBeforeB", eloBeforeB)
        put("deltaA", deltaA); put("deltaB", deltaB); put("season", season)
        put("tournamentId", tournamentId ?: JSONObject.NULL); put("slot", slot ?: JSONObject.NULL)
        put("playedAt", playedAt)
    }

    private fun matchFromJson(o: JSONObject) = Match(
        id = o.getString("id"), game = o.getString("game"), gameKey = o.getString("gameKey"),
        playerA = o.getString("playerA"), playerB = o.getString("playerB"),
        aliasA = o.getString("aliasA"), aliasB = o.getString("aliasB"),
        result = Result.valueOf(o.getString("result")),
        scoreA = if (o.isNull("scoreA")) null else o.getInt("scoreA"),
        scoreB = if (o.isNull("scoreB")) null else o.getInt("scoreB"),
        eloBeforeA = o.getInt("eloBeforeA"), eloBeforeB = o.getInt("eloBeforeB"),
        deltaA = o.getInt("deltaA"), deltaB = o.getInt("deltaB"), season = o.getInt("season"),
        tournamentId = if (o.isNull("tournamentId")) null else o.getString("tournamentId"),
        slot = if (o.isNull("slot")) null else o.getString("slot"),
        playedAt = o.getLong("playedAt")
    )

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndexOrThrow(col)
        return if (isNull(i)) null else getString(i)
    }

    private fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))

    private fun Cursor.intOrNull(col: String): Int? {
        val i = getColumnIndexOrThrow(col)
        return if (isNull(i)) null else getInt(i)
    }

    companion object {
        const val NAME = "rivalia_local.db"
        const val VERSION = 1
    }
}
