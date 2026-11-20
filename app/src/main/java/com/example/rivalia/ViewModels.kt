package com.example.rivalia

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Traduce los errores de Firebase a mensajes comprensibles. */
fun friendlyError(e: Throwable): String = when (e) {
    is FirebaseNetworkException -> "Sin conexión a internet. Inténtalo de nuevo."
    is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos."
    is FirebaseAuthWeakPasswordException -> "La contraseña es demasiado débil."
    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException ->
        "Correo o contraseña incorrectos."
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No tienes permiso para hacer eso."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Sin conexión. Se intentará más tarde."
        else -> "Error de la base de datos (${e.code.name})."
    }
    else -> e.message ?: "Ha ocurrido un error."
}

// =====================================================================  AUTENTICACIÓN

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class AuthViewModel(private val app: RivaliaApp) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    private fun run(block: suspend () -> Unit) {
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                block()
                _state.value = _state.value.copy(loading = false)
            } catch (e: Exception) {
                _state.value = AuthUiState(error = friendlyError(e))
            }
        }
    }

    fun login(email: String, password: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _state.value = AuthUiState(error = "Escribe un correo válido."); return
        }
        if (password.isEmpty()) {
            _state.value = AuthUiState(error = "Escribe la contraseña."); return
        }
        run { app.auth.login(email, password) }
    }

    fun register(alias: String, email: String, password: String, repeat: String, birthMillis: Long?) {
        val error = when {
            !Regex("^[A-Za-z0-9_ñÑ]{3,20}$").matches(alias.trim()) ->
                "El alias debe tener de 3 a 20 letras, números o _."
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Escribe un correo válido."
            birthMillis == null -> "Indica tu fecha de nacimiento."
            ageFrom(birthMillis) < MIN_AGE -> "Rivalia es para mayores de $MIN_AGE años."
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
            password != repeat -> "Las contraseñas no coinciden."
            else -> null
        }
        if (error != null) {
            _state.value = AuthUiState(error = error); return
        }
        run { app.auth.register(alias, email, password) }
    }

    fun resetPassword(email: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _state.value = AuthUiState(error = "Escribe arriba tu correo para recuperar la contraseña."); return
        }
        viewModelScope.launch {
            runCatching { app.auth.resetPassword(email) }
            // Mismo mensaje siempre, para no revelar qué correos están registrados
            _state.value = AuthUiState(info = "Si existe una cuenta con ese correo, te llegará un enlace.")
        }
    }

    companion object {
        const val MIN_AGE = 14

        fun ageFrom(birthMillis: Long): Int {
            val birth = Calendar.getInstance().apply { timeInMillis = birthMillis }
            val now = Calendar.getInstance()
            var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
            if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
            return age
        }
    }
}

// =====================================================================  INICIO (MIS GRUPOS)

class HomeViewModel(private val app: RivaliaApp) : ViewModel() {
    private val repo = app.repository

    val crews: StateFlow<List<Crew>> = repo.crewsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = app.local.pendingVersion
        .map { withContext(Dispatchers.IO) { app.local.pendingMatches().size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val syncing: StateFlow<Boolean> = app.sync.syncing

    private val _alias = MutableStateFlow("")
    val alias: StateFlow<String> = _alias.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { _alias.value = repo.myAlias() }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun action(block: suspend () -> Unit) {
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            } finally {
                _busy.value = false
            }
        }
    }

    fun createCrew(name: String, onCreated: (String) -> Unit) {
        if (name.isBlank()) {
            _message.value = "Ponle un nombre al grupo."; return
        }
        action { onCreated(repo.createCrew(name, alias.value.ifBlank { repo.myAlias() })) }
    }

    fun joinCrew(code: String, onJoined: (String) -> Unit) {
        if (code.trim().length != 6) {
            _message.value = "El código tiene 6 caracteres."; return
        }
        action { onJoined(repo.joinCrew(code, alias.value.ifBlank { repo.myAlias() })) }
    }

    fun updateAlias(newAlias: String) {
        if (!Regex("^[A-Za-z0-9_ñÑ]{3,20}$").matches(newAlias.trim())) {
            _message.value = "El alias debe tener de 3 a 20 letras, números o _."; return
        }
        action {
            repo.updateAlias(newAlias.trim(), crews.value)
            _alias.value = newAlias.trim()
            _message.value = "Alias actualizado."
        }
    }

    fun syncNow() = app.sync.requestSync()

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.local.clearAll() }
            app.auth.logout()
            onDone()
        }
    }
}

// =====================================================================  GRUPO

