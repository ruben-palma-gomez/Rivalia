@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rivalia

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ===================================================================  PARTIDA RÁPIDA

@Composable
fun NewMatchScreen(vm: CrewViewModel, onBack: () -> Unit) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var game by rememberSaveable { mutableStateOf("") }
    var playerA by rememberSaveable { mutableStateOf<String?>(vm.myUid) }
    var playerB by rememberSaveable { mutableStateOf<String?>(null) }
    var result by rememberSaveable { mutableStateOf<Result?>(null) }
    var scoreA by rememberSaveable { mutableStateOf("") }
    var scoreB by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val c = crew
    val options = c?.memberIds?.map { it to c.aliasOf(it) }.orEmpty()

    Scaffold(
        topBar = { RivaliaTopBar("Nueva partida", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = game, onValueChange = { game = it.take(40) }, label = { Text("Juego (EA FC, Mario Kart…)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (games.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { g -> SuggestionChip(onClick = { game = g }, label = { Text(g) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerPicker("Jugador A", options, playerA, { playerA = it }, Modifier.weight(1f))
                PlayerPicker("Jugador B", options.filter { it.first != playerA }, playerB, { playerB = it }, Modifier.weight(1f))
            }
            if (playerA != null && playerB != null && c != null) {
                Text("Resultado", style = MaterialTheme.typography.titleMedium)
                ResultSelector(c.aliasOf(playerA), c.aliasOf(playerB), result, allowDraw = true) { result = it }
                Text("Marcador (opcional)", style = MaterialTheme.typography.titleMedium)
                ScoreFields(scoreA, scoreB, { scoreA = it }, { scoreB = it })
            }
            if (options.size < 2) {
                Text("Necesitas al menos otro miembro en el grupo. Invítale con el código desde la pestaña «Grupo».")
            }
            Button(
                onClick = {
                    val a = playerA
                    val b = playerB
                    val r = result
                    if (a != null && b != null && r != null) {
                        vm.addMatch(game, a, b, r, scoreA.toIntOrNull(), scoreB.toIntOrNull(), onBack)
                    }
                },
                enabled = playerA != null && playerB != null && result != null && game.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Guardar partida") }
            Text(
                "Se guarda en el móvil al instante y se sincroniza cuando hay conexión.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ===================================================================  CARA A CARA

@Composable
fun HeadToHeadScreen(vm: CrewViewModel, onBack: () -> Unit) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    var rival by rememberSaveable { mutableStateOf<String?>(null) }
    val c = crew

    Scaffold(topBar = { RivaliaTopBar("Cara a cara", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Elige a tu rival", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    c?.memberIds?.filter { it != vm.myUid }?.forEach { uid ->
                        FilterChip(selected = rival == uid, onClick = { rival = uid }, label = { Text(c.aliasOf(uid)) })
                    }
                }
            }
            val r = rival
            if (r == null || c == null) {
                item { EmptyState("Selecciona un rival para ver vuestro historial.") }
            } else {
                val h = Stats.headToHead(vm.myUid, r, matches)
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tú  vs  ${c.aliasOf(r)}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${h.wins} – ${h.losses}", style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("${h.draws} empates · ${h.total} partidas", style = MaterialTheme.typography.bodyMedium)
                            val streakText = when {
                                h.currentStreak > 1 -> "Llevas ${h.currentStreak} victorias seguidas 🔥"
                                h.currentStreak < -1 -> "Llevas ${-h.currentStreak} derrotas seguidas. ¡A por la revancha!"
                                h.total == 0 -> "Todavía no habéis jugado."
                                else -> ""
                            }
                            if (streakText.isNotEmpty()) Text(streakText, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (h.lastMatches.isNotEmpty()) item { SectionTitle("Últimas partidas", Modifier.padding(horizontal = 16.dp)) }
                items(h.lastMatches, key = { it.id }) { MatchRow(it, vm.myUid) }
            }
        }
    }
}

// ===================================================================  ESTADÍSTICAS

@Composable
fun StatsScreen(vm: CrewViewModel, onBack: () -> Unit) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val trophies by vm.trophies.collectAsStateWithLifecycle()
    val myRatings = ratings.filter { it.uid == vm.myUid }.sortedByDescending { it.played }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val current = selected ?: myRatings.firstOrNull()?.gameKey
    val season = crew?.season ?: 1

    Scaffold(topBar = { RivaliaTopBar("Mis estadísticas", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (myRatings.isEmpty()) {
                item { EmptyState("Juega alguna partida esta temporada para ver tus estadísticas.") }
            } else {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        myRatings.forEach { r ->
                            FilterChip(selected = r.gameKey == current, onClick = { selected = r.gameKey }, label = { Text(r.game) })
                        }
                    }
                }
                val r = myRatings.firstOrNull { it.gameKey == current }
                if (r != null) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${r.game} · temporada $season", style = MaterialTheme.typography.titleMedium)
                                Text("ELO ${r.elo}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                val pct = if (r.played > 0) r.wins * 100 / r.played else 0
                                Text("${r.played} partidas · ${r.wins} victorias ($pct %) · mejor racha ${r.bestStreak}")
                            }
                        }
                    }
                    item { SectionTitle("Evolución del ELO") }
                    item { EloChart(Stats.eloHistory(vm.myUid, r.gameKey, season, matches)) }
                }
            }
            item { SectionTitle("Logros") }
            items(Stats.achievements(vm.myUid, matches, ratings, trophies)) { a ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (a.unlocked) "🏅" else "🔒", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleLarge)
                    Column {
                        Text(a.title, fontWeight = FontWeight.SemiBold)
                        Text(a.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
