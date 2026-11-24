package com.example.my_fitness_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val WatchBg = Color(0xFF070B14)
private val WatchBlue = Color(0xFF287BFF)
private val WatchCyan = Color(0xFF4BC5FF)

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = WatchBlue, secondary = WatchCyan, background = WatchBg, surface = Color(0xFF111B2B))) {
                WatchWorkoutScreen()
            }
        }
    }
}

@Composable
private fun WatchWorkoutScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("Fuerza") }
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("Listo para entrenar") }

    LaunchedEffect(running) {
        while (running) { delay(1000); seconds++ }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("GYMCOACH", color = WatchCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Text(mode, color = Color.White, fontWeight = FontWeight.Bold)
        Text("%02d:%02d".format(seconds / 60, seconds % 60), color = WatchCyan, style = MaterialTheme.typography.displaySmall)
        Text(status, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        if (!running) {
            Button(onClick = { running = true; status = "Sesión en curso" }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar") }
            listOf("Fuerza", "Cardio", "Movilidad").forEach { item ->
                TextButton(onClick = { mode = item }) { Text(item, color = if (mode == item) WatchCyan else Color.LightGray) }
            }
        } else {
            Button(onClick = {
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
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB83245))) { Text("Finalizar y sincronizar") }
        }
    }
}
