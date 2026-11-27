package com.marrdev.gymcoachai

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marrdev.gymcoachai.alarms.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────────────────────── Piezas comunes ─────────────────────────────

/** Columna con scroll vertical para pantallas largas; sube con el teclado. */
@Composable
internal fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

internal fun fmtClock(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

private val spanish = Locale("es")
internal fun fmtWhen(ms: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", spanish).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** Foto de perfil redonda; si no hay foto muestra un icono. */
@Composable
internal fun ProfileAvatar(path: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = PhotoUtils.load(path)?.asImageBitmap()
    }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = "Foto de perfil", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Person, null, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

// ───────────────────────────── Inicio ─────────────────────────────

@Composable
fun Dashboard(vm: GymViewModel, navigate: (String) -> Unit) {
    val u = vm.user
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val bmi = if (u != null && u.height > 0) u.weight / (u.height * u.height) else null
    val watch = vm.lastWatchSummary()

    val weekStart = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val week = sessions.filter { it.startMs >= weekStart }
    val minutes = week.sumOf { (it.endMs - it.startMs) / 60000 }.toInt()
    val next = alarms.filter { it.enabled }
        .mapNotNull { r -> ReminderScheduler.nextTrigger(r)?.let { r to it } }
        .minByOrNull { it.second }

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
        item { SectionTitle("Esta semana") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Sesiones", "${week.size}", Icons.Rounded.FitnessCenter, Modifier.weight(1f))
                MetricCard("Minutos", "$minutes", Icons.Rounded.Timer, Modifier.weight(1f))
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
        item {
            FeatureCard(
                "Próxima alarma",
                next?.let { "${it.first.title} · ${fmtWhen(it.second)}" } ?: "Sin alarmas activas. Crea una en Agenda.",
                Icons.Rounded.Alarm,
            ) { navigate("Agenda") }
        }
        item { FeatureCard("Coach inteligente", "Pide rutinas, planes y notas a tu coach.", Icons.Rounded.AutoAwesome) { navigate("Coach IA") } }
        item {
            FeatureCard("Tu reloj", watch ?: "Sincroniza tus entrenamientos desde Wear OS.", Icons.Rounded.Watch) { navigate("Rutinas") }
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

private val quickPrompts = listOf(
    "Crea una rutina de pierna para hoy",
    "Planifica mi semana de entrenamiento",
    "¿Cómo mejoro mi técnica de sentadilla?",
)

@Composable
fun ChatScreen(vm: GymViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val count = vm.messages.size + if (vm.busy) 1 else 0
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull("Modelo: ${vm.aiModel}", vm.quotaText.takeIf { vm.useBackend }).joinToString(" · "),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(onClick = { vm.clearChat() }, enabled = vm.messages.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep, "Borrar conversación")
            }
        }
        if (!vm.canChat) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VpnKey, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (vm.authAvailable && vm.account == null) "Inicia sesión o pega tu API Key de OpenAI en Ajustes."
                        else "Falta tu API Key de OpenAI. Pégala en Ajustes.",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
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
                    Text(
                        "Tu coach responde sobre entrenamiento, técnica, recuperación y hábitos. También puede crear rutinas, iniciar entrenamientos y planificar notas o alarmas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(quickPrompts) { prompt ->
                    SuggestionChip(onClick = { vm.send(prompt) }, label = { Text(prompt) }, enabled = !vm.busy)
                }
            }
            items(vm.messages, key = { it.id }) { msg ->
                val mine = msg.role == "user"
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Surface(
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(
                            topStart = 20.dp, topEnd = 20.dp,
                            bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp,
                        ),
                    ) { Text(msg.content, Modifier.widthIn(max = 300.dp).padding(horizontal = 14.dp, vertical = 10.dp)) }
                    msg.suggestions.forEachIndexed { index, s ->
                        FilledTonalButton(
                            onClick = { vm.applySuggestion(msg.id, index) },
                            modifier = Modifier.padding(top = 6.dp),
                        ) { Text(AiAssistant.label(s), maxLines = 1) }
                    }
                }
            }
            if (vm.busy) item { Text("El coach está pensando…", color = MaterialTheme.colorScheme.primary) }
        }
        vm.chatError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }
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

// ───────────────────────────── Ajustes ─────────────────────────────

