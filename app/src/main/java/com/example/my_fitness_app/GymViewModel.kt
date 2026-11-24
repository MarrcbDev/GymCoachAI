package com.example.my_fitness_app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class ChatLine(val role: String, val content: String)

class GymViewModel(
    private val appContext: Context,
    private val db: GymDatabase,
    private val vault: ApiKeyVault,
) : ViewModel() {
    private val uiPrefs = appContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    /** true hasta que se lee la base de datos; evita mostrar el registro un instante por error. */
    var loading by mutableStateOf(true); private set
    var user by mutableStateOf<UserEntity?>(null); private set
    var messages by mutableStateOf<List<ChatLine>>(emptyList()); private set
    var hasApiKey by mutableStateOf(false); private set
    var keyMessage by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var dynamicColor by mutableStateOf(uiPrefs.getBoolean("dynamic_color", true)); private set

    init {
        viewModelScope.launch {
            runCatching { LegacyUserImporter(appContext, db).importIfNeeded() }
            user = runCatching { db.dao().getUser() }.getOrNull()
            messages = runCatching { db.dao().messages().map { ChatLine(it.role, it.content) } }.getOrDefault(emptyList())
            hasApiKey = vault.hasKey()
            loading = false
        }
    }

    fun setDynamicColor(value: Boolean) {
        dynamicColor = value
        uiPrefs.edit().putBoolean("dynamic_color", value).apply()
    }

    fun saveProfile(name: String, sex: String, age: Int, height: Double, weight: Double, goals: String) =
        viewModelScope.launch {
            val previous = user
            db.dao().saveUser(
                UserEntity(
                    id = previous?.id ?: 0, name = name.trim(), imagePath = previous?.imagePath ?: "",
                    age = age, height = height, weight = weight, goals = goals.trim(), sex = sex,
                ),
            )
            user = db.dao().getUser()
        }

    /** Devuelve true si la clave se guardó. Una clave inválida ya no cierra la app. */
    fun saveApiKey(value: String): Boolean {
        val result = runCatching { vault.save(value.trim()) }
        hasApiKey = vault.hasKey()
        keyMessage = result.exceptionOrNull()?.let { it.message ?: "No se pudo guardar la clave." }
        return result.isSuccess
    }

    fun send(text: String) = viewModelScope.launch {
        if (text.isBlank() || busy) return@launch
        messages = messages + ChatLine("user", text)
        db.dao().addMessage(ChatMessageEntity(role = "user", content = text))
        busy = true
        val context = user?.let {
            val sex = if (it.sex.isBlank()) "" else "Sexo: ${it.sex}. "
            "Perfil: ${sex}${it.age} años, ${it.height} m, ${it.weight} kg. Objetivo: ${it.goals}."
        } ?: ""
        val answer = runCatching { OpenAiClient(vault.read()).ask(text, context) }
            .getOrElse { "No pude conectar con OpenAI: ${it.message ?: "error desconocido"}" }
        db.dao().addMessage(ChatMessageEntity(role = "assistant", content = answer))
        messages = messages + ChatLine("assistant", answer)
        busy = false
    }

    /** Último entrenamiento recibido desde el reloj (lo guarda WorkoutDataListenerService). */
    fun lastWatchSummary(): String? {
        val p = appContext.getSharedPreferences("wear_sync", Context.MODE_PRIVATE)
        if (p.getLong("last_workout_received", 0L) == 0L) return null
        val type = p.getString("last_workout_type", "Entrenamiento")
        val minutes = p.getLong("last_workout_duration", 0L) / 60
        return "Último: $type · $minutes min"
    }
}
