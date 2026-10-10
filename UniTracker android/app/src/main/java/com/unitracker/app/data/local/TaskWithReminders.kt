package com.unitracker.app.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithReminders(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "uid", entityColumn = "taskUid")
    val reminders: List<ReminderEntity>
)
