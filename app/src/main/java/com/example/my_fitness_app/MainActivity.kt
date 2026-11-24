package com.example.my_fitness_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent\nimport androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*\nimport android.util.Base64
import kotlinx.coroutines.launch\nimport android.content.Context\nimport android.database.sqlite.SQLiteDatabase\nimport java.io.File

private val Ink = Color(0xFF070B14)
private val Panel = Color(0xFF111B2B)
private val Blue = Color(0xFF287BFF)
private val Cyan = Color(0xFF4BC5FF)
private val Muted = Color(0xFF9BAEC8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, GymDatabase::class.java, "gymcoach_native.db")
            .fallbackToDestructiveMigration()
            .build()
        val vm = GymViewModel(applicationContext, db, ApiKeyVault(applicationContext))
        lifecycleScope.launch { LegacyUserImporter(applicationContext, db).importIfNeeded() }
        setContent { GymTheme { GymApp(vm) } }
    }
}

@Composable
private fun GymTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, secondary = Cyan, background = Ink, surface = Panel,
            onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val imagePath: String,
    val age: Int,
    val height: Double,
    val weight: Double,
    val goals: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val role: String, val content: String, val createdAt: Long = System.currentTimeMillis())

@Dao
interface GymDao {
    @Query("SELECT * FROM user LIMIT 1") suspend fun getUser(): UserEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveUser(user: UserEntity)
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") suspend fun messages(): List<ChatMessageEntity>
    @Insert suspend fun addMessage(message: ChatMessageEntity)
}

@Database(entities = [UserEntity::class, ChatMessageEntity::class], version = 2, exportSchema = false)
abstract class GymDatabase : RoomDatabase() { abstract fun dao(): GymDao }

data class ChatLine(val role: String, val content: String)

class LegacyUserImporter(private val context: Context, private val db: GymDatabase) {
    suspend fun importIfNeeded() {
        if (db.dao().getUser() != null) return
        val oldDb = context.getDatabasePath("fitness_app.db")
        if (!oldDb.exists()) return
        runCatching {
            val legacy = SQLiteDatabase.openDatabase(oldDb.path, null, SQLiteDatabase.OPEN_READONLY)
            legacy.rawQuery("SELECT id,name,imagePath,age,height,weight,goals FROM user LIMIT 1", null).use { c ->
                if (c.moveToFirst()) {
                    db.dao().saveUser(UserEntity(
                        id = c.getInt(0), name = c.getString(1), imagePath = c.getString(2),
                        age = c.getInt(3), height = c.getDouble(4), weight = c.getDouble(5), goals = c.getString(6)
                    ))
                }
            }
            legacy.close()
        }
    }
}

class GymViewModel(private val appContext: Context, private val db: GymDatabase, private val vault: ApiKeyVault) : ViewModel() {
    var user by mutableStateOf<UserEntity?>(null); private set
    var messages by mutableStateOf<List<ChatLine>>(emptyList()); private set
    var hasApiKey by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    init { viewModelScope.launch {
        runCatching { LegacyUserImporter(appContext, db).importIfNeeded() }
        user = runCatching { db.dao().getUser() }.getOrNull()
        messages = runCatching { db.dao().messages().map { ChatLine(it.role, it.content) } }.getOrDefault(emptyList())
        hasApiKey = vault.hasKey()
    } }
    fun saveProfile(name: String, age: String, height: String, weight: String, goals: String) = viewModelScope.launch {
        val previous = user
        val parsed = UserEntity(previous?.id ?: 0, name, previous?.imagePath ?: "", age.toIntOrNull() ?: 0,
            height.replace(',', '.').toDoubleOrNull() ?: 0.0, weight.replace(',', '.').toDoubleOrNull() ?: 0.0, goals)
        db.dao().saveUser(parsed); user = db.dao().getUser()
    }
    fun saveApiKey(value: String) { vault.save(value.trim()); hasApiKey = vault.hasKey() }
    fun send(text: String) = viewModelScope.launch {
        if (text.isBlank() || busy) return@launch
        messages = messages + ChatLine("user", text)
        db.dao().addMessage(ChatMessageEntity(role = "user", content = text))
        busy = true
        val context = user?.let { "Perfil: ${it.age} años, ${it.height} m, ${it.weight} kg. Objetivo: ${it.goals}." } ?: ""
        val answer = runCatching { OpenAiClient(vault.read()).ask(text, context) }
            .getOrElse { "No pude conectar con OpenAI: ${it.message ?: "error desconocido"}" }
        db.dao().addMessage(ChatMessageEntity(role = "assistant", content = answer))
        messages = messages + ChatLine("assistant", answer)
        busy = false
    }
}

@Composable
fun GymApp(vm: GymViewModel) {
    var tab by remember { mutableStateOf("Inicio") }
    var showProfile by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Color.White) {
                listOf("Inicio", "Coach IA", "Rutinas", "Más").forEach { item ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item },
                        icon = { Icon(when(item) { "Inicio" -> Icons.Default.Dashboard; "Coach IA" -> Icons.Default.SmartToy; "Rutinas" -> Icons.Default.FitnessCenter; else -> Icons.Default.MoreHoriz }, null) },
                        label = { Text(item) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GYMCOACH", color = Cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Tu progreso, sin excusas.", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = { showProfile = true }) { Icon(Icons.Default.AccountCircle, null, tint = Cyan) }
            }
            when {
                showProfile -> ProfileScreen(vm) { showProfile = false }
                tab == "Inicio" -> Dashboard(vm) { tab = it }
                tab == "Coach IA" -> ChatScreen(vm)
                tab == "Rutinas" -> WorkoutsScreen()
                else -> MoreScreen(vm)
            }
        }
    }
}

