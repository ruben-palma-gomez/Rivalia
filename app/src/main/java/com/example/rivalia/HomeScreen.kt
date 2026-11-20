@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rivalia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenCrew: (String) -> Unit) {
    val crews by vm.crews.collectAsStateWithLifecycle()
    val alias by vm.alias.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showAlias by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    if (showCreate) TextInputDialog("Nuevo grupo", "Nombre del grupo", confirm = "Crear",
        onConfirm = { vm.createCrew(it, onOpenCrew) }, onDismiss = { showCreate = false })
    if (showJoin) TextInputDialog("Unirse a un grupo", "Código de 6 caracteres", confirm = "Unirme",
        onConfirm = { vm.joinCrew(it, onOpenCrew) }, onDismiss = { showJoin = false })
    if (showAlias) TextInputDialog("Cambiar alias", "Alias", initial = alias, confirm = "Guardar",
        onConfirm = { vm.updateAlias(it) }, onDismiss = { showAlias = false })
    if (confirmLogout) ConfirmDialog(
        "Cerrar sesión",
        if (pending > 0) "Tienes $pending partidas sin subir. Si cierras sesión ahora se perderán."
        else "Se borrarán los datos guardados en este móvil.",
        "Cerrar sesión", onConfirm = { vm.logout {} }, onDismiss = { confirmLogout = false }
    )

    Scaffold(
        topBar = {
            RivaliaTopBar("Rivalia", actions = {
                TextButton(onClick = { confirmLogout = true }) { Text("Salir") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Hola, ${alias.ifBlank { "jugador" }}", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { showAlias = true }) { Text("Editar alias") }
                }
            }
            if (busy || syncing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (pending > 0) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$pending partida(s) guardadas en el móvil, pendientes de sincronizar.",
                            Modifier.weight(1f)
                        )
                        TextButton(onClick = vm::syncNow) { Text("Reintentar") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showCreate = true }, Modifier.weight(1f)) { Text("Crear grupo") }
                    OutlinedButton(onClick = { showJoin = true }, Modifier.weight(1f)) { Text("Unirme con código") }
                }
            }
            item { SectionTitle("Mis grupos") }
            if (crews.isEmpty()) item {
                EmptyState("Aún no estás en ningún grupo. Crea uno e invita a tus amigos con el código.")
            }
            items(crews, key = { it.id }) { crew ->
                Card(Modifier.fillMaxWidth().clickable { onOpenCrew(crew.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(crew.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${crew.memberIds.size} miembros · Temporada ${crew.season} · Código ${crew.code}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
