package com.marrdev.gymcoachai

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Sincronización de datos de cuenta. El chat y las claves API nunca pasan por esta clase.
 * Los IDs de Firestore se guardan en sync_mappings para que los IDs autogenerados de Room
 * no provoquen duplicados al sincronizar desde otro dispositivo.
 */
data class FirestoreSyncResult(val message: String, val healthConsent: Boolean)

class FirestoreSyncManager {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun sync(
        uid: String,
        dao: GymDao,
        healthConsent: Boolean,
        healthConsentExplicit: Boolean,
        localProfileUpdatedAt: Long,
    ): FirestoreSyncResult {
        val root = firestore.collection("users").document(uid)
        var user = dao.getUser()
        val remoteProfile = root.get().await()
        val remoteUpdatedAt = remoteProfile.getLong("updatedAt") ?: 0L
        // En un dispositivo nuevo se respeta el consentimiento ya registrado en la cuenta.
        // Si el usuario cambió explícitamente el interruptor en este dispositivo, prevalece su elección.
        val effectiveHealthConsent = if (healthConsentExplicit) healthConsent
            else remoteProfile.getBoolean("healthSyncConsent") == true
        val shouldPullProfile = remoteProfile.exists() &&
            !remoteProfile.getString("name").isNullOrBlank() &&
            (user == null || remoteUpdatedAt > localProfileUpdatedAt)
        if (shouldPullProfile) {
            user = UserEntity(
                id = user?.id ?: 0,
                name = remoteProfile.getString("name") ?: "",
                imagePath = user?.imagePath.orEmpty(),
                age = (remoteProfile.getLong("age") ?: 0L).toInt(),
                height = remoteProfile.getDouble("height") ?: 0.0,
                weight = remoteProfile.getDouble("weight") ?: 0.0,
                goals = remoteProfile.getString("goals") ?: "",
                sex = remoteProfile.getString("sex") ?: "",
            )
            dao.saveUser(user!!)
        }
        // Al revocar el consentimiento, borrar primero los documentos mientras las reglas
        // todavía reconocen el consentimiento anterior como activo.
        if (!effectiveHealthConsent && remoteProfile.getBoolean("healthSyncConsent") == true) {
            val healthDocs = sub(uid, "health_conditions").get().await()
            for (doc in healthDocs.documents) doc.reference.delete().await()
        }
        val profile = hashMapOf<String, Any>(
            "healthSyncConsent" to effectiveHealthConsent,
            "updatedAt" to System.currentTimeMillis(),
        )
        user?.let {
            profile["name"] = it.name
            profile["sex"] = it.sex
            profile["age"] = it.age
            profile["height"] = it.height
            profile["weight"] = it.weight
            profile["goals"] = it.goals
            // imagePath es una ruta privada del dispositivo; no se sube.
        }
        root.set(profile, com.google.firebase.firestore.SetOptions.merge()).await()

        syncRoutines(uid, dao)
        syncReminders(uid, dao)
        syncSessions(uid, dao)
        if (effectiveHealthConsent) syncConditions(uid, dao)
        return FirestoreSyncResult("Sincronización completada.", effectiveHealthConsent)
    }

    private suspend fun syncRoutines(uid: String, dao: GymDao) {
        val collection = "routines"
        val remote = sub(uid, collection).get().await()
        for (doc in remote.documents) {
            val mapping = dao.mappingForCloud(collection, doc.id)
            if (mapping != null) {
                val local = RoutineEntity(
                    id = mapping.localId,
                    name = doc.getString("name") ?: continue,
                    mode = doc.getString("mode") ?: "",
                    exercises = doc.getString("exercises") ?: "",
                    rounds = (doc.getLong("rounds") ?: 1L).toInt(),
                    workSec = (doc.getLong("workSec") ?: 30L).toInt(),
                    restSec = (doc.getLong("restSec") ?: 0L).toInt(),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                )
                dao.upsertRoutine(local)
            } else {
                val local = RoutineEntity(
                    name = doc.getString("name") ?: continue,
                    mode = doc.getString("mode") ?: "",
                    exercises = doc.getString("exercises") ?: "",
                    rounds = (doc.getLong("rounds") ?: 1L).toInt(),
                    workSec = (doc.getLong("workSec") ?: 30L).toInt(),
                    restSec = (doc.getLong("restSec") ?: 0L).toInt(),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                )
                val id = dao.addRoutine(local)
                dao.upsertMapping(SyncMappingEntity(collection, id, doc.id))
            }
        }
        for (item in dao.routinesOnce()) {
            val mapping = dao.mappingForLocal(collection, item.id)
            val cloudId = mapping?.cloudId ?: UUID.randomUUID().toString()
            sub(uid, collection).document(cloudId).set(routineMap(item)).await()
            if (mapping == null) dao.upsertMapping(SyncMappingEntity(collection, item.id, cloudId))
        }
    }

