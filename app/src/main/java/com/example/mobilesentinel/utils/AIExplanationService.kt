package com.example.mobilesentinel.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.data.repository.AIExplanationRepository
import com.example.mobilesentinel.domain.models.AIExplanationRequest
import com.example.mobilesentinel.domain.models.AIExplanationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIExplanationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AIExplanationRepository
) {

    suspend fun explainFinding(finding: Finding): AIExplanationResult? {
        val request = prepareRequest(finding) ?: return null
        return repository.generateAndSave(finding.id, request)
    }

    private fun prepareRequest(finding: Finding): AIExplanationRequest? {
        val pm = context.packageManager
        val packageName = finding.affectedComponent

        return try {
            val requestedPermissions = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val appInfo = requestedPermissions.applicationInfo ?: return null
            val appName = pm.getApplicationLabel(appInfo).toString()

            val detectedPermissions = requestedPermissions.requestedPermissions?.toList() ?: emptyList()
            val dangerousPerms = detectedPermissions.filter { perm ->
                val p = perm.substringAfterLast(".")
                listOf("SMS", "CONTACTS", "LOCATION", "CAMERA", "AUDIO", "PHONE", "ACCOUNTS").any {
                    p.contains(it, ignoreCase = true)
                }
            }
            val suspiciousPermissionCombinations = buildList {
                if (detectedPermissions.any { it.endsWith("READ_SMS") } && detectedPermissions.any { it.endsWith("INTERNET") }) {
                    add("SMS + Internet")
                }
                if (detectedPermissions.any { it.endsWith("RECORD_AUDIO") } && detectedPermissions.any { it.endsWith("INTERNET") }) {
                    add("Audio + Internet")
                }
                if (detectedPermissions.any { it.endsWith("ACCESS_FINE_LOCATION") } && detectedPermissions.any { it.endsWith("RECEIVE_BOOT_COMPLETED") }) {
                    add("Location + Boot")
                }
            }

            AIExplanationRequest(
                appName = appName,
                packageName = packageName,
                version = requestedPermissions.versionName ?: "Unknown",
                targetSdk = appInfo.targetSdkVersion,
                installerSource = getInstallSource(pm, packageName),
                riskScore = (100 - finding.scoreContribution).coerceIn(0, 100),
                severity = finding.severity,
                detectedPermissions = detectedPermissions,
                dangerousPermissions = dangerousPerms,
                suspiciousPermissionCombinations = suspiciousPermissionCombinations,
                debuggableStatus = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                backupStatus = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
                exportedComponentsCount = getExportedComponentsCount(pm, packageName),
                outdatedStatus = appInfo.targetSdkVersion < Build.VERSION_CODES.TIRAMISU,
                ruleBasedDescription = finding.description,
                ruleBasedImpact = finding.impact,
                ruleBasedRemediation = finding.remediation,
                confidence = finding.confidence,
                scoreContribution = finding.scoreContribution
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getExportedComponentsCount(pm: PackageManager, packageName: String): Int {
        return try {
            val pkgInfo = pm.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES
            )
            (pkgInfo.activities?.count { it.exported } ?: 0) +
                (pkgInfo.services?.count { it.exported } ?: 0) +
                (pkgInfo.receivers?.count { it.exported } ?: 0)
        } catch (_: Exception) {
            0
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstallSource(pm: PackageManager, packageName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName ?: "unknown"
            } else {
                pm.getInstallerPackageName(packageName) ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}
