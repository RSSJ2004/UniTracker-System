package com.unitracker.app.data

import androidx.room.withTransaction
import com.unitracker.app.alarm.AlarmScheduler
import com.unitracker.app.data.local.AppDatabase
import com.unitracker.app.data.local.ReminderEntity
import com.unitracker.app.data.local.TaskEntity
import com.unitracker.app.data.local.TaskWithReminders
import com.unitracker.app.data.remote.TaskApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Resumen para el "escaneo rápido": solo números, salen de la base local. */
data class QuickStats(
    val pending: Int = 0,
    val dueIn24h: Int = 0,
    val nextDueAt: Long? = null
)

data class SyncSummary(val total: Int, val added: Int)

class TaskRepository(
    private val db: AppDatabase,
    private val api: TaskApi,
    private val scheduler: AlarmScheduler
) {
    private val taskDao = db.taskDao()
    private val reminderDao = db.reminderDao()

    companion object {
        /** Alarmas que se crean automáticamente al aparecer una tarea nueva (en minutos antes). */
        val DEFAULT_REMINDER_MINUTES = listOf(1440, 180, 60)
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    // ---------- Lectura ----------

    /** Escaneo detallado: lista completa con sus alarmas. */
    fun observeTasks(): Flow<List<TaskWithReminders>> = taskDao.observeAllWithReminders()

    /** Escaneo rápido: tres consultas de conteo, instantáneas y sin red. */
    fun observeQuickStats(): Flow<QuickStats> {
        val now = System.currentTimeMillis()
        return combine(
            taskDao.observePendingCount(now),
            taskDao.observeCountBetween(now, now + DAY_MS),
            taskDao.observeNextDue(now)
        ) { pending, soon, next -> QuickStats(pending, soon, next) }
    }

    // ---------- Sincronización con la Lambda ----------

    suspend fun sync(): SyncSummary {
        val remote = api.fetchTasks()
        val now = System.currentTimeMillis()
        var added = 0
        val idsBefore = reminderDao.allIds().toSet()

        db.withTransaction {
            val keep = ArrayList<String>(remote.size)
            for (r in remote) {
                val uid = "${r.id}#${r.phase}"
                keep += uid
                val dueMillis = parseIsoToMillis(r.dueAt)

                val inserted = taskDao.insertIgnore(
                    TaskEntity(
                        uid = uid,
                        remoteId = r.id,
                        type = r.type,
                        phase = r.phase,
                        title = r.title,
                        course = r.course,
                        dueAt = dueMillis,
                        dueAtRaw = r.dueAtRaw,
                        url = r.url,
                        updatedAt = now
                    )
                )

                if (inserted == -1L) {
                    // Ya existía: actualizar datos del campus sin perder "completed" ni las alarmas.
                    taskDao.updateRemoteFields(
                        uid, r.id, r.type, r.phase, r.title, r.course,
                        dueMillis, r.dueAtRaw, r.url, now
                    )
                } else {
                    added++
                    if (dueMillis != null) {
                        val defaults = DEFAULT_REMINDER_MINUTES
                            .filter { dueMillis - it * 60_000L > now } // no crear alarmas ya vencidas
                            .map { ReminderEntity(taskUid = uid, minutesBefore = it) }
                        if (defaults.isNotEmpty()) reminderDao.insertAll(defaults)
                    }
                }
            }
            // Lo que ya no aparece en el campus se elimina (sus alarmas caen por CASCADE).
            if (keep.isEmpty()) taskDao.deleteAll() else taskDao.deleteNotIn(keep)
        }

        // Cancelar las alarmas del sistema cuyas filas ya no existen y programar el resto.
        val idsAfter = reminderDao.allIds().toSet()
        (idsBefore - idsAfter).forEach { scheduler.cancel(it) }
        scheduler.rescheduleAll()

        return SyncSummary(total = remote.size, added = added)
    }

    // ---------- Acciones del usuario ----------

    suspend fun setCompleted(uid: String, completed: Boolean) {
        taskDao.setCompleted(uid, completed)
        scheduler.rescheduleAll() // una tarea hecha no debe sonar
    }

    suspend fun addReminder(taskUid: String, minutesBefore: Int) {
        if (minutesBefore <= 0) return
        reminderDao.insert(ReminderEntity(taskUid = taskUid, minutesBefore = minutesBefore))
        scheduler.rescheduleAll()
    }

    suspend fun setReminderEnabled(id: Long, enabled: Boolean) {
        reminderDao.setEnabled(id, enabled)
        scheduler.rescheduleAll()
    }

    suspend fun deleteReminder(id: Long) {
        scheduler.cancel(id) // primero cancelar: después de borrar la fila ya no se sabría que existía
        reminderDao.delete(id)
    }

    /** Vuelve a programar todo desde la base de datos (al abrir la app, por ejemplo). */
    suspend fun refreshAlarms() = scheduler.rescheduleAll()

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExact()
}
