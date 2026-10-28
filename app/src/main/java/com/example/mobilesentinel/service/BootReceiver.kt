package com.example.mobilesentinel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val WORK_NAME = "sentinel_periodic_scan"

        fun enqueuePeriodicWork(context: Context, intervalMinutes: Long = 15) {
            val request = PeriodicWorkRequestBuilder<PeriodicScanWorker>(
                intervalMinutes, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if monitoring was enabled
            val prefs = context.getSharedPreferences("sentinel_settings", Context.MODE_PRIVATE)
            val monitoringEnabled = prefs.getBoolean("background_monitoring", false)

            if (monitoringEnabled) {
                ScanForegroundService.start(context)
            }

            // Always enqueue periodic work
            val interval = prefs.getInt("scan_interval_minutes", 15).toLong()
            enqueuePeriodicWork(context, interval)
        }
    }
}

