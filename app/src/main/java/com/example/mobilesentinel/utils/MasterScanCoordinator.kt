package com.example.mobilesentinel.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.data.database.entities.ScanSession
import com.example.mobilesentinel.data.repository.FindingRepository
import com.example.mobilesentinel.data.repository.PortRepository
import com.example.mobilesentinel.data.repository.ScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterScanCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val portScannerEngine: PortScannerEngine,
    private val processAnalyzer: ProcessAnalyzer,
    private val vulnerabilityScanner: VulnerabilityScanner,
    private val logAnalyzer: LogAnalyzer,
    private val scanRepository: ScanRepository,
    private val findingRepository: FindingRepository,
    private val portRepository: PortRepository,
    private val notificationHelper: NotificationHelper,
    private val deviceNetworkHelper: DeviceNetworkHelper,
    private val riskEngine: SecurityRiskEngine
) {

    private val TAG = "MasterScanCoordinator"

    suspend fun runFullScan(
        onStep: (step: String, progress: Int) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting full scan")

        // Step 1: Create session
        onStep("Initializing scan...", 0)
        val deviceInfo = "${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val session = ScanSession(
            timestamp = startTime,
            duration = 0,
            overallScore = 0f,
            deviceInfo = deviceInfo,
            rootStatus = RootChecker.isRooted(),
            sELinuxStatus = getSELinuxStatus(),
            status = "INCOMPLETE"
        )
        val sessionId = scanRepository.insertSession(session)
        Log.d(TAG, "Created session $sessionId")

        try {
            // Step 2: Port scan
            onStep("Scanning ports...", 10)
            val scanHost = deviceNetworkHelper.getPrimaryIpAddress()
            Log.d(TAG, "Scanning ports for $scanHost")
            val portResults = portScannerEngine.scanRange(scanHost, 1, 1024) { _, _ -> }
            val portResultsWithSession = portResults.map { it.copy(sessionId = sessionId) }
            portRepository.insertAll(portResultsWithSession)

            val portFindings = portResultsWithSession.filter { it.status == "OPEN" }.map { port ->
                Finding(
                    sessionId = sessionId,
                    module = "PORT",
                    category = "NETWORK",
                    title = "Open Port: ${port.port} (${port.serviceName})",
                    description = "Port ${port.port} is open running ${port.serviceName}",
                    evidence = "TCP Port ${port.port} is responding",
                    severity = if (port.riskFlag) "HIGH" else "MEDIUM",
                    affectedComponent = "Port ${port.port}",
                    impact = if (port.riskFlag) "Known vulnerable or suspicious service port is exposed." else "Service is accessible over the network.",
                    remediation = if (port.riskFlag) "Close risky port ${port.port} immediately"
                    else "Review if port ${port.port} needs to be open",
                    confidence = 1.0f,
                    scoreContribution = if (port.riskFlag) 15 else 5,
                    timestamp = System.currentTimeMillis()
                )
            }

            // Step 3: Process analysis
            onStep("Analyzing running apps...", 30)
            val processes = processAnalyzer.getRunningApps()
            val processFindings = processes.filter { it.isFlagged }.map { app ->
                Finding(
                    sessionId = sessionId,
                    module = "PROCESS",
                    category = "PERMISSION",
                    title = "Flagged App: ${app.appName}",
                    description = "Reasons: ${app.flagReasons.joinToString(", ")}",
                    evidence = "Package: ${app.packageName}",
                    severity = if (app.dangerousPermCount >= 5) "HIGH" else "MEDIUM",
                    affectedComponent = app.packageName,
                    impact = "App has suspicious characteristics or high permission counts.",
                    remediation = "Review app permissions and uninstall if suspicious",
                    confidence = 0.8f,
                    scoreContribution = if (app.dangerousPermCount >= 5) 20 else 10,
                    timestamp = System.currentTimeMillis()
                )
            }

            // Step 4: Vulnerability scan
            onStep("Checking vulnerabilities...", 50)
            val vulnFindings = vulnerabilityScanner.scanAllApps()
            val vulnWithSession = vulnFindings.map { it.copy(sessionId = sessionId) }

            // Step 5: Log analysis
            onStep("Reading security logs...", 70)
            logAnalyzer.analyzeAndSave(sessionId)

            // Step 6: Calculate score
            onStep("Calculating risk score...", 85)
            val allFindings = portFindings + processFindings + vulnWithSession
            findingRepository.insertAll(allFindings)

            // Aggregate risk score
            val nonSelfFindings = allFindings.filter { !it.title.contains("(Self)") }
            val appGroups = nonSelfFindings.groupBy { it.affectedComponent }
            var totalDeduction = 0
            appGroups.forEach { (_, appFindings) ->
                val maxSeverity = when {
                    appFindings.any { it.severity == "CRITICAL" } -> 25
                    appFindings.any { it.severity == "HIGH" } -> 15
                    appFindings.any { it.severity == "MEDIUM" } -> 5
                    else -> 1
                }
                totalDeduction += maxSeverity
            }
            
            val score = (100 - totalDeduction).coerceIn(0, 100).toFloat()
            val criticalCount = allFindings.count { it.severity == "CRITICAL" }
            val highCount = allFindings.count { it.severity == "HIGH" }

            // Step 7: Update session
            onStep("Saving results...", 95)
            val duration = System.currentTimeMillis() - startTime
            val updatedSession = session.copy(
                id = sessionId,
                overallScore = score,
                duration = duration,
                status = "COMPLETE"
            )
            scanRepository.updateSession(updatedSession)

            // Step 8: Notify
            onStep("Scan complete!", 100)
            notificationHelper.sendScanComplete(score, criticalCount, highCount)

            sessionId
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            val duration = System.currentTimeMillis() - startTime
            val incompleteSession = session.copy(
                id = sessionId,
                duration = duration,
                status = "INCOMPLETE"
            )
            scanRepository.updateSession(incompleteSession)
            throw e
        }
    }

    private fun getSELinuxStatus(): String {
        return try {
            val process = Runtime.getRuntime().exec("getenforce")
            val result = process.inputStream.bufferedReader().readLine() ?: "Unknown"
            process.waitFor()
            result
        } catch (_: Exception) {
            "Unknown"
        }
    }
}
