@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rivalia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ===================================================================  CREAR TORNEO

@Composable
fun NewTournamentScreen(vm: CrewViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var name by rememberSaveable { mutableStateOf("") }
    var game by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(TournamentFormat.KNOCKOUT) }
    var selected by remember(crew?.memberIds) { mutableStateOf(crew?.memberIds?.toSet().orEmpty()) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val c = crew

    Scaffold(
        topBar = { RivaliaTopBar("Nuevo torneo", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nombre del torneo") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(game, { game = it.take(40) }, label = { Text("Juego") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Formato", style = MaterialTheme.typography.titleMedium)
            TournamentFormat.entries.forEach { f ->
                Row(Modifier.fillMaxWidth().clickable { format = f }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = format == f, onClick = { format = f })
                    Text(f.label)
                }
            }
            Text("Participantes (${selected.size})", style = MaterialTheme.typography.titleMedium)
            c?.memberIds?.forEach { uid ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        selected = if (uid in selected) selected - uid else selected + uid
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = uid in selected, onCheckedChange = {
                        selected = if (it) selected + uid else selected - uid
                    })
                    Text(c.aliasOf(uid))
                }
            }
            Button(
                onClick = { vm.createTournament(name, game, format, selected.toList(), onCreated) },
                enabled = selected.size >= 2 && name.isNotBlank() && game.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Crear y sortear cuadro") }
        }
    }
}

// ===================================================================  VER TORNEO

@Composable
fun TournamentScreen(vm: TournamentViewModel, myUid: String, onBack: () -> Unit) {
    val tournament by vm.tournament.collectAsStateWithLifecycle()
    val pendingSlots by vm.pendingSlots.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var reporting by remember { mutableStateOf<BracketMatch?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val t = tournament

    if (confirmDelete) ConfirmDialog(
        "Eliminar torneo", "Se borrará el cuadro. Las partidas ya jugadas seguirán contando en el ranking.",
        "Eliminar", onConfirm = { vm.delete(onBack) }, onDismiss = { confirmDelete = false }
    )
    reporting?.let { m ->
        if (t != null) ReportDialog(t, m, onDismiss = { reporting = null }) { r, sa, sb ->
            vm.report(m, r, sa, sb)
            reporting = null
        }
    }

    Scaffold(
        topBar = {
            RivaliaTopBar(t?.name ?: "Torneo", onBack = onBack, actions = {
                if (t != null && t.createdBy == myUid) TextButton(onClick = { confirmDelete = true }) { Text("Eliminar") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (t == null) {
            EmptyState("Cargando torneo…", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("${t.game} · ${t.format.label}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (t.championId != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "🏆 Campeón: ${t.aliases[t.championId] ?: "?"}",
                        Modifier.fillMaxWidth().padding(20.dp),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                }
            }
            if (t.format == TournamentFormat.LEAGUE) {
                item { SectionTitle("Clasificación") }
                item { StandingsTable(t) }
                item { SectionTitle("Partidos") }
                items(t.matches, key = { it.slot }) { m ->
                    BracketCard(t, m, m.slot in pendingSlots) { reporting = m }
                }
            } else {
                val rounds = t.matches.maxOfOrNull { it.round } ?: 1
                t.matches.groupBy { it.round }.toSortedMap().forEach { (round, list) ->
                    item(key = "round$round") { SectionTitle(Brackets.knockoutRoundName(round, rounds)) }
                    items(list, key = { it.slot }) { m ->
                        BracketCard(t, m, m.slot in pendingSlots) { reporting = m }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BracketCard(t: Tournament, m: BracketMatch, pending: Boolean, onReport: () -> Unit) {
    fun name(uid: String?) = if (uid == null) "Por decidir" else t.aliases[uid] ?: "?"
    val canReport = m.playable && !pending
    Card(Modifier.fillMaxWidth().clickable(enabled = canReport, onClick = onReport)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                listOf(m.a, m.b).forEach { uid ->
                    val isWinner = uid != null && uid == m.winner
                    Text(
                        (if (isWinner) "✓ " else "") + if (m.bye && uid == null) "— (pasa sin jugar)" else name(uid),
                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Text(
                when {
                    pending -> "⏳ Sincronizando"
                    m.draw -> "Empate"
                    m.played -> "Jugado"
                    canReport -> "Apuntar ›"
                    else -> ""
                },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StandingsTable(t: Tournament) {
    val rows = Brackets.standings(t.players, t.matches)
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("#" to 28, "Jugador" to 0, "PJ" to 36, "V" to 32, "E" to 32, "D" to 32, "Pts" to 40).forEach { (h, w) ->
                Text(h, if (w == 0) Modifier.weight(1f) else Modifier.width(w.dp), fontWeight = FontWeight.Bold)
            }
        }
        rows.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${i + 1}", Modifier.width(28.dp))
                Text(t.aliases[s.uid] ?: "?", Modifier.weight(1f))
                Text("${s.played}", Modifier.width(36.dp))
                Text("${s.won}", Modifier.width(32.dp))
                Text("${s.drawn}", Modifier.width(32.dp))
                Text("${s.lost}", Modifier.width(32.dp))
                Text("${s.points}", Modifier.width(40.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReportDialog(
    t: Tournament,
    m: BracketMatch,
    onDismiss: () -> Unit,
    onConfirm: (Result, Int?, Int?) -> Unit
) {
    var result by remember { mutableStateOf<Result?>(null) }
    var scoreA by remember { mutableStateOf("") }
    var scoreB by remember { mutableStateOf("") }
    val a = t.aliases[m.a] ?: "?"
    val b = t.aliases[m.b] ?: "?"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$a vs $b") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultSelector(a, b, result, allowDraw = t.format == TournamentFormat.LEAGUE) { result = it }
                ScoreFields(scoreA, scoreB, { scoreA = it }, { scoreB = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { result?.let { onConfirm(it, scoreA.toIntOrNull(), scoreB.toIntOrNull()) } },
                enabled = result != null
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
