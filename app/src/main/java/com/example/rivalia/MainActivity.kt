package com.example.rivalia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rivalia.ui.theme.RivaliaTheme

/** Pantallas de la app (navegación sencilla con una pila de rutas). */
sealed interface Route {
    data object Home : Route
    data class CrewDetail(val crewId: String) : Route
    data class NewMatch(val crewId: String) : Route
    data class NewTournament(val crewId: String) : Route
    data class TournamentDetail(val crewId: String, val tournamentId: String) : Route
    data class HeadToHead(val crewId: String) : Route
    data class Stats(val crewId: String) : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RivaliaApp
        setContent {
            RivaliaTheme {
                RivaliaRoot(app)
            }
        }
    }
}

@Composable
fun RivaliaRoot(app: RivaliaApp) {
    val user by app.auth.user.collectAsStateWithLifecycle()
    val uid = user?.uid
    if (uid == null) {
        AuthFlow(app)
    } else {
        // key(uid): si cambia el usuario se crean pantallas y ViewModels nuevos
        key(uid) { MainFlow(app, uid) }
    }
}

@Composable
private fun AuthFlow(app: RivaliaApp) {
    val vm: AuthViewModel = viewModel(key = "auth") { AuthViewModel(app) }
    var register by rememberSaveable { mutableStateOf(false) }
    if (register) {
        BackHandler { register = false }
        RegisterScreen(vm, onGoLogin = { register = false })
    } else {
        LoginScreen(vm, onGoRegister = { register = true })
    }
}

@Composable
private fun MainFlow(app: RivaliaApp, uid: String) {
    var stack by remember { mutableStateOf(listOf<Route>(Route.Home)) }
    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    val replaceTop: (Route) -> Unit = { stack = stack.dropLast(1) + it }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val route = stack.last()) {
        Route.Home -> {
            val vm: HomeViewModel = viewModel(key = "home-$uid") { HomeViewModel(app) }
            HomeScreen(vm, onOpenCrew = { push(Route.CrewDetail(it)) })
        }
        is Route.CrewDetail -> {
            val vm: CrewViewModel = viewModel(key = "crew-$uid-${route.crewId}") { CrewViewModel(app, route.crewId) }
            CrewScreen(
                vm = vm,
                onBack = pop,
                onNewMatch = { push(Route.NewMatch(route.crewId)) },
                onNewTournament = { push(Route.NewTournament(route.crewId)) },
                onOpenTournament = { push(Route.TournamentDetail(route.crewId, it)) },
                onHeadToHead = { push(Route.HeadToHead(route.crewId)) },
                onStats = { push(Route.Stats(route.crewId)) }
            )
        }
        is Route.NewMatch -> {
            val vm: CrewViewModel = viewModel(key = "crew-$uid-${route.crewId}") { CrewViewModel(app, route.crewId) }
            NewMatchScreen(vm, onBack = pop)
        }
        is Route.NewTournament -> {
            val vm: CrewViewModel = viewModel(key = "crew-$uid-${route.crewId}") { CrewViewModel(app, route.crewId) }
            NewTournamentScreen(vm, onBack = pop, onCreated = { replaceTop(Route.TournamentDetail(route.crewId, it)) })
        }
        is Route.TournamentDetail -> {
            val vm: TournamentViewModel = viewModel(key = "t-$uid-${route.tournamentId}") {
                TournamentViewModel(app, route.crewId, route.tournamentId)
            }
            TournamentScreen(vm, uid, onBack = pop)
        }
        is Route.HeadToHead -> {
            val vm: CrewViewModel = viewModel(key = "crew-$uid-${route.crewId}") { CrewViewModel(app, route.crewId) }
            HeadToHeadScreen(vm, onBack = pop)
        }
        is Route.Stats -> {
            val vm: CrewViewModel = viewModel(key = "crew-$uid-${route.crewId}") { CrewViewModel(app, route.crewId) }
            StatsScreen(vm, onBack = pop)
        }
    }
}
