@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rivalia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
private fun AuthLayout(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Rivalia", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Tus piques con amigos, convertidos en una liga de verdad.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun ColumnScope.AuthMessagesInColumn(state: AuthUiState) {
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
    }
    state.info?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
    }
    if (state.loading) {
        CircularProgressIndicator(Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally))
    }
}

@Composable
fun LoginScreen(vm: AuthViewModel, onGoRegister: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Correo electrónico") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Contraseña") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Button(
                onClick = { vm.login(email, password) }, enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Entrar") }
            TextButton(onClick = { vm.resetPassword(email) }) { Text("¿Has olvidado la contraseña?") }
            OutlinedButton(onClick = { vm.clearMessages(); onGoRegister() }, modifier = Modifier.fillMaxWidth()) {
                Text("Crear una cuenta nueva")
            }
            AuthMessagesInColumn(state)
        }
    }
}

@Composable
fun RegisterScreen(vm: AuthViewModel, onGoLogin: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var alias by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var birth by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Input)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { birth = pickerState.selectedDateMillis; showPicker = false }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = pickerState, title = { Text("Fecha de nacimiento", Modifier.padding(16.dp)) })
        }
    }

    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Crear cuenta", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = alias, onValueChange = { alias = it.take(20) }, label = { Text("Alias (lo verán tus amigos)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Correo electrónico") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(birth?.let { "Nacimiento: ${formatUtcDate(it)}" } ?: "Fecha de nacimiento")
            }
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Contraseña (mín. 8)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = repeat, onValueChange = { repeat = it }, label = { Text("Repite la contraseña") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Text(
                "Rivalia es para mayores de ${AuthViewModel.MIN_AGE} años. Solo guardamos tu alias y tu correo.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { vm.register(alias, email, password, repeat, birth) }, enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Crear cuenta") }
            TextButton(onClick = { vm.clearMessages(); onGoLogin() }) { Text("¿Ya tienes cuenta? Inicia sesión") }
            AuthMessagesInColumn(state)
        }
    }
}
