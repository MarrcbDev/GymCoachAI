package com.marrdev.gymcoachai

import android.app.Activity
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrdev.gymcoachai.alarms.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ChatLine(val id: Long, val role: String, val content: String, val suggestions: List<JSONObject> = emptyList())

val DEFAULT_MODELS = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1")
private const val FALLBACK_MODEL = "gpt-4o-mini"

class GymViewModel(
    private val appContext: Context,
    private val db: GymDatabase,
    private val vault: ApiKeyVault,
) : ViewModel() {
    private val dao = db.dao()
    private val uiPrefs = appContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    // ───────── estado general ─────────
    /** true hasta que se lee la base de datos; evita mostrar el registro un instante por error. */
    var loading by mutableStateOf(true); private set
    var user by mutableStateOf<UserEntity?>(null); private set
    var dynamicColor by mutableStateOf(uiPrefs.getBoolean("dynamic_color", true)); private set
    /** Pestaña que otra parte de la app (por ejemplo el coach) pide abrir. */
    var requestedTab by mutableStateOf<String?>(null)

    val routines = dao.routines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes = dao.reminders(false).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val alarms = dao.reminders(true).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions = dao.sessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val conditions = dao.conditions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ───────── chat / IA ─────────
    var messages by mutableStateOf<List<ChatLine>>(emptyList()); private set
    var hasApiKey by mutableStateOf(false); private set
    var keyMessage by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var chatError by mutableStateOf<String?>(null); private set
    var aiModel by mutableStateOf(uiPrefs.getString("ai_model", FALLBACK_MODEL) ?: FALLBACK_MODEL); private set
    var models by mutableStateOf(DEFAULT_MODELS); private set
    var modelsLoading by mutableStateOf(false); private set
    var modelsMessage by mutableStateOf<String?>(null); private set

    // ───────── cuenta (opcional) ─────────
    private val auth = AuthManager(appContext)
    val authAvailable: Boolean get() = auth.available
    val googleAvailable: Boolean get() = auth.googleAvailable
    var account by mutableStateOf<AccountInfo?>(null); private set
    var authBusy by mutableStateOf(false); private set
    var authMessage by mutableStateOf<String?>(null)
    var aiSource by mutableStateOf(uiPrefs.getString("ai_source", "account") ?: "account"); private set
    var quotaText by mutableStateOf<String?>(null); private set
    val backendConfigured: Boolean get() = BuildConfig.BACKEND_URL.isNotBlank()
    /** Con sesión iniciada y el servicio configurado, el coach usa la API Key del desarrollador vía backend. */
    val useBackend: Boolean get() = account != null && aiSource == "account" && backendConfigured
    val canChat: Boolean get() = useBackend || hasApiKey

    // ───────── salud ─────────
    var shareHealth by mutableStateOf(uiPrefs.getBoolean("share_health", true)); private set
    var healthAsked by mutableStateOf(uiPrefs.getBoolean("health_asked", false)); private set
    var openHealthDialog by mutableStateOf(false)

    /** Mensaje corto para mostrar como aviso (Toast). */
    var message by mutableStateOf<String?>(null)

    // ───────── entrenamiento ─────────
    var workout by mutableStateOf(WorkoutState()); private set
    private var timerJob: Job? = null
    private var workoutStartMs = 0L

    init {
        account = auth.current()
        auth.listen { account = it }
        viewModelScope.launch {
            runCatching { LegacyUserImporter(appContext, db).importIfNeeded() }
            user = runCatching { dao.getUser() }.getOrNull()
            messages = runCatching {
                dao.messages().map { ChatLine(it.id, it.role, it.content, parseSuggestions(it.suggestions)) }
            }.getOrDefault(emptyList())
            hasApiKey = vault.hasKey()
            loading = false
        }
    }

    private fun parseSuggestions(json: String): List<JSONObject> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    // ───────── ajustes ─────────
    fun updateDynamicColor(value: Boolean) {
        dynamicColor = value
        uiPrefs.edit().putBoolean("dynamic_color", value).apply()
    }

    fun updateModel(value: String) {
        aiModel = value
        uiPrefs.edit().putString("ai_model", value.trim()).apply()
    }

    fun refreshModels() {
        viewModelScope.launch {
            modelsLoading = true
            modelsMessage = null
            try {
                val list = if (useBackend) {
                    val token = auth.idToken() ?: error("Vuelve a iniciar sesión.")
                    BackendClient(BuildConfig.BACKEND_URL).models(token)
                } else {
                    OpenAiClient(vault.read()).listModels()
                }
                if (list.isNotEmpty()) {
                    models = list
                    // Con el servicio incluido solo valen los modelos que el backend permite.
                    if (useBackend && aiModel !in list) updateModel(list.first())
                }
                modelsMessage = "${list.size} modelos disponibles."
            } catch (e: Exception) {
                modelsMessage = e.message ?: "No se pudo cargar la lista."
            }
            modelsLoading = false
        }
    }

    /** Devuelve true si la clave se guardó. Una clave inválida no cierra la app. */
    fun saveApiKey(value: String): Boolean {
        val result = runCatching { vault.save(value.trim()) }
        hasApiKey = vault.hasKey()
        keyMessage = result.exceptionOrNull()?.let { it.message ?: "No se pudo guardar la clave." }
        return result.isSuccess
    }

    fun saveProfile(name: String, sex: String, age: Int, height: Double, weight: Double, goals: String, imagePath: String) =
        viewModelScope.launch {
            val previous = user
            dao.saveUser(
                UserEntity(
                    id = previous?.id ?: 0, name = name.trim(), imagePath = imagePath,
                    age = age, height = height, weight = weight, goals = goals.trim(), sex = sex,
                ),
            )
            if (previous != null && previous.imagePath != imagePath) PhotoUtils.deleteOld(appContext, previous.imagePath)
            user = dao.getUser()
        }

    // ───────── notas y alarmas ─────────
    fun saveReminder(r: ReminderEntity) = viewModelScope.launch {
        try {
            persistReminder(r)
        } catch (e: Exception) {
            message = "No se pudo guardar: ${e.message ?: "error desconocido"}"
        }
    }

    private suspend fun persistReminder(r: ReminderEntity): ReminderEntity {
        val id = dao.upsertReminder(r).toInt()
        val saved = if (r.id == 0) r.copy(id = id) else r
        ReminderScheduler.schedule(appContext, saved)
        return saved
    }

    fun deleteReminder(r: ReminderEntity) = viewModelScope.launch {
        ReminderScheduler.cancel(appContext, r.id)
        dao.deleteReminder(r)
    }

    // ───────── rutinas ─────────
    fun saveRoutine(r: RoutineEntity) = viewModelScope.launch { dao.addRoutine(r) }
    fun deleteRoutine(r: RoutineEntity) = viewModelScope.launch { dao.deleteRoutine(r) }

    // ───────── entrenamiento ─────────
    fun selectMode(name: String) {
        if (workout.phase != Phase.IDLE) return
        val s = modeSpec(name)
        workout = WorkoutState(mode = s.name, rounds = s.rounds, work = s.work, rest = s.rest)
    }

    fun adjustRounds(d: Int) { if (workout.phase == Phase.IDLE) workout = workout.copy(rounds = (workout.rounds + d).coerceIn(1, 30)) }
    fun adjustWork(d: Int) {
        if (workout.phase != Phase.IDLE) return
        val step = if (workout.mode == "Cardio") 60 else 5
        workout = workout.copy(work = (workout.work + d * step).coerceIn(step, 7200))
    }
    fun adjustRest(d: Int) { if (workout.phase == Phase.IDLE) workout = workout.copy(rest = (workout.rest + d * 5).coerceIn(0, 600)) }

    fun startRoutine(r: RoutineEntity) {
        if (workout.active) return
        workout = WorkoutState(
            mode = r.mode, routineName = r.name,
            exercises = r.exercises.lines().map { it.trim() }.filter { it.isNotEmpty() },
            rounds = r.rounds, work = r.workSec, rest = r.restSec,
        )
        startWorkout()
    }

    fun startWorkout(): Boolean {
        val s = workout
        if (s.phase != Phase.IDLE) return false
        workoutStartMs = System.currentTimeMillis()
        workout = s.copy(phase = Phase.WORK, running = true, left = s.work, phaseTotal = s.work, round = 1, elapsed = 0)
        buzz()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                tick()
            }
        }
        requestedTab = "Rutinas"
        return true
    }

    fun togglePause() {
        val s = workout
        if (s.active) workout = s.copy(running = !s.running)
    }

    private fun tick() {
        val s = workout
        if (!s.running || !s.active) return
        val st = s.copy(elapsed = s.elapsed + 1)
        val left = s.left - 1
        if (left > 0) {
            workout = st.copy(left = left)
            return
        }
        when (s.phase) {
            Phase.WORK -> when {
                s.round >= s.rounds -> { workout = st; finishWorkout() }
                s.rest > 0 -> { buzz(); workout = st.copy(phase = Phase.REST, left = s.rest, phaseTotal = s.rest) }
                else -> { buzz(); workout = st.copy(round = s.round + 1, left = s.work, phaseTotal = s.work) }
            }
            Phase.REST -> { buzz(); workout = st.copy(phase = Phase.WORK, round = s.round + 1, left = s.work, phaseTotal = s.work) }
            else -> Unit
        }
    }

    fun finishWorkout() {
        timerJob?.cancel()
        val s = workout
        if (!s.active) return
        buzz()
        if (s.elapsed >= 10) {
            viewModelScope.launch {
                dao.addSession(
                    SessionEntity(
                        startMs = workoutStartMs, endMs = System.currentTimeMillis(),
                        mode = s.mode, routineName = s.routineName, source = "phone",
                    ),
                )
            }
        }
        workout = s.copy(phase = Phase.DONE, running = false)
    }

    fun resetWorkout() {
        timerJob?.cancel()
        val spec = modeSpec(workout.mode)
        workout = WorkoutState(mode = spec.name, rounds = spec.rounds, work = spec.work, rest = spec.rest)
    }

    private fun buzz() {
        val v = appContext.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // ───────── coach IA ─────────
    fun clearChat() = viewModelScope.launch {
        dao.clearChat()
        messages = emptyList()
        chatError = null
    }

    fun send(text: String) {
        viewModelScope.launch {
            val t = text.trim()
            if (t.isEmpty() || busy) return@launch
            chatError = null
            val userId = dao.addMessage(ChatMessageEntity(role = "user", content = t))
            messages = messages + ChatLine(userId, "user", t)
            busy = true
            try {
                val history = messages.takeLast(12).map { it.role to it.content }
                val health = if (shareHealth) conditions.value else null
                val prompt = AiAssistant.systemPrompt(user, routines.value.map { it.name }, health)
                val model = aiModel.trim().ifBlank { FALLBACK_MODEL }
                val parsed = if (useBackend) {
                    val token = auth.idToken() ?: error("Vuelve a iniciar sesión.")
                    val reply = BackendClient(BuildConfig.BACKEND_URL).chat(token, model, prompt, history)
                    quotaText = reply.remaining?.let { "Te quedan $it de ${reply.limit ?: "?"} mensajes hoy" }
                    AiAssistant.parse(reply.content)
                } else {
                    val key = vault.read()
                    AiAssistant.parse(OpenAiClient(key).chat(model, listOf("system" to prompt) + history))
                }

                val done = parsed.actions.mapNotNull { applyAction(it) }
                val content = (listOf(parsed.reply) + done.map { "✓ $it" }).joinToString("\n\n")
                val suggestions = parsed.suggestions.filter { isValidAction(it) }
                val json = if (suggestions.isEmpty()) "" else JSONArray(suggestions).toString()
                val id = dao.addMessage(ChatMessageEntity(role = "assistant", content = content, suggestions = json))
                messages = messages + ChatLine(id, "assistant", content, suggestions)
            } catch (e: Exception) {
                chatError = "No pude responder: ${e.message ?: "error desconocido"}"
            }
            busy = false
        }
    }

    /** Ejecuta una propuesta del coach cuando el usuario toca su botón. */
    fun applySuggestion(messageId: Long, index: Int) {
        viewModelScope.launch {
            val line = messages.firstOrNull { it.id == messageId } ?: return@launch
            val action = line.suggestions.getOrNull(index) ?: return@launch
            val result = applyAction(action) ?: "No se pudo aplicar la propuesta."
            val remaining = line.suggestions.filterIndexed { i, _ -> i != index }
            dao.setSuggestions(messageId, if (remaining.isEmpty()) "" else JSONArray(remaining).toString())
            messages = messages.map { if (it.id == messageId) it.copy(suggestions = remaining) else it }
            val text = "✓ $result"
            val id = dao.addMessage(ChatMessageEntity(role = "assistant", content = text))
            messages = messages + ChatLine(id, "assistant", text)
        }
    }

    private fun isValidAction(a: JSONObject): Boolean = when (a.optString("type")) {
        "add_routine" -> a.optString("name").isNotBlank()
        "start_workout" -> true
        "add_note" -> a.optString("title").isNotBlank()
        "add_alarm" -> parseTime(a.optString("time")) != null
        else -> false
    }

    private fun parseTime(s: String): Pair<Int, Int>? {
        val m = Regex("^\\s*(\\d{1,2}):(\\d{2})\\s*$").find(s) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    private fun daysMask(arr: JSONArray?): Int {
        if (arr == null) return 0
        val letters = listOf("L", "M", "X", "J", "V", "S", "D")
        var mask = 0
        for (i in 0 until arr.length()) {
            val idx = letters.indexOf(arr.optString(i).trim().uppercase())
            if (idx >= 0) mask = mask or (1 shl idx)
        }
        return mask
    }

    /** Aplica una acción del coach y devuelve un texto de confirmación, o null si no era válida. */
    private suspend fun applyAction(a: JSONObject): String? {
        if (!isValidAction(a)) return null
        return when (a.optString("type")) {
            "add_routine" -> {
                val name = a.optString("name").trim()
                val arr = a.optJSONArray("exercises")
                val exercises = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
                val spec = modeSpec(a.optString("mode"))
                dao.addRoutine(
                    RoutineEntity(
                        name = name, mode = spec.name, exercises = exercises.joinToString("\n"),
                        rounds = a.optInt("rounds", spec.rounds).coerceIn(1, 30),
                        workSec = a.optInt("work_seconds", spec.work).coerceIn(5, 7200),
                        restSec = a.optInt("rest_seconds", spec.rest).coerceIn(0, 600),
                    ),
                )
                "Rutina «$name» guardada en Rutinas."
            }
            "start_workout" -> {
                if (workout.active) return "Ya hay un entrenamiento en curso."
                val routineName = a.optString("routine").trim()
                val routine = if (routineName.isEmpty()) null
                else dao.routinesOnce().firstOrNull { it.name.equals(routineName, ignoreCase = true) }
                if (routine != null) {
                    startRoutine(routine)
                    "Entrenamiento «${routine.name}» iniciado."
                } else {
                    val spec = modeSpec(a.optString("mode"))
                    workout = WorkoutState(
                        mode = spec.name,
                        rounds = a.optInt("rounds", spec.rounds).coerceIn(1, 30),
                        work = a.optInt("work_seconds", spec.work).coerceIn(5, 7200),
                        rest = a.optInt("rest_seconds", spec.rest).coerceIn(0, 600),
                    )
                    startWorkout()
                    "Entrenamiento de ${spec.name} iniciado."
                }
            }
            "add_note" -> {
                val time = parseTime(a.optString("time"))
                val date = runCatching { LocalDate.parse(a.optString("date").trim()) }.getOrNull()
                val saved = persistReminder(
                    ReminderEntity(
                        title = a.optString("title").trim(), body = a.optString("body").trim(), isAlarm = false,
                        hour = time?.first ?: -1, minute = time?.second ?: 0,
                        dateEpochDay = date?.toEpochDay(),
                    ),
                )
                val whenText = buildString {
                    date?.let { append(" para el ${it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}") }
                    time?.let { append(" a las %02d:%02d".format(it.first, it.second)) }
                }
                "Nota «${saved.title}» guardada$whenText."
            }
            "add_alarm" -> {
                val (h, m) = parseTime(a.optString("time"))!!
                val mask = daysMask(a.optJSONArray("days"))
                val saved = persistReminder(
                    ReminderEntity(
                        title = a.optString("title").trim().ifBlank { "Entrenamiento" },
                        isAlarm = true, hour = h, minute = m, daysMask = mask,
                    ),
                )
                "Alarma «${saved.title}» programada a las %02d:%02d.".format(h, m)
            }
            else -> null
        }
    }

    // ───────── cuenta ─────────
    fun signInGoogle(activity: Activity) = launchAuth { auth.signInWithGoogle(activity) }
    fun signInEmail(email: String, password: String) = launchAuth { auth.signInWithEmail(email, password) }
    fun signUpEmail(email: String, password: String) = launchAuth { auth.signUpWithEmail(email, password) }

    private fun launchAuth(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            authBusy = true
            authMessage = null
            val result = block()
            result.onFailure { authMessage = it.message }
            result.onSuccess {
                account = auth.current()
                updateAiSource("account")
                if (useBackend) refreshModels()
            }
            authBusy = false
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            authBusy = true
            val result = auth.sendPasswordReset(email)
            authMessage = result.exceptionOrNull()?.message ?: "Te enviamos un correo para restablecer tu contraseña."
            authBusy = false
        }
    }

    fun resendVerification() {
        viewModelScope.launch {
            val result = auth.resendVerification()
            message = result.exceptionOrNull()?.message ?: "Te enviamos de nuevo el correo de verificación."
        }
    }

    /** Vuelve a leer la cuenta (por si ya verificó el correo) y renueva el token. */
    fun refreshAccount() {
        viewModelScope.launch {
            account = auth.reload()
            auth.idToken(forceRefresh = true)
            message = if (account?.emailVerified == true) "Correo verificado." else "Aún no aparece como verificado."
        }
    }

    fun signOut() {
        auth.signOut()
        account = null
        quotaText = null
    }

    fun updateAiSource(source: String) {
        aiSource = source
        uiPrefs.edit().putString("ai_source", source).apply()
        quotaText = null
    }

    // ───────── salud ─────────
    fun addCondition(name: String, kind: String, notes: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        dao.addCondition(HealthConditionEntity(name = name.trim(), kind = kind, notes = notes.trim()))
        markHealthAsked()
    }

    fun deleteCondition(c: HealthConditionEntity) = viewModelScope.launch { dao.deleteCondition(c) }

    fun markHealthAsked() {
        healthAsked = true
        uiPrefs.edit().putBoolean("health_asked", true).apply()
    }

    fun updateShareHealth(value: Boolean) {
        shareHealth = value
        uiPrefs.edit().putBoolean("share_health", value).apply()
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
