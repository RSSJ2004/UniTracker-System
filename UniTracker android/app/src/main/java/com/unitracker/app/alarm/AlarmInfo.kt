package com.unitracker.app.alarm

import android.content.Intent
import com.unitracker.app.util.formatMillis
import com.unitracker.app.util.formatOffset
import com.unitracker.app.util.phaseLabel

/** Todo lo que hace falta para mostrar y sonar una alarma, sin consultar la base de datos. */
data class AlarmInfo(
    val reminderId: Long,
    val title: String,
    val course: String?,
    val phase: String,
    val minutesBefore: Int,
    val dueAt: Long
) {
    /** Ej.: "Entrega en 3 h". */
    val headline: String
        get() = "${phaseLabel(phase)} en ${formatOffset(minutesBefore)}"

    val details: String
        get() = buildString {
            append(headline)
            if (dueAt > 0) append(" (${formatMillis(dueAt)})")
            if (!course.isNullOrBlank()) append("\n").append(course)
        }

    fun putInto(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_REMINDER_ID, reminderId)
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_COURSE, course)
        putExtra(EXTRA_PHASE, phase)
        putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
        putExtra(EXTRA_DUE_AT, dueAt)
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COURSE = "course"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_MINUTES_BEFORE = "minutesBefore"
        const val EXTRA_DUE_AT = "dueAt"

        fun from(intent: Intent?): AlarmInfo = AlarmInfo(
            reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L,
            title = intent?.getStringExtra(EXTRA_TITLE) ?: "Tarea pendiente",
            course = intent?.getStringExtra(EXTRA_COURSE),
            phase = intent?.getStringExtra(EXTRA_PHASE) ?: "due",
            minutesBefore = intent?.getIntExtra(EXTRA_MINUTES_BEFORE, 0) ?: 0,
            dueAt = intent?.getLongExtra(EXTRA_DUE_AT, 0L) ?: 0L
        )
    }
}
