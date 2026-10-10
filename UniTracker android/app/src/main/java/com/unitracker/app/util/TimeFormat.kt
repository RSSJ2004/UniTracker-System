package com.unitracker.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.forLanguageTag("es-DO"))

fun formatMillis(millis: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun phaseLabel(phase: String): String = when (phase) {
    "due" -> "Entrega"
    "opens" -> "Abre"
    "closes" -> "Cierra"
    else -> phase
}

/** 1500 -> "1 día 1 h", 90 -> "1 h 30 min", 45 -> "45 min". */
fun formatOffset(totalMinutes: Int): String {
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (days > 0) add(if (days == 1) "1 día" else "$days días")
        if (hours > 0) add("$hours h")
        if (minutes > 0) add("$minutes min")
    }
    return parts.joinToString(" ").ifEmpty { "0 min" }
}
