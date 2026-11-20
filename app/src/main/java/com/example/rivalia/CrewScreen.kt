@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rivalia

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CrewScreen(
    vm: CrewViewModel,
    onBack: () -> Unit,
    onNewMatch: () -> Unit,
    onNewTournament: () -> Unit,
    onOpenTournament: (String) -> Unit,
    onHeadToHead: () -> Unit,
    onStats: () -> Unit
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Ranking", "Partidas", "Torneos", "Grupo")

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = { RivaliaTopBar(crew?.name ?: "Grupo", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            when (tab) {
                0, 1 -> ExtendedFloatingActionButton(onClick = onNewMatch) { Text("+ Partida") }
                2 -> ExtendedFloatingActionButton(onClick = onNewTournament) { Text("+ Torneo") }
                else -> {}
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            val c = crew
            if (c == null) {
                EmptyState("Cargando grupo…")
            } else when (tab) {
                0 -> RankingTab(vm, c)
                1 -> MatchesTab(vm, onHeadToHead, onStats)
                2 -> TournamentsTab(vm, onOpenTournament)
                else -> GroupTab(vm, c, onLeft = onBack)
            }
        }
    }
}

@Composable
private fun GameChips(games: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        games.forEach { g ->
            FilterChip(selected = gameKey(g) == selected, onClick = { onSelect(gameKey(g)) }, label = { Text(g) })
        }
    }
}

@Composable
private fun RankingTab(vm: CrewViewModel, crew: Crew) {
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    val games = ratings.map { it.game }.distinctBy { gameKey(it) }.sortedBy { it.lowercase() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val current = selected?.takeIf { s -> games.any { gameKey(it) == s } } ?: games.firstOrNull()?.let { gameKey(it) }

    if (games.isEmpty()) {
        EmptyState("Temporada ${crew.season}: todavía no hay partidas. Pulsa «+ Partida» para apuntar la primera.")
        return
    }
    GameChips(games, current) { selected = it }
    val rows = ratings.filter { it.gameKey == current }.sortedByDescending { it.elo }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(
                "Temporada ${crew.season} · ELO inicial ${Elo.INITIAL}",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge
            )
        }
        itemsIndexed(rows, key = { _, r -> r.uid }) { i, r ->
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = if (r.uid == vm.myUid) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) else CardDefaults.cardColors()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(medal, Modifier.width(40.dp), style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(r.alias, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${r.wins}V · ${r.losses}D · ${r.draws}E" + if (r.streak >= 2) " · racha ${r.streak} 🔥" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("${r.elo}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
    }
}

@Composable
private fun MatchesTab(vm: CrewViewModel, onHeadToHead: () -> Unit, onStats: () -> Unit) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHeadToHead, Modifier.weight(1f)) { Text("Cara a cara") }
                OutlinedButton(onClick = onStats, Modifier.weight(1f)) { Text("Mis estadísticas") }
            }
        }
        if (matches.isEmpty()) item { EmptyState("Aún no hay partidas en este grupo.") }
        items(matches, key = { it.id }) { m -> MatchRow(m, vm.myUid) }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
    }
}

@Composable
fun MatchRow(m: Match, myUid: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(resultText(m), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val extra = when {
                m.pending -> "⏳ Pendiente de sincronizar"
                m.involves(myUid) -> {
                    val d = if (m.playerA == myUid) m.deltaA else m.deltaB
                    "Tu ELO: ${if (d >= 0) "+" else ""}$d"
                }
                else -> "${m.aliasA} ${signed(m.deltaA)} · ${m.aliasB} ${signed(m.deltaB)}"
            }
            Text(
                "${m.game} · ${formatDate(m.playedAt)} · $extra" + if (m.tournamentId != null) " · Torneo" else "",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

@Composable
private fun TournamentsTab(vm: CrewViewModel, onOpen: (String) -> Unit) {
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp)) }
        if (tournaments.isEmpty()) item {
            EmptyState("No hay torneos. Crea uno y la app sorteará el cuadro automáticamente.")
        }
        items(tournaments, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpen(t.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${t.game} · ${t.format.label} · ${t.players.size} jugadores", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (t.status == TournamentStatus.FINISHED) "🏆 Campeón: ${t.aliases[t.championId] ?: "?"}"
                        else "En juego · ${t.matches.count { it.played && !it.bye }}/${t.matches.count { !it.bye }} partidas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupTab(vm: CrewViewModel, crew: Crew, onLeft: () -> Unit) {
    val trophies by vm.trophies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isOwner = crew.ownerId == vm.myUid
    var rename by remember { mutableStateOf(false) }
    var confirmSeason by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    if (rename) TextInputDialog("Renombrar grupo", "Nombre", crew.name, "Guardar",
        onConfirm = { vm.rename(it) }, onDismiss = { rename = false })
    if (confirmSeason) ConfirmDialog(
        "Cerrar temporada ${crew.season}",
        "El primero del ranking de cada juego recibirá un trofeo y el ELO de todos volverá a ${Elo.INITIAL}.",
        "Cerrar temporada", onConfirm = { vm.closeSeason() }, onDismiss = { confirmSeason = false }
    )
    if (confirmLeave) ConfirmDialog(
        "Salir del grupo", "Dejarás de ver este grupo. Tus partidas seguirán en el historial.", "Salir",
        onConfirm = { vm.leave(onLeft) }, onDismiss = { confirmLeave = false }
    )

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Código de invitación", style = MaterialTheme.typography.labelLarge)
                    Text(crew.code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "¡Únete a mi grupo «${crew.name}» en Rivalia! Código: ${crew.code}"
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Invitar amigos"))
                    }) { Text("Invitar amigos") }
                }
            }
        }
        item { SectionTitle("Miembros (${crew.memberIds.size})") }
        items(crew.memberIds) { uid ->
            Text(
                (crew.members[uid] ?: "?") + if (uid == crew.ownerId) "  · creador" else "",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item { SectionTitle("Vitrina de trofeos") }
        if (trophies.isEmpty()) item { Text("Todavía no hay trofeos. Gana un torneo o una temporada.") }
        items(trophies, key = { it.id }) { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏆", Modifier.width(36.dp), style = MaterialTheme.typography.titleLarge)
                Column {
                    Text("${t.alias} · ${t.title}", fontWeight = FontWeight.Medium)
                    Text("${t.game} · temporada ${t.season}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOwner) {
                    AssistChip(onClick = { rename = true }, label = { Text("Renombrar") })
                    AssistChip(onClick = { confirmSeason = true }, label = { Text("Cerrar temporada") })
                }
                AssistChip(onClick = { confirmLeave = true }, label = { Text("Salir del grupo") })
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp)) }
    }
}
