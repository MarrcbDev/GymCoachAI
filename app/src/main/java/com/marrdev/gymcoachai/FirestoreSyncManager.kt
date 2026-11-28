package com.marrdev.gymcoachai

import android.content.Context
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.UUID

/**
 * Sincronización automática de datos de cuenta. El chat y las claves API nunca pasan por esta clase.
 *
 * Cómo funciona:
 *  - Cada colección se compara contra Firestore con una huella (hash) del contenido. Solo se sube lo que
 *    cambió en este dispositivo y solo se baja lo que cambió en otro; lo que no cambió no genera escrituras.
 *  - Si un documento desaparece de la nube (borrado en otro dispositivo) se borra aquí, y viceversa.
 *  - Cada escritura "toca" el documento users/{uid} (syncRev + syncDevice). Los demás dispositivos escuchan
 *    solo ese documento ([listenForRemoteChanges]) y se sincronizan solos cuando alguien más cambió algo.
 *  - Los IDs de Firestore se guardan en sync_mappings para que los IDs autogenerados de Room
 *    no provoquen duplicados al sincronizar desde otro dispositivo.
 */
data class FirestoreSyncResult(
    val message: String,
    val healthConsent: Boolean,
    /** Marca de tiempo de la última edición del perfil (local o recibida), para guardarla en preferencias. */
    val profileUpdatedAt: Long,
    /** Recordatorios borrados localmente porque se borraron en otro dispositivo (hay que cancelar su alarma). */
    val deletedReminderIds: List<Int> = emptyList(),
)

class FirestoreSyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = appContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    /** Identifica este dispositivo para ignorar los avisos que genera él mismo. */
    private val deviceId: String by lazy {
        prefs.getString("sync_device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("sync_device_id", it).apply()
        }
    }

    companion object {
        /** Un solo sync a la vez en todo el proceso (la app y el servicio del reloj comparten base de datos). */
        private val lock = Mutex()
        private const val COMMIT_TIMEOUT_MS = 10_000L
    }

    // ───────── escucha en tiempo real ─────────

    /** Avisa cuando OTRO dispositivo cambió algo. Solo lee un documento, así que cuesta casi nada. */
    fun listenForRemoteChanges(uid: String, onChange: () -> Unit): ListenerRegistration =
        firestore.collection("users").document(uid).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            if (snap.metadata.hasPendingWrites()) return@addSnapshotListener
            if (snap.getString("syncDevice") == deviceId) return@addSnapshotListener
            onChange()
        }

    // ───────── sincronización ─────────

    suspend fun sync(
        uid: String,
        dao: GymDao,
        healthConsent: Boolean,
        healthConsentExplicit: Boolean,
        localProfileUpdatedAt: Long,
    ): FirestoreSyncResult = lock.withLock {
        // Otra cuenta en este dispositivo: los vínculos de la cuenta anterior no valen para esta.
        if (prefs.getString("sync_last_uid", null) != uid) {
            dao.clearAllMappings()
            prefs.edit().putString("sync_last_uid", uid).apply()
        }

        val root = firestore.collection("users").document(uid)
        // Source.SERVER: sin conexión falla (y se reintenta solo) en vez de confundir "sin caché" con "borrado".
        val remoteProfile = root.get(Source.SERVER).await()
        val remoteConsent = remoteProfile.getBoolean("healthSyncConsent") == true
        // En un dispositivo nuevo se respeta el consentimiento ya registrado en la cuenta.
        // Si el usuario cambió explícitamente el interruptor en este dispositivo, prevalece su elección.
        val consent = if (healthConsentExplicit) healthConsent else remoteConsent

        // Al revocar el consentimiento se borran primero los documentos, mientras las reglas
        // todavía reconocen el consentimiento anterior como activo.
        if (!consent && remoteConsent) {
            val docs = sub(uid, "health_conditions").get(Source.SERVER).await()
            val batch = firestore.batch()
            docs.documents.forEach { batch.delete(it.reference) }
            commit(batch)
            dao.clearMappings("health_conditions")
        }

        val profileUpdatedAt = syncProfile(root, dao, remoteProfile, consent, localProfileUpdatedAt)

        var wrote = false
        syncCollection(uid, dao, routines(dao)).also { wrote = wrote || it.wrote }
        val reminders = syncCollection(uid, dao, reminders(dao)).also { wrote = wrote || it.wrote }
        syncCollection(uid, dao, sessions(dao)).also { wrote = wrote || it.wrote }
        if (consent) syncCollection(uid, dao, conditions(dao)).also { wrote = wrote || it.wrote }
        if (wrote) touch(root, consent)

        FirestoreSyncResult(
            message = "Sincronizado",
            healthConsent = consent,
            profileUpdatedAt = profileUpdatedAt,
            deletedReminderIds = reminders.deletedLocalIds.map { it.toInt() },
        )
    }

    /** Borra un documento de la nube. No espera confirmación: sin conexión la SDK lo deja en cola. */
    suspend fun deleteRemote(uid: String, dao: GymDao, collection: String, localId: Long) {
        val mapping = dao.mappingForLocal(collection, localId) ?: return
        val batch = firestore.batch()
        batch.delete(sub(uid, collection).document(mapping.cloudId))
        batch.set(
            firestore.collection("users").document(uid),
            mapOf("syncRev" to FieldValue.serverTimestamp(), "syncDevice" to deviceId),
            SetOptions.merge(),
        )
        withTimeoutOrNull(COMMIT_TIMEOUT_MS) { batch.commit().await() }
        dao.deleteMapping(collection, localId)
    }

    // ───────── perfil (con foto) ─────────

    /** Devuelve la marca de tiempo de edición del perfil que debe guardarse localmente. */
    private suspend fun syncProfile(
        root: DocumentReference,
        dao: GymDao,
        remote: DocumentSnapshot,
        consent: Boolean,
        localUpdatedAt: Long,
    ): Long {
        val user = dao.getUser()
        val remoteUpdatedAt = remote.getLong("updatedAt") ?: 0L
        val remoteHasProfile = remote.exists() && !remote.getString("name").isNullOrBlank()
        val remoteConsent = remote.getBoolean("healthSyncConsent") == true
        var effectiveUpdatedAt = localUpdatedAt
        val updates = HashMap<String, Any?>()

        val pull = remoteHasProfile && (user == null || remoteUpdatedAt > localUpdatedAt)
        if (pull) {
            var imagePath = user?.imagePath.orEmpty()
            val remotePhoto = remote.getString("photo")
            if (!remotePhoto.isNullOrBlank()) {
                if (remote.getString("photoHash") != PhotoUtils.hashOf(imagePath)) {
                    PhotoUtils.saveFromCloud(appContext, remotePhoto)?.let { path ->
                        PhotoUtils.deleteOld(appContext, imagePath)
                        imagePath = path
                    }
                }
            } else if (imagePath.isNotBlank()) {
                PhotoUtils.deleteOld(appContext, imagePath)
                imagePath = ""
            }
            dao.saveUser(
                UserEntity(
                    id = user?.id ?: 0,
                    name = remote.getString("name") ?: "",
                    imagePath = imagePath,
                    age = (remote.getLong("age") ?: 0L).toInt(),
                    height = remote.getDouble("height") ?: 0.0,
                    weight = remote.getDouble("weight") ?: 0.0,
                    goals = remote.getString("goals") ?: "",
                    sex = remote.getString("sex") ?: "",
                ),
            )
            effectiveUpdatedAt = remoteUpdatedAt
        } else if (user != null && (!remoteHasProfile || localUpdatedAt > remoteUpdatedAt)) {
            val ts = if (localUpdatedAt > 0) localUpdatedAt else System.currentTimeMillis()
            updates["name"] = user.name
            updates["sex"] = user.sex
            updates["age"] = user.age
            updates["height"] = user.height
            updates["weight"] = user.weight
            updates["goals"] = user.goals
            updates["updatedAt"] = ts
            val photo = PhotoUtils.encodeForCloud(user.imagePath)
            if (photo != null) {
                if (photo.second != remote.getString("photoHash")) {
                    updates["photo"] = photo.first
                    updates["photoHash"] = photo.second
                }
            } else if (remote.contains("photo")) {
                updates["photo"] = FieldValue.delete()
                updates["photoHash"] = FieldValue.delete()
            }
            effectiveUpdatedAt = ts
        }

        if (consent != remoteConsent || !remote.exists()) updates["healthSyncConsent"] = consent
        if (updates.isNotEmpty()) {
            updates["healthSyncConsent"] = consent // las reglas exigen este campo en cada escritura
            updates["syncRev"] = FieldValue.serverTimestamp()
            updates["syncDevice"] = deviceId
            val batch = firestore.batch()
            batch.set(root, updates, SetOptions.merge())
            commit(batch)
        }
        return effectiveUpdatedAt
    }

    // ───────── colecciones ─────────

    private class Adapter<T>(
        val name: String,
        val all: suspend () -> List<T>,
        val idOf: (T) -> Long,
        val withId: (T, Long) -> T,
        val parse: (DocumentSnapshot) -> T?,
        val toMap: (T) -> Map<String, Any?>,
        val insert: suspend (T) -> Long,
        val replace: suspend (T) -> Unit,
        val remove: suspend (Long) -> Unit,
    )

    private class CollectionResult(val wrote: Boolean, val deletedLocalIds: List<Long>)

    private suspend fun <T> syncCollection(uid: String, dao: GymDao, a: Adapter<T>): CollectionResult {
        val col = sub(uid, a.name)
        val remoteDocs = col.get(Source.SERVER).await().documents
        val remoteById = remoteDocs.associateBy { it.id }
        val locals = a.all()
        val localIds = locals.map { a.idOf(it) }.toHashSet()
        val writes = PendingWrites()
        val deletedLocal = mutableListOf<Long>()

        // 1) Lo que existe aquí.
        for (item in locals) {
            val id = a.idOf(item)
            val mapping = dao.mappingForLocal(a.name, id)
            if (mapping == null) {
                // Nuevo en este dispositivo: se sube.
                val cloudId = UUID.randomUUID().toString()
                val map = a.toMap(item)
                writes.set(col.document(cloudId), map)
                dao.upsertMapping(SyncMappingEntity(a.name, id, cloudId, fingerprint(map)))
                writes.flushIfFull()
                continue
            }
            val remote = remoteById[mapping.cloudId]
            if (remote == null) {
                // Ya estaba sincronizado y desapareció de la nube: se borró en otro dispositivo.
                a.remove(id)
                dao.deleteMapping(a.name, id)
                deletedLocal += id
                continue
            }
            val remoteItem = a.parse(remote) ?: continue
            val localMap = a.toMap(item)
            val localHash = fingerprint(localMap)
            val remoteHash = fingerprint(a.toMap(remoteItem))
            when {
                localHash == remoteHash ->
                    if (mapping.lastHash != localHash) dao.upsertMapping(mapping.copy(lastHash = localHash))
                // Cambió aquí (si cambió en ambos lados gana este dispositivo): se sube.
                mapping.lastHash.isNotEmpty() && localHash != mapping.lastHash -> {
                    writes.set(remote.reference, localMap)
                    dao.upsertMapping(mapping.copy(lastHash = localHash))
                    writes.flushIfFull()
                }
                // Cambió en otro dispositivo: se baja.
                else -> {
                    a.replace(a.withId(remoteItem, id))
                    dao.upsertMapping(mapping.copy(lastHash = remoteHash))
                }
            }
        }

        // 2) Lo que existe en la nube.
        for (doc in remoteDocs) {
            val mapping = dao.mappingForCloud(a.name, doc.id)
            if (mapping == null) {
                // Nuevo en la nube (otro dispositivo): se baja.
                val item = a.parse(doc) ?: continue
                val id = a.insert(item)
                dao.upsertMapping(SyncMappingEntity(a.name, id, doc.id, fingerprint(a.toMap(item))))
            } else if (mapping.localId !in localIds) {
                // Se borró aquí y la nube aún lo tiene: se borra también allá.
                writes.delete(doc.reference)
                dao.deleteMapping(a.name, mapping.localId)
                writes.flushIfFull()
            }
        }
        val wrote = writes.flush()
        return CollectionResult(wrote, deletedLocal)
    }

    private fun routines(dao: GymDao) = Adapter<RoutineEntity>(
        name = "routines",
        all = { dao.routinesOnce() },
        idOf = { it.id },
        withId = { x, id -> x.copy(id = id) },
        parse = parse@{ doc ->
            RoutineEntity(
                name = doc.getString("name") ?: return@parse null,
                mode = doc.getString("mode") ?: "",
                exercises = doc.getString("exercises") ?: "",
                rounds = (doc.getLong("rounds") ?: 1L).toInt(),
                workSec = (doc.getLong("workSec") ?: 30L).toInt(),
                restSec = (doc.getLong("restSec") ?: 0L).toInt(),
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            )
        },
        toMap = {
            mapOf(
                "name" to it.name, "mode" to it.mode, "exercises" to it.exercises,
                "rounds" to it.rounds, "workSec" to it.workSec, "restSec" to it.restSec, "createdAt" to it.createdAt,
            )
        },
        insert = { dao.addRoutine(it) },
        replace = { dao.upsertRoutine(it) },
        remove = { dao.deleteRoutineById(it) },
    )

    private fun reminders(dao: GymDao) = Adapter<ReminderEntity>(
        name = "reminders",
        all = { dao.allReminders() },
        idOf = { it.id.toLong() },
        withId = { x, id -> x.copy(id = id.toInt()) },
        parse = parse@{ doc ->
            ReminderEntity(
                title = doc.getString("title") ?: return@parse null,
                body = doc.getString("body") ?: "",
                isAlarm = doc.getBoolean("isAlarm") ?: false,
                hour = (doc.getLong("hour") ?: -1L).toInt(),
                minute = (doc.getLong("minute") ?: 0L).toInt(),
                daysMask = (doc.getLong("daysMask") ?: 0L).toInt(),
                dateEpochDay = doc.getLong("dateEpochDay"),
                enabled = doc.getBoolean("enabled") ?: true,
            )
        },
        toMap = {
            mapOf(
                "title" to it.title, "body" to it.body, "isAlarm" to it.isAlarm, "hour" to it.hour,
                "minute" to it.minute, "daysMask" to it.daysMask, "dateEpochDay" to it.dateEpochDay, "enabled" to it.enabled,
            )
        },
        insert = { dao.upsertReminder(it) },
        replace = { dao.upsertReminder(it) },
        remove = { dao.deleteReminderById(it.toInt()) },
    )

    private fun sessions(dao: GymDao) = Adapter<SessionEntity>(
        name = "sessions",
        all = { dao.sessionsOnce() },
        idOf = { it.id },
        withId = { x, id -> x.copy(id = id) },
        parse = parse@{ doc ->
            SessionEntity(
                startMs = doc.getLong("startMs") ?: return@parse null,
                endMs = doc.getLong("endMs") ?: 0L,
                mode = doc.getString("mode") ?: "",
                routineName = doc.getString("routineName") ?: "",
                avgHr = (doc.getLong("avgHr") ?: 0L).toInt(),
                maxHr = (doc.getLong("maxHr") ?: 0L).toInt(),
                calories = (doc.getLong("calories") ?: 0L).toInt(),
                source = doc.getString("source") ?: "phone",
            )
        },
        toMap = {
            mapOf(
                "startMs" to it.startMs, "endMs" to it.endMs, "mode" to it.mode, "routineName" to it.routineName,
                "avgHr" to it.avgHr, "maxHr" to it.maxHr, "calories" to it.calories, "source" to it.source,
            )
        },
        insert = { dao.addSession(it) },
        replace = { dao.upsertSession(it) },
        remove = { dao.deleteSessionById(it) },
    )

    private fun conditions(dao: GymDao) = Adapter<HealthConditionEntity>(
        name = "health_conditions",
        all = { dao.conditionsOnce() },
        idOf = { it.id },
        withId = { x, id -> x.copy(id = id) },
        parse = parse@{ doc ->
            HealthConditionEntity(
                name = doc.getString("name") ?: return@parse null,
                kind = doc.getString("kind") ?: "Otra",
                notes = doc.getString("notes") ?: "",
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            )
        },
        toMap = {
            mapOf("name" to it.name, "kind" to it.kind, "notes" to it.notes, "createdAt" to it.createdAt)
        },
        insert = { dao.addCondition(it) },
        replace = { dao.upsertCondition(it) },
        remove = { dao.deleteConditionById(it) },
    )

    // ───────── utilidades ─────────

    /** Agrupa escrituras en lotes (Firestore admite 500 por lote). */
    private inner class PendingWrites {
        private var batch: WriteBatch = firestore.batch()
        private var ops = 0
        private var total = 0

        fun set(ref: DocumentReference, data: Map<String, Any?>) { batch.set(ref, data); ops++; total++ }
        fun delete(ref: DocumentReference) { batch.delete(ref); ops++; total++ }

        suspend fun flushIfFull() { if (ops >= 400) flushBatch() }

        /** Devuelve true si en total se escribió algo. */
        suspend fun flush(): Boolean { flushBatch(); return total > 0 }

        private suspend fun flushBatch() {
            if (ops == 0) return
            commit(batch)
            batch = firestore.batch()
            ops = 0
        }
    }

    /**
     * Espera la confirmación del servidor (para ver errores como permisos), pero si no llega a tiempo
     * (sin conexión) sigue: la SDK guarda la escritura y la envía sola al recuperar la conexión.
     */
    private suspend fun commit(batch: WriteBatch) {
        withTimeoutOrNull(COMMIT_TIMEOUT_MS) { batch.commit().await() }
    }

    /** Avisa a los otros dispositivos de que hay cambios que bajar. */
    private suspend fun touch(root: DocumentReference, consent: Boolean) {
        val batch = firestore.batch()
        batch.set(
            root,
            mapOf("healthSyncConsent" to consent, "syncRev" to FieldValue.serverTimestamp(), "syncDevice" to deviceId),
            SetOptions.merge(),
        )
        commit(batch)
    }

    private fun fingerprint(map: Map<String, Any?>): String {
        val canonical = map.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("SHA-1").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun sub(uid: String, collection: String) =
        firestore.collection("users").document(uid).collection(collection)
}
