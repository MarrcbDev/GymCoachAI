package com.marrdev.gymcoachai.alarms

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marrdev.gymcoachai.GymDatabase
import com.marrdev.gymcoachai.MainActivity
import com.marrdev.gymcoachai.R
import com.marrdev.gymcoachai.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ReminderScheduler {
    private const val CHANNEL = "gymcoach_reminders"

    /** Próximo disparo (epoch ms) o null si no hay ninguno (sin hora, fecha pasada, etc.). */
    fun nextTrigger(r: ReminderEntity, now: Long = System.currentTimeMillis()): Long? {
        if (r.hour < 0) return null
        val zone = ZoneId.systemDefault()
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun at(d: LocalDate) = d.atTime(r.hour, r.minute).atZone(zone).toInstant().toEpochMilli()

        if (r.daysMask == 0) {
            r.dateEpochDay?.let { day ->
                val t = at(LocalDate.ofEpochDay(day))
                return if (t > now) t else null
            }
            val t = at(today)
            return if (t > now) t else at(today.plusDays(1))
        }
        for (i in 0..7) {
            val d = today.plusDays(i.toLong())
            val bit = 1 shl (d.dayOfWeek.value - 1)
            if (r.daysMask and bit != 0) {
                val t = at(d)
                if (t > now) return t
            }
        }
        return null
    }

    private fun pending(ctx: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, id, Intent(ctx, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }

    /** En Android 12+ las alarmas exactas requieren el permiso "Alarmas y recordatorios"; en Android 14+ viene apagado. */
    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(ctx: Context, r: ReminderEntity) {
        cancel(ctx, r.id)
        if (!r.enabled) return
        val t = nextTrigger(r) ?: return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val operation = pending(ctx, r.id)
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            if (canScheduleExact(ctx)) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), operation)
            } else {
                // Sin permiso: alarma aproximada (puede retrasarse unos minutos) en vez de cerrar la app.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, operation)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, operation)
        }
    }

    fun notify(ctx: Context, r: ReminderEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Notas y alarmas", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, r.id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (r.body.isBlank()) r.title else "${r.title}\n${r.body}"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_gym)
            .setContentTitle(if (r.isAlarm) "Hora de entrenar" else "Nota")
            .setContentText(r.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (r.isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(r.id, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        val app = ctx.applicationContext
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = GymDatabase.get(app).dao()
                val r = dao.reminder(id) ?: return@launch
                ReminderScheduler.notify(app, r)
                if (r.daysMask == 0) dao.upsertReminder(r.copy(enabled = false)) else ReminderScheduler.schedule(app, r)
            } finally {
                pr.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = ctx.applicationContext
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GymDatabase.get(app).dao().allReminders().filter { it.enabled }.forEach { ReminderScheduler.schedule(app, it) }
            } finally {
                pr.finish()
            }
        }
    }
}
