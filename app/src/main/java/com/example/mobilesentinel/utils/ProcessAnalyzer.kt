package com.example.mobilesentinel.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.mobilesentinel.domain.models.AppProcess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val riskEngine: SecurityRiskEngine
) {

    companion object {
        private val SUSPICIOUS_KEYWORDS = listOf(
            "spy", "hack", "rat", "keylog", "stealth"
        )

        private const val DAYS_180_MS = 180L * 24 * 60 * 60 * 1000
    }

    @Suppress("DEPRECATION")
    suspend fun getRunningApps(): List<AppProcess> = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val packageManager = context.packageManager

        val runningProcesses = activityManager.runningAppProcesses ?: emptyList()

        val processedPackages = mutableSetOf<String>()
        val appProcesses = mutableListOf<AppProcess>()

        for (processInfo in runningProcesses) {
            val packageName = processInfo.processName.split(":").first()
            val isSelf = packageName == context.packageName

            if (packageName in processedPackages) continue
            processedPackages.add(packageName)

            try {
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_PERMISSIONS
                )

                val appLabel = try {
                    packageManager.getApplicationLabel(packageInfo.applicationInfo!!).toString()
                } catch (e: Exception) {
                    packageName
                }

                val appInfo = packageInfo.applicationInfo ?: continue

                // Memory info
                val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                val memoryKB = if (memoryInfo.isNotEmpty()) {
                    memoryInfo[0].totalPss.toLong()
                } else {
                    0L
                }

                // Risk Analysis
                val permissions = extractPermissions(packageInfo)
                val configs = mutableListOf<String>()
                if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                    appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                    configs.add("DEBUGGABLE")
                }
                if (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0 &&
                    appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                    configs.add("ALLOW_BACKUP")
                }

                val installSource = getInstallSource(packageManager, packageName)
                val isSideloaded = installSource !in setOf(
                    "com.android.vending", "com.google.android.packageinstaller",
                    "com.android.packageinstaller", "pre-installed"
                ) && appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0

                val breakdown = riskEngine.calculateScore(permissions, configs, isSideloaded)
                val dangerousPermCount = breakdown.contributors.count { it.reason.contains("permission", ignoreCase = true) }

                // Flag analysis
                val flagReasons = breakdown.contributors.map { "${it.title} (+${it.score})" }.toMutableList()

                val now = System.currentTimeMillis()
                val lastUpdated = packageInfo.lastUpdateTime
                if (lastUpdated > 0 && lastUpdated < now - DAYS_180_MS && !isSelf) {
                    flagReasons.add("Not updated in 180+ days")
                }

                if (SUSPICIOUS_KEYWORDS.any { packageName.lowercase().contains(it) } && !isSelf) {
                    flagReasons.add("Suspicious package name")
                }

                val isFlagged = breakdown.finalScore < 70 && !isSelf

                appProcesses.add(
                    AppProcess(
                        appName = appLabel,
                        packageName = packageName,
                        pid = processInfo.pid,
                        memoryKB = memoryKB,
                        permissionsList = permissions,
                        dangerousPermCount = dangerousPermCount,
                        installSource = installSource,
                        lastUpdated = lastUpdated,
                        isFlagged = isFlagged,
                        flagReasons = flagReasons
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Skip processes we can't resolve
            } catch (_: Exception) {
                // Skip on any other error
            }
        }

        // Sort: flagged first, then by dangerousPermCount descending
        appProcesses.sortedWith(
            compareByDescending<AppProcess> { it.isFlagged }
                .thenByDescending { it.dangerousPermCount }
        )
    }

    private fun extractPermissions(packageInfo: PackageInfo): List<String> {
        return packageInfo.requestedPermissions?.toList() ?: emptyList()
    }

    @Suppress("DEPRECATION")
    private fun getInstallSource(packageManager: PackageManager, packageName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val installSourceInfo = packageManager.getInstallSourceInfo(packageName)
                installSourceInfo.installingPackageName ?: "unknown"
            } else {
                packageManager.getInstallerPackageName(packageName) ?: "unknown"
            }
        } catch (e: Exception) {
            // If the package is a system app or pre-installed
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) {
                    "pre-installed"
                } else {
                    "unknown"
                }
            } catch (_: Exception) {
                "unknown"
            }
        }
    }
}

