package com.unitracker.app.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Convierte el dueAt de la Lambda a epoch millis.
 * Acepta ISO con zona ("...Z" o "...-04:00") y, como último recurso,
 * una fecha local sin zona (se asume la zona del teléfono).
 */
fun parseIsoToMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
}
