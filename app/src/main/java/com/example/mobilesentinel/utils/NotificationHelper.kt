package com.example.mobilesentinel.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mobilesentinel.MainActivity
import com.example.mobilesentinel.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_CRITICAL = "sentinel_critical"
        const val CHANNEL_WARNING = "sentinel_warning"
        const val CHANNEL_INFO = "sentinel_info"
        const val FOREGROUND_NOTIF_ID = 1001
        private var notifCounter = 2000
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // Critical channel
            val critical = NotificationChannel(
                CHANNEL_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical security alerts requiring immediate attention"
                enableLights(true)
                lightColor = Color.parseColor("#F44336")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            // Warning channel
            val warning = NotificationChannel(
                CHANNEL_WARNING,
                "Warning Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Security warnings and potential issues"
                enableLights(true)
                lightColor = Color.parseColor("#FF9800")
            }

            // Info channel
            val info = NotificationChannel(
                CHANNEL_INFO,
                "Information",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Scan results and general information"
            }

            manager.createNotificationChannel(critical)
            manager.createNotificationChannel(warning)
            manager.createNotificationChannel(info)
        }
    }

    fun sendCriticalAlert(title: String, body: String, sessionId: Long = 0) {
        if (criticalOnlyDisabled()) return

        val viewIntent = createMainIntent()
        val dismissIntent = PendingIntent.getActivity(
            context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CRITICAL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(viewIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "View Report",
                viewIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                dismissIntent
            )
            .setColor(Color.parseColor("#F44336"))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifCounter++, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    fun sendWarningAlert(title: String, body: String) {
        val prefs = context.getSharedPreferences("sentinel_settings", Context.MODE_PRIVATE)
        val criticalOnly = prefs.getBoolean("critical_alerts_only", false)
        if (criticalOnly) return

        val notification = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent())
            .setColor(Color.parseColor("#FF9800"))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifCounter++, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    fun sendScanComplete(score: Float, criticalCount: Int, highCount: Int) {
        val title = "Scan Complete — Risk Score: ${score.toInt()}/100"
        val body = "Found $criticalCount critical, $highCount high severity issues"

        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(createMainIntent())
            .addAction(
                android.R.drawable.ic_menu_view,
                "View Report",
                createMainIntent()
            )
            .setColor(Color.parseColor("#2196F3"))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifCounter++, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("MobileSentinel Active")
            .setContentText("Monitoring device security...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(createMainIntent())
            .setColor(Color.parseColor("#1A1A2E"))
            .build()
    }

    private fun createMainIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun criticalOnlyDisabled(): Boolean {
        // Critical alerts should always go through regardless of setting
        return false
    }
}