@Composable
private fun Dashboard(vm: GymViewModel, navigate: (String) -> Unit) {
    val u = vm.user
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("HOLA${u?.name?.let { ", $it" } ?: ""}", color = Cyan, fontWeight = FontWeight.Bold)
                    Text("Hoy es un buen día para avanzar.", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { navigate("Rutinas") }) { Icon(Icons.Default.PlayArrow, null); Text("  Iniciar entrenamiento") }
                }
            }
        }
        item { Text("TU RESUMEN", color = Muted, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Peso", u?.let { "${it.weight} kg" } ?: "—", Icons.Default.MonitorWeight, Modifier.weight(1f))
                MetricCard("Objetivo", u?.goals?.take(16) ?: "Configurar", Icons.Default.Flag, Modifier.weight(1f))
            }
        }
        item { FeatureCard("Coach inteligente", "Resuelve dudas y crea hábitos con IA.", Icons.Default.SmartToy) { navigate("Coach IA") } }
        item { FeatureCard("Recordatorios", "Organiza tus próximas sesiones.", Icons.Default.Notifications) { navigate("Más") } }
        item { FeatureCard("Conecta tu reloj", "Sincroniza tus entrenamientos desde Wear OS.", Icons.Default.Watch) { navigate("Más") } }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) { Icon(icon, null, tint = Cyan); Spacer(Modifier.height(8.dp)); Text(title, color = Muted); Text(value, color = Color.White, fontWeight = FontWeight.Bold) }
    }
}
@Composable
private fun FeatureCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted) }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun ChatScreen(vm: GymViewModel) {
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Text("Tu coach está listo", color = Muted, modifier = Modifier.padding(bottom = 10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start) {
                    Surface(color = if (msg.role == "user") Blue else Panel, shape = RoundedCornerShape(18.dp)) {
                        Text(msg.content, Modifier.widthIn(max = 300.dp).padding(14.dp), color = Color.White)
                    }
                }
            }
            if (vm.busy) item { Text("El coach está pensando…", color = Cyan) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Escribe tu consulta…") }, maxLines = 4)
            IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !vm.busy) { Icon(Icons.Default.Send, null, tint = Cyan) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WorkoutsScreen() {
    var selected by remember { mutableStateOf("Fuerza") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MODOS DE ENTRENAMIENTO", color = Muted, fontWeight = FontWeight.Bold)
        listOf("Fuerza", "Cardio", "Movilidad", "Resistencia", "Recuperación").forEach { mode ->
            FeatureCard(mode, when(mode) { "Fuerza" -> "Series, repeticiones y descansos"; "Cardio" -> "Tiempo, ritmo y distancia"; "Movilidad" -> "Flexibilidad y rango de movimiento"; "Resistencia" -> "Trabajo por intervalos"; else -> "Sesiones suaves y descanso" }, Icons.Default.FitnessCenter) { selected = mode }
        }
        Text("Modo seleccionado: $selected", color = Cyan)
    }
}

@Composable
private fun MoreScreen(vm: GymViewModel) {
    var reminder by remember { mutableStateOf("") }
    var alarmTime by remember { mutableStateOf("07:00") }
    var keyInput by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ORGANIZACIÓN", color = Muted, fontWeight = FontWeight.Bold)
        OutlinedTextField(reminder, { reminder = it }, Modifier.fillMaxWidth(), label = { Text("Nuevo recordatorio") })
        Button(onClick = { reminder = "" }, enabled = reminder.isNotBlank()) { Text("Guardar recordatorio (pendiente de persistencia)") }
        OutlinedTextField(alarmTime, { alarmTime = it }, label = { Text("Hora de alarma (HH:mm)") })
        Text("La programación real de alarmas se incorpora en la siguiente iteración con permisos de Android.", color = Muted)
        Divider(color = Panel)
        Text("OPENAI", color = Muted, fontWeight = FontWeight.Bold)
        OutlinedTextField(keyInput, { keyInput = it }, Modifier.fillMaxWidth(), label = { Text("Tu API Key (sk-…)") }, singleLine = true)
        Button(onClick = { vm.saveApiKey(keyInput); keyInput = "" }) { Text("Guardar clave de forma cifrada") }
        Text(if (vm.hasApiKey) "API Key guardada en este dispositivo." else "No hay una API Key configurada.", color = if (vm.hasApiKey) Cyan else Muted)
        Text("La clave se cifra con Android Keystore. Para una app distribuida, un backend sigue siendo la opción más segura.", color = Muted)
    }
}

@Composable
private fun ProfileScreen(vm: GymViewModel, close: () -> Unit) {
    val u = vm.user
    var name by remember { mutableStateOf(u?.name ?: "") }
    var age by remember { mutableStateOf(u?.age?.toString() ?: "") }
    var height by remember { mutableStateOf(u?.height?.toString() ?: "") }
    var weight by remember { mutableStateOf(u?.weight?.toString() ?: "") }
    var goals by remember { mutableStateOf(u?.goals ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("TU PERFIL", color = Cyan, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") })
        OutlinedTextField(age, { age = it }, Modifier.fillMaxWidth(), label = { Text("Edad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(height, { height = it }, Modifier.fillMaxWidth(), label = { Text("Estatura (m)") })
        OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text("Peso (kg)") })
        OutlinedTextField(goals, { goals = it }, Modifier.fillMaxWidth(), label = { Text("Objetivos") })
        Button(onClick = { vm.saveProfile(name, age, height, weight, goals); close() }) { Text("Guardar perfil") }
        TextButton(onClick = close) { Text("Volver") }
    }
}
