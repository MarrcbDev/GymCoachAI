package com.marrdev.gymcoachai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/** Modo de entrenamiento con valores por defecto (segundos). Cardio usa work como duración total. */
data class ModeSpec(
    val name: String, val description: String, val icon: ImageVector,
    val work: Int, val rest: Int, val rounds: Int,
)

val MODE_SPECS = listOf(
    ModeSpec("Fuerza", "Series con descanso largo", Icons.Rounded.FitnessCenter, 40, 120, 5),
    ModeSpec("Cardio", "Ritmo constante sin pausas", Icons.Rounded.Favorite, 1200, 0, 1),
    ModeSpec("Movilidad", "Flexibilidad y rango de movimiento", Icons.Rounded.SelfImprovement, 40, 20, 8),
    ModeSpec("Resistencia", "Trabajo por intervalos", Icons.Rounded.Timer, 30, 15, 10),
    ModeSpec("Recuperación", "Sesión suave y descanso activo", Icons.Rounded.Spa, 45, 45, 6),
)

fun modeSpec(name: String): ModeSpec =
    MODE_SPECS.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MODE_SPECS.first()

enum class Phase { IDLE, WORK, REST, DONE }

data class WorkoutState(
    val mode: String = MODE_SPECS.first().name,
    val routineName: String = "",
    val exercises: List<String> = emptyList(),
    val phase: Phase = Phase.IDLE,
    val left: Int = 0,
    val phaseTotal: Int = 1,
    val round: Int = 1,
    val rounds: Int = MODE_SPECS.first().rounds,
    val work: Int = MODE_SPECS.first().work,
    val rest: Int = MODE_SPECS.first().rest,
    val elapsed: Int = 0,
    val running: Boolean = false,
) {
    val active get() = phase == Phase.WORK || phase == Phase.REST
    /** Ejercicio actual de la rutina (rota por ronda); vacío si el modo no tiene lista. */
    val currentExercise: String
        get() = if (exercises.isEmpty()) "" else exercises[(round - 1) % exercises.size]
}