@OptIn(ExperimentalCoroutinesApi::class)
class CrewViewModel(private val app: RivaliaApp, val crewId: String) : ViewModel() {
    private val repo = app.repository
    val myUid: String = repo.uid ?: ""

    val crew: StateFlow<Crew?> = repo.crewFlow(crewId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ratings: StateFlow<List<Rating>> = crew.map { it?.season }.distinctUntilChanged()
        .flatMapLatest { season -> if (season == null) flowOf(emptyList()) else repo.ratingsFlow(crewId, season) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val pending = app.local.pendingVersion
        .map { withContext(Dispatchers.IO) { app.local.pendingMatches(crewId) } }

    /** Historial: partidas pendientes (solo en el móvil) + partidas ya sincronizadas. */
    val matches: StateFlow<List<Match>> = combine(repo.matchesFlow(crewId), pending) { remote, local ->
        val uploaded = remote.map { it.id }.toSet()
        local.filter { it.uuid !in uploaded }.map { it.toMatch() }.reversed() + remote
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tournaments: StateFlow<List<Tournament>> = repo.tournamentsFlow(crewId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trophies: StateFlow<List<Trophy>> = repo.trophiesFlow(crewId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Juegos que ya se han jugado en el grupo (para sugerirlos). */
    val games: StateFlow<List<String>> = combine(ratings, matches) { r, m ->
        (r.map { it.game } + m.map { it.game }).filter { it.isNotBlank() }
            .distinctBy { gameKey(it) }.sortedBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun addMatch(
        game: String, playerA: String, playerB: String, result: Result,
        scoreA: Int?, scoreB: Int?, onDone: () -> Unit
    ) {
        val c = crew.value ?: return
        when {
            game.isBlank() -> { _message.value = "Escribe a qué se ha jugado."; return }
            playerA == playerB -> { _message.value = "Elige dos jugadores distintos."; return }
        }
        viewModelScope.launch {
            try {
                repo.recordMatch(
                    crewId, game, playerA, playerB, c.aliasOf(playerA), c.aliasOf(playerB),
                    result, scoreA, scoreB
                )
                app.sync.requestSync()
                _message.value = "Partida apuntada."
                onDone()
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }

    fun createTournament(
        name: String, game: String, format: TournamentFormat, players: List<String>,
        onCreated: (String) -> Unit
    ) {
        val c = crew.value ?: return
        when {
            name.isBlank() -> { _message.value = "Ponle nombre al torneo."; return }
            game.isBlank() -> { _message.value = "Indica el juego del torneo."; return }
            players.size < 2 -> { _message.value = "Elige al menos 2 jugadores."; return }
        }
        viewModelScope.launch {
            try {
                onCreated(repo.createTournament(crewId, name, game, format, players, c.members))
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }

    fun closeSeason() {
        val c = crew.value ?: return
        viewModelScope.launch {
            try {
                val n = repo.closeSeason(c)
                _message.value = "Temporada ${c.season} cerrada. Trofeos entregados: $n."
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repo.renameCrew(crewId, name)
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }

    fun leave(onDone: () -> Unit) {
        val c = crew.value ?: return
        viewModelScope.launch {
            try {
                repo.leaveCrew(c)
                onDone()
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }
}

// =====================================================================  TORNEO

class TournamentViewModel(
    private val app: RivaliaApp,
    val crewId: String,
    val tournamentId: String
) : ViewModel() {
    private val repo = app.repository

    val tournament: StateFlow<Tournament?> = repo.tournamentFlow(crewId, tournamentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Huecos del cuadro con resultado apuntado en el móvil pero aún sin subir. */
    val pendingSlots: StateFlow<Set<String>> = app.local.pendingVersion
        .map {
            withContext(Dispatchers.IO) {
                app.local.pendingMatches(crewId).filter { it.tournamentId == tournamentId }
                    .mapNotNull { it.slot }.toSet()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun report(match: BracketMatch, result: Result, scoreA: Int?, scoreB: Int?) {
        val t = tournament.value ?: return
        val a = match.a ?: return
        val b = match.b ?: return
        if (t.format == TournamentFormat.KNOCKOUT && result == Result.DRAW) {
            _message.value = "En eliminatoria tiene que haber un ganador."; return
        }
        viewModelScope.launch {
            try {
                repo.recordMatch(
                    crewId, t.game, a, b, t.aliases[a] ?: "?", t.aliases[b] ?: "?",
                    result, scoreA, scoreB, tournamentId = t.id, slot = match.slot
                )
                app.sync.requestSync()
                _message.value = "Resultado apuntado."
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteTournament(crewId, tournamentId)
                onDone()
            } catch (e: Exception) {
                _message.value = friendlyError(e)
            }
        }
    }
}
