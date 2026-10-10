package com.unitracker.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

fun scheduleTestAlarmInOneMinute(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Verificar permisos de alarma exacta en Android 12+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        Toast.makeText(context, "Habilita los permisos de alarmas exactas en ajustes", Toast.LENGTH_LONG).show()
        return
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        action = "TEST_ALARM_ACTION"
        putExtra("title", "¡Alarma de Prueba!")
        putExtra("course", "UniTracker - Prueba a 1 Minuto")
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        9999, // ID único para pruebas
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Programar exactamente 60 segundos (1 minuto) en el futuro
    val triggerTime = System.currentTimeMillis() + 60_000

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
        Toast.makeText(context, "Alarma de prueba programada. Sonará en 1 minuto.", Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Error de permisos: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}