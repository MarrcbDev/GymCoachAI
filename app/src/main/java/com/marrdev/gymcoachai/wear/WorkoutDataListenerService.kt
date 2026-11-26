package com.marrdev.gymcoachai.wear

import android.util.Log
import com.marrdev.gymcoachai.GymDatabase
import com.marrdev.gymcoachai.SessionEntity
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkoutDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/gymcoach/workout/session") {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val type = map.getString("type", "Entrenamiento")
                val duration = map.getLong("duration_seconds", 0L)
                val steps = map.getInt("steps", 0)
                val end = map.getLong("timestamp", System.currentTimeMillis())
                getSharedPreferences("wear_sync", MODE_PRIVATE).edit()
                    .putString("last_workout_type", type)
                    .putLong("last_workout_duration", duration)
                    .putInt("last_workout_steps", steps)
                    .putLong("last_workout_received", System.currentTimeMillis())
                    .apply()
                Log.i("GymCoachAI", "Sesión recibida: $type, ${duration}s, $steps pasos")

                val start = end - duration * 1000
                val dao = GymDatabase.get(applicationContext).dao()
                CoroutineScope(Dispatchers.IO).launch {
                    if (duration > 0 && dao.watchCount(start) == 0) {
                        dao.addSession(SessionEntity(startMs = start, endMs = end, mode = type, source = "watch"))
                    }
                }
            }
        }
    }
}
