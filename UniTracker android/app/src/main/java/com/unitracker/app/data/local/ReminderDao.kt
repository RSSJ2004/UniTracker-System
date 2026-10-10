package com.unitracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Lo mínimo que el programador de alarmas necesita saber de cada alarma activa. */
data class ScheduledReminder(
    val reminderId: Long,
    val minutesBefore: Int,
    val dueAt: Long,
    val title: String,
    val course: String?,
    val phase: String
)

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    /** Permite actualizar o insertar una alarma existente sin duplicar claves. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reminder: ReminderEntity)

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM reminders")
    suspend fun allIds(): List<Long>

    /** Alarmas encendidas, de tareas no completadas y con fecha conocida. */
    @Query(
        """SELECT r.id AS reminderId, r.minutesBefore AS minutesBefore, t.dueAt AS dueAt,
                  t.title AS title, t.course AS course, t.phase AS phase
           FROM reminders r
           INNER JOIN tasks t ON t.uid = r.taskUid
           WHERE r.enabled = 1 AND t.completed = 0 AND t.dueAt IS NOT NULL"""
    )
    suspend fun getActiveForScheduling(): List<ScheduledReminder>
}