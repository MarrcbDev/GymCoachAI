package com.marrdev.gymcoachai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marrdev.gymcoachai.alarms.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun daysText(r: ReminderEntity): String = when {
    r.daysMask == 127 -> "Todos los días"
    r.daysMask != 0 -> DAY_LABELS.filterIndexed { i, _ -> r.daysMask and (1 shl i) != 0 }.joinToString(" ")
    r.dateEpochDay != null -> LocalDate.ofEpochDay(r.dateEpochDay).format(dateFormat)
    r.hour >= 0 -> "Una vez"
    else -> ""
}

@Composable
fun AgendaScreen(vm: GymViewModel) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Boolean?>(null) } // null = cerrado; valor = ¿es alarma?
    val list = if (tab == 0) notes else alarms

    // En Android 12+ las alarmas exactas necesitan el permiso "Alarmas y recordatorios".
    val context = LocalContext.current
    var canExact by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        canExact = ReminderScheduler.canScheduleExact(context)
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Notas") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Alarmas") })
            }
            if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Card(
                    modifier = Modifier.padding(top = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Para que las alarmas suenen a la hora exacta, permite \"Alarmas y recordatorios\" para esta app. Sin eso pueden retrasarse unos minutos.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        FilledTonalButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                            )
                        }) { Text("Permitir") }
                    }
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (tab == 0) "Planifica una nota con el botón +, o pídesela al coach."
                        else "Programa una alarma de entrenamiento con el botón +.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { r ->
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    if (r.hour >= 0) {
                                        Text("%02d:%02d".format(r.hour, r.minute), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Text(r.title, fontWeight = FontWeight.SemiBold)
                                    if (r.body.isNotBlank()) Text(r.body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    val extra = daysText(r)
                                    if (extra.isNotEmpty()) Text(extra, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                }
                                if (r.hour >= 0) {
                                    Switch(checked = r.enabled, onCheckedChange = { vm.saveReminder(r.copy(enabled = it)) })
                                }
                                IconButton(onClick = { vm.deleteReminder(r) }) { Icon(Icons.Rounded.Delete, "Eliminar") }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { dialog = (tab == 1) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
        ) { Icon(Icons.Rounded.Add, "Nuevo") }
    }

    dialog?.let { isAlarm ->
        ReminderDialog(isAlarm, onDismiss = { dialog = null }) { vm.saveReminder(it); dialog = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(isAlarm: Boolean, onDismiss: () -> Unit, onSave: (ReminderEntity) -> Unit) {
    val suggested = remember { LocalTime.now().plusHours(1) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(suggested.hour) }
    var minute by remember { mutableIntStateOf(0) }
    var notify by remember { mutableStateOf(isAlarm) } // las alarmas siempre llevan hora
    var mask by remember { mutableIntStateOf(0) }
    var day by remember { mutableStateOf<Long?>(null) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAlarm) "Nueva alarma" else "Nueva nota") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(),
                    label = { Text(if (isAlarm) "Entrenamiento (ej. Pierna)" else "Título") },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                )
                if (!isAlarm) {
                    OutlinedTextField(
                        body, { body = it }, Modifier.fillMaxWidth(),
                        label = { Text("Detalle (opcional)") }, minLines = 2, shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(day?.let { "Fecha: ${LocalDate.ofEpochDay(it).format(dateFormat)}" } ?: "Fecha (opcional)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Avisarme a una hora", Modifier.weight(1f))
                        Switch(checked = notify, onCheckedChange = { notify = it })
                    }
                }
                if (notify) {
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Hora: %02d:%02d".format(hour, minute))
                    }
                }
                if (isAlarm) {
                    Text("Repetir", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DAY_LABELS.forEachIndexed { i, label ->
                            val on = mask and (1 shl i) != 0
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { mask = mask xor (1 shl i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (mask == 0) {
                        Text("Sin días: suena una sola vez.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        ReminderEntity(
                            title = title.trim(), body = body.trim(), isAlarm = isAlarm,
                            hour = if (notify) hour else -1, minute = if (notify) minute else 0,
                            daysMask = if (isAlarm) mask else 0,
                            dateEpochDay = if (isAlarm) null else day,
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (showTime) {
        val state = rememberTimePickerState(hour, minute, true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
            confirmButton = { TextButton(onClick = { hour = state.hour; minute = state.minute; showTime = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancelar") } },
        )
    }
    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showDate = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}
