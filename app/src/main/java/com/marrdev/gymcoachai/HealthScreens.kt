package com.marrdev.gymcoachai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val KINDS = listOf("Lesión", "Condición", "Otra")

/** Lista de lesiones y condiciones médicas, guardadas solo en el dispositivo. */
@Composable
internal fun HealthSection(vm: GymViewModel) {
    val items by vm.conditions.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm.openHealthDialog) {
        if (vm.openHealthDialog) {
            showDialog = true
            vm.openHealthDialog = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle("Lesiones y condiciones") }
            FilledTonalButton(onClick = { showDialog = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar")
            }
        }
        if (items.isEmpty()) {
            Text("Sin condiciones anotadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { c ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            c.kind + if (c.notes.isBlank()) "" else " · ${c.notes}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.deleteCondition(c) }) { Icon(Icons.Rounded.Delete, "Eliminar") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usarlas en las recomendaciones del coach", Modifier.weight(1f))
            Switch(checked = vm.shareHealth, onCheckedChange = { vm.setShareHealth(it) })
        }
        Text(
            "Se guardan solo en este dispositivo. Si lo dejas activado, el coach de IA las recibe como contexto para adaptar tus ejercicios. No sustituyen la opinión de un profesional de la salud.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showDialog) {
        ConditionDialog(onDismiss = { showDialog = false }) { name, kind, notes ->
            vm.addCondition(name, kind, notes)
            showDialog = false
        }
    }
}

@Composable
private fun ConditionDialog(onDismiss: () -> Unit, onSave: (name: String, kind: String, notes: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(KINDS.first()) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar lesión o condición") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text("Ej. rodilla izquierda, asma") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KINDS.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) }) }
                }
                OutlinedTextField(
                    notes, { notes = it }, Modifier.fillMaxWidth(),
                    label = { Text("Notas (opcional)") }, minLines = 2, shape = RoundedCornerShape(16.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, kind, notes) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Se muestra una sola vez al entrar a la app, si no hay condiciones anotadas. */
@Composable
internal fun HealthPromptDialog(vm: GymViewModel, onAdd: () -> Unit) {
    AlertDialog(
        onDismissRequest = { vm.markHealthAsked() },
        title = { Text("¿Tienes lesiones o condiciones médicas?") },
        text = {
            Text(
                "Cuéntanos si tienes alguna (rodilla, hombro, espalda, asma, hipertensión…) para adaptar tus ejercicios y recomendaciones. Se guardan solo en tu dispositivo.",
            )
        },
        confirmButton = { TextButton(onClick = { vm.markHealthAsked(); onAdd() }) { Text("Sí, agregar") } },
        dismissButton = { TextButton(onClick = { vm.markHealthAsked() }) { Text("No tengo") } },
    )
}
