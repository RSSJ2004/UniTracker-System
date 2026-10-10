package com.unitracker.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unitracker.app.UniTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android borra todas las alarmas al reiniciar el teléfono (y al actualizar la app).
 * Este receptor las vuelve a programar desde la base de datos.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != ACTION_EXACT_ALARM_PERMISSION_CHANGED
        ) return

        val pending = goAsync()
        val scheduler = (context.applicationContext as UniTrackerApp).container.alarmScheduler
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+)
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
