package com.unitracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // ---- Lectura (escaneo detallado) ----

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY dueAt IS NULL, dueAt ASC")
    fun observeAllWithReminders(): Flow<List<TaskWithReminders>>

    // ---- Lectura (escaneo rápido): solo conteos, sin traer filas ----

    @Query("SELECT COUNT(*) FROM tasks WHERE completed = 0 AND (dueAt IS NULL OR dueAt >= :now)")
    fun observePendingCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE completed = 0 AND dueAt >= :from AND dueAt < :until")
    fun observeCountBetween(from: Long, until: Long): Flow<Int>

    @Query("SELECT MIN(dueAt) FROM tasks WHERE completed = 0 AND dueAt >= :now")
    fun observeNextDue(now: Long): Flow<Long?>

    // ---- Escritura (sincronización) ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(task: TaskEntity): Long

    /** Actualiza solo los campos que vienen del campus; no toca "completed". */
    @Query(
        """UPDATE tasks SET remoteId = :remoteId, type = :type, phase = :phase, title = :title,
           course = :course, dueAt = :dueAt, dueAtRaw = :dueAtRaw, url = :url, updatedAt = :updatedAt
           WHERE uid = :uid"""
    )
    suspend fun updateRemoteFields(
        uid: String,
        remoteId: String,
        type: String,
        phase: String,
        title: String,
        course: String?,
        dueAt: Long?,
        dueAtRaw: String?,
        url: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM tasks WHERE uid NOT IN (:keep)")
    suspend fun deleteNotIn(keep: List<String>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET completed = :completed WHERE uid = :uid")
    suspend fun setCompleted(uid: String, completed: Boolean)
}