    private suspend fun syncReminders(uid: String, dao: GymDao) {
        val collection = "reminders"
        val remote = sub(uid, collection).get().await()
        for (doc in remote.documents) {
            val mapping = dao.mappingForCloud(collection, doc.id)
            val item = ReminderEntity(
                title = doc.getString("title") ?: continue,
                body = doc.getString("body") ?: "",
                isAlarm = doc.getBoolean("isAlarm") ?: false,
                hour = (doc.getLong("hour") ?: -1L).toInt(),
                minute = (doc.getLong("minute") ?: 0L).toInt(),
                daysMask = (doc.getLong("daysMask") ?: 0L).toInt(),
                dateEpochDay = doc.getLong("dateEpochDay"),
                enabled = doc.getBoolean("enabled") ?: true,
                id = mapping?.localId?.toInt() ?: 0,
            )
            val id = dao.upsertReminder(item).toInt().let { if (item.id != 0) item.id else it }
            if (mapping == null) dao.upsertMapping(SyncMappingEntity(collection, id.toLong(), doc.id))
        }
        for (item in dao.allReminders()) {
            val mapping = dao.mappingForLocal(collection, item.id.toLong())
            val cloudId = mapping?.cloudId ?: UUID.randomUUID().toString()
            sub(uid, collection).document(cloudId).set(reminderMap(item)).await()
            if (mapping == null) dao.upsertMapping(SyncMappingEntity(collection, item.id.toLong(), cloudId))
        }
    }

    private suspend fun syncSessions(uid: String, dao: GymDao) {
        val collection = "sessions"
        val remote = sub(uid, collection).get().await()
        for (doc in remote.documents) {
            val mapping = dao.mappingForCloud(collection, doc.id)
            val item = SessionEntity(
                startMs = doc.getLong("startMs") ?: continue,
                endMs = doc.getLong("endMs") ?: 0L,
                mode = doc.getString("mode") ?: "",
                routineName = doc.getString("routineName") ?: "",
                avgHr = (doc.getLong("avgHr") ?: 0L).toInt(),
                maxHr = (doc.getLong("maxHr") ?: 0L).toInt(),
                calories = (doc.getLong("calories") ?: 0L).toInt(),
                source = doc.getString("source") ?: "phone",
                id = mapping?.localId ?: 0L,
            )
            if (mapping == null) {
                dao.addSession(item)
                val inserted = dao.sessionsOnce().firstOrNull { it.startMs == item.startMs && it.endMs == item.endMs && it.mode == item.mode }
                if (inserted != null) dao.upsertMapping(SyncMappingEntity(collection, inserted.id, doc.id))
            } else dao.upsertSession(item)
        }
        for (item in dao.sessionsOnce()) {
            val mapping = dao.mappingForLocal(collection, item.id)
            val cloudId = mapping?.cloudId ?: UUID.randomUUID().toString()
            sub(uid, collection).document(cloudId).set(sessionMap(item)).await()
            if (mapping == null) dao.upsertMapping(SyncMappingEntity(collection, item.id, cloudId))
        }
    }

    private suspend fun syncConditions(uid: String, dao: GymDao) {
        val collection = "health_conditions"
        val remote = sub(uid, collection).get().await()
        for (doc in remote.documents) {
            val mapping = dao.mappingForCloud(collection, doc.id)
            val item = HealthConditionEntity(
                name = doc.getString("name") ?: continue,
                kind = doc.getString("kind") ?: "Otra",
                notes = doc.getString("notes") ?: "",
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                id = mapping?.localId ?: 0L,
            )
            if (mapping == null) {
                val id = dao.addCondition(item)
                dao.upsertMapping(SyncMappingEntity(collection, id, doc.id))
            } else dao.upsertCondition(item)
        }
        for (item in dao.conditionsOnce()) {
            val mapping = dao.mappingForLocal(collection, item.id)
            val cloudId = mapping?.cloudId ?: UUID.randomUUID().toString()
            sub(uid, collection).document(cloudId).set(conditionMap(item)).await()
            if (mapping == null) dao.upsertMapping(SyncMappingEntity(collection, item.id, cloudId))
        }
    }

    suspend fun deleteRemote(uid: String, dao: GymDao, collection: String, localId: Long) {
        val mapping = dao.mappingForLocal(collection, localId) ?: return
        sub(uid, collection).document(mapping.cloudId).delete().await()
        dao.deleteMapping(collection, localId)
    }

    private fun sub(uid: String, collection: String) =
        firestore.collection("users").document(uid).collection(collection)

    private fun routineMap(x: RoutineEntity) = mapOf(
        "name" to x.name, "mode" to x.mode, "exercises" to x.exercises,
        "rounds" to x.rounds, "workSec" to x.workSec, "restSec" to x.restSec, "createdAt" to x.createdAt,
    )
    private fun reminderMap(x: ReminderEntity) = mapOf(
        "title" to x.title, "body" to x.body, "isAlarm" to x.isAlarm, "hour" to x.hour,
        "minute" to x.minute, "daysMask" to x.daysMask, "dateEpochDay" to x.dateEpochDay, "enabled" to x.enabled,
    )
    private fun sessionMap(x: SessionEntity) = mapOf(
        "startMs" to x.startMs, "endMs" to x.endMs, "mode" to x.mode, "routineName" to x.routineName,
        "avgHr" to x.avgHr, "maxHr" to x.maxHr, "calories" to x.calories, "source" to x.source,
    )
    private fun conditionMap(x: HealthConditionEntity) = mapOf(
        "name" to x.name, "kind" to x.kind, "notes" to x.notes, "createdAt" to x.createdAt,
    )
}
