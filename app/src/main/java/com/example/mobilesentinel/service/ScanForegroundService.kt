package com.example.mobilesentinel.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.mobilesentinel.data.repository.FindingRepository
import com.example.mobilesentinel.utils.NotificationHelper
import com.example.mobilesentinel.utils.VulnerabilityScanner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScanForegroundService : Service() {

    @Inject lateinit var vulnerabilityScanner: VulnerabilityScanner
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var findingRepository: FindingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null

    companion object {
        private const val PREFS_NAME = "sentinel_settings"
        private const val KEY_SCAN_INTERVAL = "scan_interval_minutes"
        private const val KEY_LAST_FINDINGS = "last_scan_finding_titles"
        private const val DEFAULT_INTERVAL_MINUTES = 15

        fun start(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScanForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        startForeground(NotificationHelper.FOREGROUND_NOTIF_ID, notificationHelper.buildForegroundNotification())
        scheduleScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleScan() {
        scanRunnable?.let { handler.removeCallbacks(it) }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val intervalMinutes = prefs.getInt(KEY_SCAN_INTERVAL, DEFAULT_INTERVAL_MINUTES)
        val intervalMs = intervalMinutes.toLong() * 60 * 1000

        val runnable = object : Runnable {
            override fun run() {
                performScan()
                // Re-read interval in case user changed it
                val currentInterval = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getInt(KEY_SCAN_INTERVAL, DEFAULT_INTERVAL_MINUTES)
                handler.postDelayed(this, currentInterval.toLong() * 60 * 1000)
            }
        }
        scanRunnable = runnable
        // First scan after a short delay
        handler.postDelayed(runnable, 30_000)
    }

    private fun performScan() {
        serviceScope.launch {
            try {
                val findings = vulnerabilityScanner.quickScan()
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val criticalOnly = prefs.getBoolean("critical_alerts_only", false)

                // Get previous finding titles
                val lastFindingTitles = prefs.getStringSet(KEY_LAST_FINDINGS, emptySet()) ?: emptySet()

                // Check for NEW critical findings
                val criticalFindings = findings.filter { it.severity == "CRITICAL" }
                val highFindings = findings.filter { it.severity == "HIGH" }

                val newCriticals = criticalFindings.filter { it.title !in lastFindingTitles }
                val newHighs = highFindings.filter { it.title !in lastFindingTitles }

                if (newCriticals.isNotEmpty()) {
                    val count = newCriticals.size
                    notificationHelper.sendCriticalAlert(
                        title = "⚠ $count New Critical Issue${if (count > 1) "s" else ""} Detected",
                        body = newCriticals.joinToString(", ") { it.title }
                    )
                }

                if (!criticalOnly && newHighs.isNotEmpty()) {
                    val count = newHighs.size
                    notificationHelper.sendWarningAlert(
                        title = "$count New High Severity Issue${if (count > 1) "s" else ""}",
                        body = newHighs.joinToString(", ") { it.title }
                    )
                }

                // Update stored findings
                val allTitles = findings.map { it.title }.toSet()
                prefs.edit().putStringSet(KEY_LAST_FINDINGS, allTitles).apply()

            } catch (_: Exception) {
                // Silently fail in background
            }
        }
    }

    override fun onDestroy() {
        scanRunnable?.let { handler.removeCallbacks(it) }
        serviceScope.cancel()
        super.onDestroy()
    }
}

