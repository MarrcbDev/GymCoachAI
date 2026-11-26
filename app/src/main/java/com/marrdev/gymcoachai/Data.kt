package com.marrdev.gymcoachai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val imagePath: String,
    val age: Int,
    val height: Double,
    val weight: Double,
    val goals: String,
    val sex: String = "",
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** JSON con las acciones que el coach propone y el usuario aún no aceptó. */
    val suggestions: String = "",
)

/** Rutina guardada. exercises: un ejercicio por línea. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String,
    val exercises: String,
    val rounds: Int,
    val workSec: Int,
    val restSec: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Nota planificada (isAlarm=false) o alarma de entrenamiento (isAlarm=true).
 * hour = -1 significa nota sin hora (no notifica).
 * daysMask: bit0 = lunes … bit6 = domingo; 0 = una sola vez.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String = "",
    val isAlarm: Boolean,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0,
    val dateEpochDay: Long? = null,
    val enabled: Boolean = true,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val mode: String,
    val routineName: String = "",
    val avgHr: Int = 0,
    val maxHr: Int = 0,
    val calories: Int = 0,
    val source: String, // "phone" | "watch"
)

/** Lesión o condición médica del usuario. Se guarda solo en el dispositivo. kind: Lesión | Condición | Otra. */
@Entity(tableName = "health_conditions")
data class HealthConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface GymDao {
    @Query("SELECT * FROM user LIMIT 1") suspend fun getUser(): UserEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveUser(user: UserEntity)

    @Query("SELECT * FROM chat_messages ORDER BY createdAt, id") suspend fun messages(): List<ChatMessageEntity>
    @Insert suspend fun addMessage(message: ChatMessageEntity): Long
    @Query("UPDATE chat_messages SET suggestions = :json WHERE id = :id") suspend fun setSuggestions(id: Long, json: String)
    @Query("DELETE FROM chat_messages") suspend fun clearChat()

    @Query("SELECT * FROM routines ORDER BY createdAt DESC") fun routines(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routines") suspend fun routinesOnce(): List<RoutineEntity>
    @Insert suspend fun addRoutine(routine: RoutineEntity): Long
    @Delete suspend fun deleteRoutine(routine: RoutineEntity)

    @Query("SELECT * FROM reminders WHERE isAlarm = :alarm ORDER BY hour, minute, id") fun reminders(alarm: Boolean): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: Int): ReminderEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertReminder(reminder: ReminderEntity): Long
    @Delete suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM health_conditions ORDER BY createdAt DESC") fun conditions(): Flow<List<HealthConditionEntity>>
    @Query("SELECT * FROM health_conditions ORDER BY createdAt DESC") suspend fun conditionsOnce(): List<HealthConditionEntity>
    @Insert suspend fun addCondition(condition: HealthConditionEntity): Long
    @Delete suspend fun deleteCondition(condition: HealthConditionEntity)

    @Query("SELECT * FROM sessions ORDER BY startMs DESC") fun sessions(): Flow<List<SessionEntity>>
    @Insert suspend fun addSession(session: SessionEntity)
    @Query("SELECT COUNT(*) FROM sessions WHERE startMs = :startMs AND source = 'watch'") suspend fun watchCount(startMs: Long): Int
}

/** v2 -> v3: añade el sexo sin perder perfil ni historial del chat. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user ADD COLUMN sex TEXT NOT NULL DEFAULT ''")
    }
}

/** v3 -> v4: rutinas, notas/alarmas y sesiones; el chat guarda las propuestas del coach. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN suggestions TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `mode` TEXT NOT NULL, `exercises` TEXT NOT NULL, `rounds` INTEGER NOT NULL, " +
                "`workSec` INTEGER NOT NULL, `restSec` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `isAlarm` INTEGER NOT NULL, `hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `dateEpochDay` INTEGER, `enabled` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `mode` TEXT NOT NULL, `routineName` TEXT NOT NULL, " +
                "`avgHr` INTEGER NOT NULL, `maxHr` INTEGER NOT NULL, `calories` INTEGER NOT NULL, `source` TEXT NOT NULL)",
        )
    }
}

/** v4 -> v5: lesiones y condiciones médicas del usuario. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_conditions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    }
}

@Database(
    entities = [
        UserEntity::class, ChatMessageEntity::class, RoutineEntity::class, ReminderEntity::class,
        SessionEntity::class, HealthConditionEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao

    companion object {
        @Volatile private var instance: GymDatabase? = null

        fun get(context: Context): GymDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GymDatabase::class.java, "gymcoach_native.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}

/** Importa el usuario de la base antigua de Flutter (fitness_app.db) si todavía existe. */
class LegacyUserImporter(private val context: Context, private val db: GymDatabase) {
    suspend fun importIfNeeded() {
        if (db.dao().getUser() != null) return
        val oldDb = context.getDatabasePath("fitness_app.db")
        if (!oldDb.exists()) return
        runCatching {
            val legacy = SQLiteDatabase.openDatabase(oldDb.path, null, SQLiteDatabase.OPEN_READONLY)
            legacy.rawQuery("SELECT id,name,imagePath,age,height,weight,goals FROM user LIMIT 1", null).use { c ->
                if (c.moveToFirst()) {
                    db.dao().saveUser(
                        UserEntity(
                            id = c.getInt(0), name = c.getString(1), imagePath = c.getString(2),
                            age = c.getInt(3), height = c.getDouble(4), weight = c.getDouble(5), goals = c.getString(6),
                        ),
                    )
                }
            }
            legacy.close()
        }
    }
}
