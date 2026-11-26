package com.marrdev.gymcoachai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

internal fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Pantalla inicial cuando no hay perfil: Google, correo o registro local (sin cuenta). */
@Composable
fun WelcomeScreen(vm: GymViewModel, onLocal: () -> Unit) {
    val context = LocalContext.current
    var showEmail by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(Icons.Rounded.FitnessCenter, Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("GymCoachAI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Tu coach personal con IA. Inicia sesión para usarlo sin configurar nada, o continúa sin cuenta.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { context.findActivity()?.let { vm.signInGoogle(it) } },
            enabled = vm.googleAvailable && !vm.authBusy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.AccountCircle, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (vm.authBusy) "Conectando…" else "Continuar con Google")
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = { showEmail = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.Email, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Continuar con correo")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onLocal,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Registrarme localmente")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Con cuenta, el coach funciona sin que pongas tu API Key. Sin cuenta, tus datos se quedan en este dispositivo y puedes usar tu propia API Key de OpenAI desde Ajustes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        )
        vm.authMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showEmail && vm.account == null) EmailAuthDialog(vm) { showEmail = false }
}

@Composable
fun EmailAuthDialog(vm: GymViewModel, onClose: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.authMessage = null }
    LaunchedEffect(vm.account) { if (vm.account != null) onClose() }

    AlertDialog(
        onDismissRequest = { if (!vm.authBusy) onClose() },
        title = { Text(if (creating) "Crear cuenta" else "Iniciar sesión") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Contraseña (mínimo 6)") },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostrar u ocultar")
                        }
                    },
                )
                vm.authMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (!creating) {
                    TextButton(onClick = { vm.resetPassword(email) }, enabled = email.contains("@") && !vm.authBusy) {
                        Text("Olvidé mi contraseña")
                    }
                }
                TextButton(onClick = { creating = !creating; vm.authMessage = null }) {
                    Text(if (creating) "Ya tengo cuenta: iniciar sesión" else "No tengo cuenta: crear una")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (creating) vm.signUpEmail(email, password) else vm.signInEmail(email, password) },
                enabled = email.contains("@") && password.length >= 6 && !vm.authBusy,
            ) { Text(if (vm.authBusy) "Un momento…" else if (creating) "Crear cuenta" else "Entrar") }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !vm.authBusy) { Text("Cancelar") } },
    )
}

/** Sección "Cuenta" de Ajustes: iniciar/cerrar sesión y elegir qué API Key usa el coach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSection(vm: GymViewModel) {
    val context = LocalContext.current
    var showEmail by remember { mutableStateOf(false) }
    val acc = vm.account

    SectionTitle("Cuenta")
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                !vm.authAvailable -> Text(
                    "El inicio de sesión no está disponible en esta versión (falta google-services.json). Puedes seguir usando tu propia API Key.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                acc == null -> {
                    Text("Sin sesión", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Es opcional. Con una cuenta el coach funciona sin tu API Key.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { context.findActivity()?.let { vm.signInGoogle(it) } },
                            enabled = vm.googleAvailable && !vm.authBusy,
                        ) { Text("Google") }
                        FilledTonalButton(onClick = { showEmail = true }) { Text("Correo") }
                    }
                }
                else -> {
                    Text(acc.name.ifBlank { acc.email }, fontWeight = FontWeight.SemiBold)
                    if (acc.name.isNotBlank()) Text(acc.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!acc.emailVerified) {
                        Text(
                            "Verifica tu correo (te enviamos un enlace) para usar el coach incluido.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { vm.refreshAccount() }) { Text("Ya verifiqué") }
                            TextButton(onClick = { vm.resendVerification() }) { Text("Reenviar correo") }
                        }
                    }
                    if (vm.backendConfigured) {
                        Text("El coach usa:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                        val options = listOf("account" to "Servicio incluido", "own" to "Mi API Key")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = vm.aiSource == value,
                                    onClick = { vm.setAiSource(value) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                ) { Text(label, maxLines = 1) }
                            }
                        }
                    } else {
                        Text(
                            "El servicio incluido aún no está configurado en esta versión; el coach usará tu API Key.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OutlinedButton(onClick = { vm.signOut() }) { Text("Cerrar sesión") }
                }
            }
            vm.authMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (showEmail && vm.account == null) EmailAuthDialog(vm) { showEmail = false }
}
