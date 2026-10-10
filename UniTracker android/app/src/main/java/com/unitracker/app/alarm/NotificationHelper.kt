package com.unitracker.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

object NotificationHelper {
    // Si cambias el sonido o la importancia de un canal, súbele el número al id:
    // Android no deja modificar un canal ya creado.

    /** Respaldo: notificación con sonido de alarma, por si el servicio no puede arrancar. */
    const val CHANNEL_ID = "task_alarms_v1"

    /** La alarma sonando: el sonido lo reproduce el servicio, no el canal. */
    const val CHANNEL_RINGING = "task_ringing_v1"

    /** Aviso silencioso de una alarma que no atendiste. */
    const val CHANNEL_MISSED = "task_missed_v1"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Alarmas (respaldo)", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Se usa solo si la alarma con sonido no puede iniciar"
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attributes)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }

        if (manager.getNotificationChannel(CHANNEL_RINGING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RINGING, "Alarma sonando", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Aviso con botones Detener y Posponer mientras suena la alarma"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }

        if (manager.getNotificationChannel(CHANNEL_MISSED) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MISSED, "Alarmas sin atender", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Queda como registro si no detuviste la alarma"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }
}
