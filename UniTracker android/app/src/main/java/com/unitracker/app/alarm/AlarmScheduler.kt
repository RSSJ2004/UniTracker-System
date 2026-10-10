package com.unitracker.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.unitracker.app.data.local.ReminderDao

/**
 * Convierte las alarmas guardadas en Room en alarmas reales del sistema.
 *
 * La hora de disparo es dueAt - minutesBefore. Es idempotente: se puede llamar
 * cuantas veces haga falta (al sincronizar, al editar una alarma, al reiniciar el teléfono)
 * y deja el sistema exactamente como la base de datos.
 */
class AlarmScheduler(
    context: Context,
    private val reminderDao: ReminderDao
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** En Android 12 y 12L el usuario debe permitir "Alarmas y recordatorios"; desde 13 se concede sola. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        val active = reminderDao.getActiveForScheduling().associateBy { it.reminderId }
        for (id in reminderDao.allIds()) {
            val reminder = active[id]
            val triggerAt = reminder?.let { it.dueAt - it.minutesBefore * 60_000L }
            if (reminder != null && triggerAt != null && triggerAt > now) {
                val info = AlarmInfo(
                    reminderId = reminder.reminderId,
                    title = reminder.title,
                    course = reminder.course,
                    phase = reminder.phase,
                    minutesBefore = reminder.minutesBefore,
                    dueAt = reminder.dueAt
                )
                val pendingIntent = buildPendingIntent(KIND_REMINDER, id, info, PendingIntent.FLAG_UPDATE_CURRENT)
                if (pendingIntent != null) setAlarm(triggerAt, pendingIntent)
            } else {
                cancel(id)
            }
        }
    }

    /** Vuelve a sonar esta misma alarma dentro de [minutes] minutos (botón "Posponer"). */
    fun scheduleSnooze(info: AlarmInfo, minutes: Int) {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val pendingIntent = buildPendingIntent(KIND_SNOOZE, info.reminderId, info, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return
        setAlarm(triggerAt, pendingIntent)
    }

    fun cancel(reminderId: Long) {
        val pendingIntent = buildPendingIntent(KIND_REMINDER, reminderId, null, PendingIntent.FLAG_NO_CREATE)
            ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun setAlarm(triggerAt: Long, pendingIntent: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            // Sin permiso de alarmas exactas: puede retrasarse unos minutos, pero no se pierde.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun buildPendingIntent(
        kind: String,
        reminderId: Long,
        info: AlarmInfo?,
        modeFlag: Int
    ): PendingIntent? {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            // El data hace único cada Intent aunque cambien los extras.
            data = Uri.parse("unitracker://$kind/$reminderId")
        }
        info?.putInto(intent)
        return PendingIntent.getBroadcast(
            appContext,
            reminderId.toInt(),
            intent,
            modeFlag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val KIND_REMINDER = "reminder"
        const val KIND_SNOOZE = "snooze"
    }
}
