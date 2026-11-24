package com.example.my_fitness_app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** Esquema azul y negro: se usa cuando los colores dinámicos están apagados o en Android < 12. */
private val BlueAmoled = darkColorScheme(
    primary = Color(0xFF8DB8FF), onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF0B3F80), onPrimaryContainer = Color(0xFFD6E5FF),
    secondary = Color(0xFF4BC5FF), onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF0A3B52), onSecondaryContainer = Color(0xFFC6EAFF),
    tertiary = Color(0xFFB3C6FF), onTertiary = Color(0xFF16276B),
    tertiaryContainer = Color(0xFF2E3F85), onTertiaryContainer = Color(0xFFDCE1FF),
    background = Color.Black, onBackground = Color(0xFFE3E9F5),
    surface = Color.Black, onSurface = Color(0xFFE3E9F5),
    surfaceVariant = Color(0xFF1A2230), onSurfaceVariant = Color(0xFFB4C0D4),
    outline = Color(0xFF6E7B92), outlineVariant = Color(0xFF2A3446),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080D15),
    surfaceContainer = Color(0xFF0C121C), surfaceContainerHigh = Color(0xFF121A28),
    surfaceContainerHighest = Color(0xFF182233),
)

/** AMOLED: fondo negro puro y contenedores apenas elevados, conservando el tono del esquema base. */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.55f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.7f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.85f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.9f),
)

@Composable
fun GymTheme(dynamic: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).amoled()
    } else {
        BlueAmoled
    }
    MaterialTheme(colorScheme = base, content = content)
}
