package com.unitracker.app.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unitracker.app.UniTrackerApp
import com.unitracker.app.ui.MainActivity

/**
 * Hace sonar la alarma de verdad: reproduce el tono por el canal de ALARMA (sigue el volumen de
 * alarma, no el del timbre ni el de notificaciones), vibra y muestra una notificación con
 * "Detener" y "Posponer". Se detiene sola al minuto.
 */
class AlarmRingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var current: AlarmInfo? = null

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { onTimeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_SNOOZE -> snooze()
            else -> startRinging(AlarmInfo.from(intent))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    // ---------- Ciclo de la alarma ----------

    private fun startRinging(info: AlarmInfo) {
        // Android exige llamar a startForeground enseguida después de startForegroundService.
        NotificationHelper.createChannels(this)
        val notification = buildRingingNotification(info)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        stopPlayback() // por si ya sonaba otra: la nueva ocupa su lugar
        current = info
        acquireWakeLock()
        startSound()
        startVibration()
        handler.postDelayed(timeoutRunnable, RING_MS)
    }

    private fun stopRinging() {
        stopPlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun snooze() {
        current?.let {
            (application as UniTrackerApp).container.alarmScheduler.scheduleSnooze(it, SNOOZE_MINUTES)
        }
        stopRinging()
    }

    /** Nadie la atendió en un minuto: se calla y deja un aviso silencioso como registro. */
    @SuppressLint("MissingPermission") // se comprueba dentro de canNotify()
    private fun onTimeout() {
        val info = current
        stopPlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (info != null && canNotify()) {
            val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_MISSED)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Alarma sin atender: ${info.title}")
                .setContentText(info.headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(info.details))
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .build()
            val id = if (info.reminderId >= 0) info.reminderId.toInt() else NOTIFICATION_ID
            NotificationManagerCompat.from(this).notify(id, notification)
        }
        stopSelf()
    }

    // ---------- Sonido, vibración y batería ----------

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "unitracker:alarm").apply {
            acquire(RING_MS + 10_000)
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(timeoutRunnable)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    // ---------- Notificaciones ----------

    private fun buildRingingNotification(info: AlarmInfo) =
        NotificationCompat.Builder(this, NotificationHelper.CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(info.title)
            .setContentText(info.headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, "Detener", servicePendingIntent(ACTION_STOP, 1))
            .addAction(0, "Posponer $SNOOZE_MINUTES min", servicePendingIntent(ACTION_SNOOZE, 2))
            .build()

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AlarmRingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_STOP = "com.unitracker.app.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE = "com.unitracker.app.ACTION_SNOOZE_ALARM"
        private const val NOTIFICATION_ID = 4242
        private const val RING_MS = 60_000L
        private const val SNOOZE_MINUTES = 10
    }
}
