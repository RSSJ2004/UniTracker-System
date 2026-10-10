package com.unitracker.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una alarma de una tarea. Una tarea puede tener varias.
 *
 * Solo se guarda el desfase ("minutesBefore"); la hora exacta de disparo
 * se calcula como dueAt - minutesBefore, así que si el campus cambia la
 * fecha de entrega, las alarmas se mueven solas.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["uid"],
            childColumns = ["taskUid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskUid")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskUid: String,
    val minutesBefore: Int,
    val enabled: Boolean = true
)
