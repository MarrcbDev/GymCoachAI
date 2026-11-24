package com.example.my_fitness_app

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Columna con scroll vertical para pantallas largas; sube con el teclado. */
@Composable
private fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

// ───────────────────────────── Inicio ─────────────────────────────

@Composable
fun Dashboard(vm: GymViewModel, navigate: (String) -> Unit) {
    val u = vm.user
    val bmi = if (u != null && u.height > 0) u.weight / (u.height * u.height) else null
    val watch = vm.lastWatchSummary()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("Hola${u?.name?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Hoy es un buen día para avanzar.",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navigate("Rutinas") }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar entrenamiento")
                    }
                }
            }
        }
        item { SectionTitle("Tu resumen") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Peso", u?.let { "${it.weight} kg" } ?: "—", Icons.Rounded.MonitorWeight, Modifier.weight(1f))
                MetricCard("IMC", bmi?.let { "%.1f".format(it) } ?: "—", Icons.Rounded.Speed, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Estatura", u?.let { "${it.height} m" } ?: "—", Icons.Rounded.Straighten, Modifier.weight(1f))
                MetricCard("Objetivo", u?.goals?.take(16)?.ifBlank { null } ?: "Configurar", Icons.Rounded.TrackChanges, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Atajos") }
        item { FeatureCard("Coach inteligente", "Resuelve dudas y crea hábitos con IA.", Icons.Rounded.AutoAwesome) { navigate("Coach IA") } }
        item { FeatureCard("Recordatorios", "Organiza tus próximas sesiones.", Icons.Rounded.NotificationsActive) { navigate("Más") } }
        item {
            FeatureCard("Tu reloj", watch ?: "Sincroniza tus entrenamientos desde Wear OS.", Icons.Rounded.Watch) { navigate("Más") }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────────────────────────── Coach IA ─────────────────────────────

@Composable
fun ChatScreen(vm: GymViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val count = vm.messages.size + if (vm.busy) 1 else 0
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (!vm.hasApiKey) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VpnKey, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text("Falta tu API Key de OpenAI. Pégala en Más → OpenAI.", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (vm.messages.isEmpty()) {
                item {
                    Text("Tu coach está listo. Pregunta por rutinas, técnica o hábitos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(vm.messages) { msg ->
                val mine = msg.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(
                            topStart = 20.dp, topEnd = 20.dp,
                            bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp,
                        ),
                    ) { Text(msg.content, Modifier.widthIn(max = 300.dp).padding(horizontal = 14.dp, vertical = 10.dp)) }
                }
            }
            if (vm.busy) item { Text("El coach está pensando…", color = MaterialTheme.colorScheme.primary) }
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f),
                placeholder = { Text("Escribe tu consulta…") },
                shape = RoundedCornerShape(26.dp), maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { vm.send(input); input = "" },
                enabled = input.isNotBlank() && !vm.busy,
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Enviar") }
        }
    }
}

// ───────────────────────────── Rutinas ─────────────────────────────

private data class ModeInfo(val name: String, val description: String, val icon: ImageVector)

private val modes = listOf(
    ModeInfo("Fuerza", "Series, repeticiones y descansos", Icons.Rounded.FitnessCenter),
    ModeInfo("Cardio", "Tiempo, ritmo y distancia", Icons.Rounded.Favorite),
    ModeInfo("Movilidad", "Flexibilidad y rango de movimiento", Icons.Rounded.SelfImprovement),
    ModeInfo("Resistencia", "Trabajo por intervalos", Icons.Rounded.Timer),
    ModeInfo("Recuperación", "Sesiones suaves y descanso", Icons.Rounded.Spa),
)

@Composable
fun WorkoutsScreen() {
    var selected by remember { mutableStateOf("Fuerza") }
    ScrollColumn {
        SectionTitle("Modos de entrenamiento")
        modes.forEach { mode ->
            val on = mode.name == selected
            Card(
                onClick = { selected = mode.name },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(mode.icon)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.name, fontWeight = FontWeight.SemiBold)
                        Text(mode.description, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
                    }
                    if (on) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text("Modo seleccionado: $selected", color = MaterialTheme.colorScheme.primary)
    }
}

// ───────────────────────────── Más ─────────────────────────────

@Composable
fun MoreScreen(vm: GymViewModel) {
    var reminder by remember { mutableStateOf("") }
    var alarmTime by remember { mutableStateOf("07:00") }
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    ScrollColumn {
        SectionTitle("Apariencia")
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Palette)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Colores dinámicos", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (dynamicAvailable) "Usa los colores de tu fondo de pantalla (Material You). Apagado: azul y negro."
                        else "Disponible desde Android 12. Ahora usa azul y negro.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.dynamicColor && dynamicAvailable, onCheckedChange = { vm.setDynamicColor(it) }, enabled = dynamicAvailable)
            }
        }

        SectionTitle("Organización")
        OutlinedTextField(
            reminder, { reminder = it }, Modifier.fillMaxWidth(),
            label = { Text("Nuevo recordatorio") }, shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
        )
        FilledTonalButton(onClick = { reminder = "" }, enabled = reminder.isNotBlank()) {
            Text("Guardar recordatorio (pendiente de persistencia)")
        }
        OutlinedTextField(
            alarmTime, { alarmTime = it }, Modifier.fillMaxWidth(),
            label = { Text("Hora de alarma (HH:mm)") }, shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.Alarm, null) },
        )
        Text(
            "La programación real de alarmas se incorpora en la siguiente iteración con permisos de Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("OpenAI")
        OutlinedTextField(
            keyInput, { keyInput = it }, Modifier.fillMaxWidth(),
            label = { Text("Tu API Key (sk-…)") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Rounded.VpnKey, null) },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostrar u ocultar clave")
                }
            },
        )
        Button(onClick = { if (vm.saveApiKey(keyInput)) keyInput = "" }, enabled = keyInput.isNotBlank()) {
            Text("Guardar clave de forma cifrada")
        }
        vm.keyMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            if (vm.hasApiKey) "API Key guardada en este dispositivo." else "No hay una API Key configurada.",
            color = if (vm.hasApiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "La clave se cifra con Android Keystore. Para una app distribuida, un backend sigue siendo la opción más segura.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ───────────────────────────── Perfil y registro ─────────────────────────────

@Composable
fun ProfileScreen(vm: GymViewModel, close: () -> Unit) {
    ProfileForm(initial = vm.user, submitLabel = "Guardar perfil", showBack = true, onBack = close) { name, sex, age, h, w, goals ->
        vm.saveProfile(name, sex, age, h, w, goals)
        close()
    }
}

@Composable
fun RegistrationScreen(vm: GymViewModel) {
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        IconBadge(Icons.Rounded.FitnessCenter, Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text("Te damos la bienvenida", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Cuéntanos sobre ti para personalizar tu coach.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        ProfileForm(initial = null, submitLabel = "Empezar", showBack = false, onBack = {}) { name, sex, age, h, w, goals ->
            vm.saveProfile(name, sex, age, h, w, goals)
        }
    }
}

private fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileForm(
    initial: UserEntity?,
    submitLabel: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onSubmit: (name: String, sex: String, age: Int, height: Double, weight: Double, goals: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var sex by remember { mutableStateOf(initial?.sex ?: "") }
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "") }
    var height by remember { mutableStateOf(initial?.height?.toString() ?: "") }
    var weight by remember { mutableStateOf(initial?.weight?.toString() ?: "") }
    var goals by remember { mutableStateOf(initial?.goals ?: "") }

    val ageV = age.toIntOrNull()
    val heightV = parseNumber(height)
    val weightV = parseNumber(weight)
    val ageOk = ageV != null && ageV in 10..100
    val heightOk = heightV != null && heightV in 0.8..2.5
    val weightOk = weightV != null && weightV in 25.0..350.0
    val valid = name.isNotBlank() && sex.isNotBlank() && ageOk && heightOk && weightOk

    val sexOptions = listOf("Hombre", "Mujer", "Otro")

    ScrollColumn {
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Text("Sexo", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            sexOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = sex == option,
                    onClick = { sex = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = sexOptions.size),
                ) { Text(option) }
            }
        }
        NumberField("Edad", age, { age = it }, KeyboardType.Number, error = "Entre 10 y 100", invalid = age.isNotEmpty() && !ageOk)
        NumberField("Estatura (m)", height, { height = it }, KeyboardType.Decimal, error = "Usa metros, por ejemplo 1.75", invalid = height.isNotEmpty() && !heightOk)
        NumberField("Peso (kg)", weight, { weight = it }, KeyboardType.Decimal, error = "Entre 25 y 350 kg", invalid = weight.isNotEmpty() && !weightOk)
        OutlinedTextField(
            goals, { goals = it }, Modifier.fillMaxWidth(), label = { Text("Objetivos") },
            shape = RoundedCornerShape(16.dp), minLines = 2,
        )
        Button(
            onClick = { onSubmit(name, sex, ageV!!, heightV!!, weightV!!, goals) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(submitLabel) }
        if (showBack) TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun NumberField(
    label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, error: String, invalid: Boolean,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
        isError = invalid,
        supportingText = { if (invalid) Text(error) },
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
    )
}
