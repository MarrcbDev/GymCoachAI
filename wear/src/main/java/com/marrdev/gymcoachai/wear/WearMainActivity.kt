package com.marrdev.gymcoachai.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val WatchBlue = Color(0xFF8DB8FF)
private val WatchCyan = Color(0xFF4BC5FF)

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = WatchBlue, onPrimary = Color(0xFF00305F),
                    secondary = WatchCyan, background = Color.Black, surface = Color.Black,
                    surfaceContainerHigh = Color(0xFF121A28),
                ),
            ) {
                WatchWorkoutScreen()
            }
        }
    }
}

@Composable
private fun WatchWorkoutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("Fuerza") }
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("Listo para entrenar") }

    LaunchedEffect(running) {
        while (running) { delay(1000); seconds++ }
    }

    // Fondo negro AMOLED; scroll vertical y margen extra arriba/abajo por la pantalla redonda.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("GymCoachAI", color = WatchCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(mode, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("%02d:%02d".format(seconds / 60, seconds % 60), color = WatchBlue, style = MaterialTheme.typography.displaySmall)
            Text(status, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

            if (!running) {
                Button(
                    onClick = { seconds = 0; running = true; status = "Sesión en curso" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Iniciar") }
                listOf("Fuerza", "Cardio", "Movilidad", "Resistencia", "Recuperación").forEach { item ->
                    FilledTonalButton(
                        onClick = { mode = item },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (mode == item) ButtonDefaults.filledTonalButtonColors()
                        else ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF0C121C), contentColor = Color.LightGray),
                    ) { Text(item) }
                }
            } else {
                Button(
                    onClick = {
                        running = false
                        scope.launch {
                            val request = PutDataMapRequest.create("/gymcoach/workout/session").apply {
                                dataMap.putString("type", mode)
                                dataMap.putLong("duration_seconds", seconds)
                                dataMap.putInt("steps", 0)
                                dataMap.putLong("timestamp", System.currentTimeMillis())
                            }.asPutDataRequest().setUrgent()
                            runCatching { Wearable.getDataClient(context).putDataItem(request).await() }
                                .onSuccess { status = "Sesión sincronizada" }
                                .onFailure { status = "No se pudo sincronizar" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB83245), contentColor = Color.White),
                ) { Text("Finalizar y sincronizar") }
            }
        }
    }
}
