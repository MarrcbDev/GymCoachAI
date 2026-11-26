package com.marrdev.gymcoachai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WorkoutsScreen(vm: GymViewModel) {
    val w = vm.workout
    if (w.phase == Phase.IDLE) WorkoutHome(vm, w) else RunningWorkout(vm, w)
}

@Composable
private fun WorkoutHome(vm: GymViewModel, w: WorkoutState) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    ScrollColumn {
        SectionTitle("Modos de entrenamiento")
        MODE_SPECS.forEach { spec ->
            val on = spec.name == w.mode
            Card(
                onClick = { vm.selectMode(spec.name) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(spec.icon)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(spec.name, fontWeight = FontWeight.SemiBold)
                            Text(spec.description, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
                        }
                        if (on) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (on) {
                        Spacer(Modifier.height(12.dp))
                        if (spec.name != "Cardio") Stepper("Rondas", "${w.rounds}", { vm.adjustRounds(-1) }, { vm.adjustRounds(1) })
                        Stepper(
                            if (spec.name == "Cardio") "Duración" else "Trabajo",
                            if (spec.name == "Cardio") "${w.work / 60} min" else "${w.work} s",
                            { vm.adjustWork(-1) }, { vm.adjustWork(1) },
                        )
                        if (spec.name != "Cardio") Stepper("Descanso", "${w.rest} s", { vm.adjustRest(-1) }, { vm.adjustRest(1) })
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.startWorkout() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Empezar")
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle("Mis rutinas") }
            FilledTonalButton(onClick = { showDialog = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nueva")
            }
        }
        if (routines.isEmpty()) {
            Text(
                "Aún no tienes rutinas. Crea una aquí o pídesela al coach en la pestaña Coach IA.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        routines.forEach { r ->
            val exercises = r.exercises.lines().filter { it.isNotBlank() }
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${r.mode} · ${exercises.size} ejercicios · ${r.rounds} rondas",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (exercises.isNotEmpty()) {
                            Text(
                                exercises.joinToString(" · "), maxLines = 2,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { vm.startRoutine(r) }) { Icon(Icons.Rounded.PlayArrow, "Iniciar rutina", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { vm.deleteRoutine(r) }) { Icon(Icons.Rounded.Delete, "Eliminar rutina") }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            SectionTitle("Historial")
            sessions.take(8).forEach { s ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.routineName.ifBlank { s.mode }, fontWeight = FontWeight.SemiBold)
                            Text(
                                fmtWhen(s.startMs) + if (s.source == "watch") " · reloj" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(fmtClock(((s.endMs - s.startMs) / 1000).toInt()), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showDialog) {
        RoutineDialog(onDismiss = { showDialog = false }) { vm.saveRoutine(it); showDialog = false }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = LocalContentColor.current.copy(alpha = 0.75f))
        IconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, "Menos") }
        Text(value, Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, "Más") }
    }
}

@Composable
private fun RoutineDialog(onDismiss: () -> Unit, onSave: (RoutineEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(MODE_SPECS.first().name) }
    var exercises by remember { mutableStateOf("") }
    var rounds by remember { mutableIntStateOf(MODE_SPECS.first().rounds) }
    var work by remember { mutableIntStateOf(MODE_SPECS.first().work) }
    var rest by remember { mutableIntStateOf(MODE_SPECS.first().rest) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva rutina") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre (ej. Pierna)") },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MODE_SPECS.forEach { spec ->
                        FilterChip(
                            selected = mode == spec.name,
                            onClick = { mode = spec.name; rounds = spec.rounds; work = spec.work; rest = spec.rest },
                            label = { Text(spec.name) },
                        )
                    }
                }
                OutlinedTextField(
                    exercises, { exercises = it }, Modifier.fillMaxWidth(),
                    label = { Text("Ejercicios (uno por línea)") }, minLines = 3, shape = RoundedCornerShape(16.dp),
                )
                if (mode != "Cardio") Stepper("Rondas", "$rounds", { rounds = (rounds - 1).coerceAtLeast(1) }, { rounds = (rounds + 1).coerceAtMost(30) })
                Stepper(
                    if (mode == "Cardio") "Duración" else "Trabajo",
                    if (mode == "Cardio") "${work / 60} min" else "$work s",
                    { work = (work - (if (mode == "Cardio") 60 else 5)).coerceAtLeast(if (mode == "Cardio") 60 else 5) },
                    { work = (work + (if (mode == "Cardio") 60 else 5)).coerceAtMost(7200) },
                )
                if (mode != "Cardio") Stepper("Descanso", "$rest s", { rest = (rest - 5).coerceAtLeast(0) }, { rest = (rest + 5).coerceAtMost(600) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        RoutineEntity(
                            name = name.trim(), mode = mode,
                            exercises = exercises.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
                            rounds = rounds, workSec = work, restSec = rest,
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RunningWorkout(vm: GymViewModel, w: WorkoutState) {
    val view = LocalView.current
    DisposableEffect(w.running) {
        view.keepScreenOn = w.running
        onDispose { view.keepScreenOn = false }
    }

    val progress = if (w.phase == Phase.DONE) 1f else (w.left.toFloat() / w.phaseTotal).coerceIn(0f, 1f)
    val anim by animateFloatAsState(progress, tween(900, easing = LinearEasing), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary
    val title = when (w.phase) {
        Phase.WORK -> "Trabajo"
        Phase.REST -> "Descanso"
        Phase.DONE -> "Sesión guardada"
        else -> ""
    }

    ScrollColumn {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(w.routineName.ifBlank { w.mode }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (w.phase == Phase.WORK && w.currentExercise.isNotEmpty()) {
                Text(w.currentExercise, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 18.dp.toPx()
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    rotate(-90f) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(c1, c2, c1)),
                            startAngle = 0f, sweepAngle = 360f * anim, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmtClock(if (w.phase == Phase.DONE) w.elapsed else w.left), fontSize = 54.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (w.phase == Phase.DONE) "Duración total" else "Ronda ${w.round} de ${w.rounds}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (w.phase == Phase.DONE) {
                Button(onClick = { vm.resetWorkout() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Listo") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.togglePause() }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(if (w.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (w.running) "Pausa" else "Seguir")
                    }
                    OutlinedButton(onClick = { vm.finishWorkout() }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Terminar")
                    }
                }
            }
        }
    }
}
