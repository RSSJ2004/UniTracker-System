package com.unitracker.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una tarea/evento tal como lo devuelve la Lambda.
 *
 * La clave es "id#phase" porque un mismo examen puede aparecer dos veces
 * (phase = opens y phase = closes) y cada fase necesita sus propias alarmas.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val uid: String,
    val remoteId: String,
    val type: String,          // assignment | exam | ...
    val phase: String,         // due | opens | closes
    val title: String,
    val course: String?,
    val dueAt: Long?,          // epoch millis (UTC); null si no se pudo interpretar la fecha
    val dueAtRaw: String?,     // texto original del campus
    val url: String?,
    val completed: Boolean = false, // lo marca el usuario; la sincronización no lo pisa
    val updatedAt: Long
)
