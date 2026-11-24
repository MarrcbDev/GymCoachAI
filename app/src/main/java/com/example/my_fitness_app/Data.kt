package com.example.my_fitness_app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
)

@Dao
interface GymDao {
    @Query("SELECT * FROM user LIMIT 1") suspend fun getUser(): UserEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveUser(user: UserEntity)
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") suspend fun messages(): List<ChatMessageEntity>
    @Insert suspend fun addMessage(message: ChatMessageEntity)
}

/** v2 -> v3: añade el sexo sin perder perfil ni historial del chat. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user ADD COLUMN sex TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [UserEntity::class, ChatMessageEntity::class], version = 3, exportSchema = false)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao

    companion object {
        @Volatile private var instance: GymDatabase? = null

        fun get(context: Context): GymDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GymDatabase::class.java, "gymcoach_native.db")
                .addMigrations(MIGRATION_2_3)
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
