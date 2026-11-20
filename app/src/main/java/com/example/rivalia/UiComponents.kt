@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rivalia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Barra superior común con botón de volver opcional. */
@Composable
fun RivaliaTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("‹ Volver") }
            }
        },
        actions = { actions() }
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
    )
}

/** Selector desplegable de jugador. */
@Composable
fun PlayerPicker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "Elegir jugador")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (uid, alias) ->
                    DropdownMenuItem(text = { Text(alias) }, onClick = { onSelect(uid); open = false })
                }
            }
        }
    }
}

/** Elección del resultado: gana A, empate o gana B. */
@Composable
fun ResultSelector(
    aliasA: String,
    aliasB: String,
    result: Result?,
    allowDraw: Boolean,
    onChange: (Result) -> Unit
) {
    Column {
        listOfNotNull(
            Result.A to "Gana $aliasA",
            if (allowDraw) Result.DRAW to "Empate" else null,
            Result.B to "Gana $aliasB"
        ).forEach { (r, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = result == r, onClick = { onChange(r) })
                Text(text)
            }
        }
    }
}

@Composable
fun ScoreFields(scoreA: String, scoreB: String, onA: (String) -> Unit, onB: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = scoreA, onValueChange = { v -> onA(v.filter { it.isDigit() }.take(3)) },
            label = { Text("Marcador A") }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = scoreB, onValueChange = { v -> onB(v.filter { it.isDigit() }.take(3)) },
            label = { Text("Marcador B") }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirm: String = "Aceptar",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(value); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Gráfica de líneas sencilla de la evolución del ELO. */
@Composable
fun EloChart(points: List<Int>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (points.size < 2) {
        EmptyState("Juega al menos una partida para ver tu evolución.", modifier)
        return
    }
    val min = (points.min() - 10).toFloat()
    val max = (points.max() + 10).toFloat()
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Máx. ${points.max()}", style = MaterialTheme.typography.labelSmall)
            Text("Actual ${points.last()}", style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
            val stepX = size.width / (points.size - 1)
            fun y(v: Int) = size.height - (v - min) / (max - min) * size.height
            val base = y(Elo.INITIAL).coerceIn(0f, size.height)
            drawLine(grid, Offset(0f, base), Offset(size.width, base), strokeWidth = 2f)
            for (i in 1 until points.size) {
                drawLine(
                    line, Offset((i - 1) * stepX, y(points[i - 1])), Offset(i * stepX, y(points[i])),
                    strokeWidth = 6f, cap = StrokeCap.Round
                )
            }
        }
        Text("Línea gris: ELO inicial (${Elo.INITIAL})", style = MaterialTheme.typography.labelSmall)
    }
}

fun formatDate(millis: Long, withTime: Boolean = true): String {
    val pattern = if (withTime) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy"
    return SimpleDateFormat(pattern, Locale.forLanguageTag("es-ES")).format(Date(millis))
}

/** El selector de fechas de Material devuelve la medianoche en UTC. */
fun formatUtcDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-ES"))
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

fun resultText(m: Match): String {
    val score = if (m.scoreA != null && m.scoreB != null) " (${m.scoreA}-${m.scoreB})" else ""
    return when (m.result) {
        Result.A -> "${m.aliasA} gana a ${m.aliasB}$score"
        Result.B -> "${m.aliasB} gana a ${m.aliasA}$score"
        Result.DRAW -> "${m.aliasA} y ${m.aliasB} empatan$score"
    }
}