@Composable
fun SettingsScreen(vm: GymViewModel) {
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    ScrollColumn {
        AccountSection(vm)

        if (vm.account != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle("Sincronización en la nube")
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Perfil, rutinas, recordatorios y entrenamientos se sincronizan con tu cuenta. Los chats y las API Keys permanecen en este dispositivo.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.syncNow() }, enabled = !vm.syncBusy) {
                        Text(if (vm.syncBusy) "Sincronizando…" else "Sincronizar ahora")
                    }
                    vm.syncMessage?.let { Text(it, color = if (it.startsWith("No se pudo")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                Switch(checked = vm.dynamicColor && dynamicAvailable, onCheckedChange = { vm.updateDynamicColor(it) }, enabled = dynamicAvailable)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("Modelo de IA")
        ModelPicker(vm)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("Tu propia API Key de OpenAI (opcional)")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(vm: GymViewModel) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = vm.aiModel,
            onValueChange = { vm.updateModel(it) },
            readOnly = vm.useBackend,
            label = { Text("Modelo que usa el coach") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vm.models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = { vm.updateModel(model); expanded = false },
                    leadingIcon = { if (model == vm.aiModel) Icon(Icons.Rounded.CheckCircle, null) },
                )
            }
        }
    }
    FilledTonalButton(onClick = { vm.refreshModels() }, enabled = vm.canChat && !vm.modelsLoading) {
        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (vm.modelsLoading) "Consultando…" else "Cargar modelos disponibles")
    }
    Text(
        vm.modelsMessage ?: if (vm.useBackend) "Con el servicio incluido solo puedes elegir entre los modelos permitidos."
        else "Elige de la lista o escribe el nombre exacto de un modelo. Los modelos más grandes responden mejor pero cuestan más.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
    )
}

// ───────────────────────────── Perfil y registro ─────────────────────────────

@Composable
fun ProfileScreen(vm: GymViewModel, close: () -> Unit) {
    ScrollColumn {
        ProfileFormFields(
            initial = vm.user, initialName = "", submitLabel = "Guardar perfil", showBack = true, onBack = close,
            extra = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HealthSection(vm)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            },
        ) { name, sex, age, h, w, goals, photo ->
            vm.saveProfile(name, sex, age, h, w, goals, photo)
            close()
        }
    }
}

/** Primer perfil local. Si hay sesión iniciada, el nombre llega precargado desde la cuenta. */
@Composable
fun RegistrationScreen(vm: GymViewModel, onBack: (() -> Unit)?) {
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
        ScrollColumn {
            ProfileFormFields(
                initial = null, initialName = vm.account?.name.orEmpty(), submitLabel = "Empezar", showBack = false, onBack = {},
            ) { name, sex, age, h, w, goals, photo ->
                vm.saveProfile(name, sex, age, h, w, goals, photo)
            }
            val acc = vm.account
            if (acc != null) {
                TextButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Usar otra cuenta (cerrar sesión)") }
            } else if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
            }
        }
    }
}

private fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ProfileFormFields(
    initial: UserEntity?,
    initialName: String,
    submitLabel: String,
    showBack: Boolean,
    onBack: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
    onSubmit: (name: String, sex: String, age: Int, height: Double, weight: Double, goals: String, photo: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: initialName) }
    var sex by remember { mutableStateOf(initial?.sex ?: "") }
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "") }
    var height by remember { mutableStateOf(initial?.height?.toString() ?: "") }
    var weight by remember { mutableStateOf(initial?.weight?.toString() ?: "") }
    var goals by remember { mutableStateOf(initial?.goals ?: "") }
    var photo by remember { mutableStateOf(initial?.imagePath ?: "") }
    var photoError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val saved = PhotoUtils.saveProfilePhoto(context, uri)
            if (saved != null) {
                // Si la foto actual era una elegida en esta misma pantalla, no se deja huérfana.
                if (photo != (initial?.imagePath ?: "")) PhotoUtils.deleteOld(context, photo)
                photo = saved
                photoError = null
            } else {
                photoError = "No se pudo leer esa imagen."
            }
        }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val ageV = age.toIntOrNull()
    val heightV = parseNumber(height)
    val weightV = parseNumber(weight)
    val ageOk = ageV != null && ageV in 10..100
    val heightOk = heightV != null && heightV in 0.8..2.5
    val weightOk = weightV != null && weightV in 25.0..350.0
    val valid = name.isNotBlank() && sex.isNotBlank() && ageOk && heightOk && weightOk

    val sexOptions = listOf("Hombre", "Mujer", "Otro")

    run {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                ProfileAvatar(photo, 112.dp)
                FilledIconButton(
                    onClick = { pickPhoto() },
                    modifier = Modifier.align(Alignment.BottomEnd).size(36.dp),
                ) { Icon(Icons.Rounded.PhotoCamera, "Cambiar foto", Modifier.size(18.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickPhoto() }) { Text(if (photo.isBlank()) "Elegir foto" else "Cambiar foto") }
                if (photo.isNotBlank()) {
                    TextButton(onClick = {
                        if (photo != (initial?.imagePath ?: "")) PhotoUtils.deleteOld(context, photo)
                        photo = ""
                    }) { Text("Quitar") }
                }
            }
            photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
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
        extra()
        Button(
            onClick = { onSubmit(name, sex, ageV!!, heightV!!, weightV!!, goals, photo) },
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
