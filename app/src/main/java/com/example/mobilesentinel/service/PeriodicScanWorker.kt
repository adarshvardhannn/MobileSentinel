package com.example.mobilesentinel.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mobilesentinel.utils.NotificationHelper
import com.example.mobilesentinel.utils.VulnerabilityScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PeriodicScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val vulnerabilityScanner: VulnerabilityScanner,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val findings = vulnerabilityScanner.quickScan()
            val criticals = findings.filter { it.severity == "CRITICAL" }
            val highs = findings.filter { it.severity == "HIGH" }

            if (criticals.isNotEmpty()) {
                notificationHelper.sendCriticalAlert(
                    title = "⚠ ${criticals.size} Critical Issue${if (criticals.size > 1) "s" else ""} Found",
                    body = criticals.joinToString(", ") { it.title }
                )
            }

            if (highs.isNotEmpty()) {
                val prefs = applicationContext.getSharedPreferences(
                    "sentinel_settings", Context.MODE_PRIVATE
                )
                val criticalOnly = prefs.getBoolean("critical_alerts_only", false)
                if (!criticalOnly) {
                    notificationHelper.sendWarningAlert(
                        title = "${highs.size} High Severity Issue${if (highs.size > 1) "s" else ""}",
                        body = highs.joinToString(", ") { it.title }
                    )
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

