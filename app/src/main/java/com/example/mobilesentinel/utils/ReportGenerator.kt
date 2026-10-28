package com.example.mobilesentinel.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import com.example.mobilesentinel.data.database.dao.AIExplanationDao
import com.example.mobilesentinel.data.database.entities.AIExplanationEntity
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.data.database.entities.LogEntry
import com.example.mobilesentinel.data.database.entities.PortResult
import com.example.mobilesentinel.data.database.entities.ScanSession
import com.example.mobilesentinel.data.repository.FindingRepository
import com.example.mobilesentinel.data.repository.LogRepository
import com.example.mobilesentinel.data.repository.PortRepository
import com.example.mobilesentinel.data.repository.ScanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanRepository: ScanRepository,
    private val findingRepository: FindingRepository,
    private val aiExplanationDao: AIExplanationDao,
    private val portRepository: PortRepository,
    private val logRepository: LogRepository
) {

    data class ReportData(
        val session: ScanSession,
        val findings: List<Finding>,
        val aiExplanations: Map<Long, AIExplanationEntity>,
        val portResults: List<PortResult>,
        val logEntries: List<LogEntry>,
        val deviceInfo: String
    )

    companion object {
        private const val PAGE_WIDTH = 595  // A4
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN.toInt()
    }

    suspend fun collectReportData(sessionId: Long): ReportData = withContext(Dispatchers.IO) {
        val session = scanRepository.getSessionById(sessionId)
            ?: throw IllegalStateException("Session not found")
        val findings = findingRepository.getFindingsBySession(sessionId).firstOrNull() ?: emptyList()
        
        val aiExplanations = mutableMapOf<Long, AIExplanationEntity>()
        findings.forEach { finding ->
            aiExplanationDao.getLatestForFindingSync(finding.id)?.let {
                aiExplanations[finding.id] = it
            }
        }

        val portResults = portRepository.getResultsBySession(sessionId).firstOrNull() ?: emptyList()
        val logEntries = logRepository.getLogsBySession(sessionId).firstOrNull() ?: emptyList()
        val deviceInfo = getDeviceInfo()

        ReportData(session, findings, aiExplanations, portResults, logEntries, deviceInfo)
    }

    fun getDeviceInfo(): String {
        val rootDetected = File("/system/bin/su").exists() ||
                File("/system/xbin/su").exists()
        val fingerprint = Build.FINGERPRINT.let {
            if (it.length > 30) "…${it.takeLast(30)}" else it
        }
        return buildString {
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Fingerprint: $fingerprint")
            append("Root Detected: ${if (rootDetected) "Yes" else "No"}")
        }
    }

    suspend fun generatePdf(reportData: ReportData): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val scanDate = dateFormat.format(Date(reportData.session.timestamp))

        var pageNumber = 1

        // ---- Page 1: Cover ----
        val coverInfo = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
        )
        drawCoverPage(coverInfo.canvas, reportData, scanDate)
        document.finishPage(coverInfo)

        // ---- Page 2: Executive Summary ----
        val summaryInfo = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
        )
        drawExecutiveSummary(summaryInfo.canvas, reportData)
        document.finishPage(summaryInfo)

        // ---- Page 3+: Findings Detail ----
        val findingsByModule = reportData.findings.groupBy { it.module }
        val allFindings = mutableListOf<Finding>()
        for ((_, findings) in findingsByModule) {
            allFindings.addAll(findings)
        }

        val findingsPerPage = 5
        val chunks = allFindings.chunked(findingsPerPage)
        for (chunk in chunks) {
            val pageInfo = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            )
            drawFindingsPage(pageInfo.canvas, chunk, pageNumber - 1, reportData.aiExplanations)
            document.finishPage(pageInfo)
        }

        // ---- Last Page: Remediation Checklist ----
        val remediations = reportData.findings.map { it.remediation }.distinct()
        val remChunks = remediations.chunked(20)
        for (chunk in remChunks) {
            val pageInfo = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            )
            drawRemediationPage(pageInfo.canvas, chunk)
            document.finishPage(pageInfo)
        }

        // Save
        val reportsDir = File(context.getExternalFilesDir(null), "Reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(reportsDir, "Report_$timestamp.pdf")

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        file
    }

    private fun drawCoverPage(canvas: Canvas, data: ReportData, scanDate: String) {
        // Dark background
        val bgPaint = Paint().apply { color = Color.parseColor("#1A1A2E") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        val whitePaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        // Title
        whitePaint.textSize = 40f
        whitePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MobileSentinel", MARGIN, 200f, whitePaint)

        // Subtitle
        whitePaint.textSize = 22f
        whitePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Security Report", MARGIN, 240f, whitePaint)

        // Divider line
        val linePaint = Paint().apply { color = Color.parseColor("#4CAF50"); strokeWidth = 3f }
        canvas.drawLine(MARGIN, 260f, PAGE_WIDTH - MARGIN, 260f, linePaint)

        // Device info
        whitePaint.textSize = 14f
        whitePaint.color = Color.parseColor("#BBBBBB")
        var y = 300f
        for (line in data.deviceInfo.split("\n")) {
            canvas.drawText(line, MARGIN, y, whitePaint)
            y += 22f
        }

        // Scan date
        y += 10f
        canvas.drawText("Scan Date: $scanDate", MARGIN, y, whitePaint)

        // Risk Score (large)
        val score = data.session.overallScore.toInt()
        val scorePaint = Paint().apply {
            isAntiAlias = true
            textSize = 120f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = when {
                score >= 80 -> Color.parseColor("#4CAF50")
                score >= 50 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
        }
        canvas.drawText("$score", MARGIN + 150f, 560f, scorePaint)

        whitePaint.textSize = 20f
        whitePaint.color = Color.WHITE
        canvas.drawText("/ 100 Risk Score", MARGIN + 150f, 590f, whitePaint)

        // Footer
        whitePaint.textSize = 10f
        whitePaint.color = Color.parseColor("#666666")
        canvas.drawText(
            "Generated by MobileSentinel — $scanDate",
            MARGIN, PAGE_HEIGHT - 30f, whitePaint
        )
    }

    private fun drawExecutiveSummary(canvas: Canvas, data: ReportData) {
        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1A2E")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 13f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var y = 60f
        canvas.drawText("Executive Summary", MARGIN, y, titlePaint)
        y += 40f

        // Risk Score
        canvas.drawText("Risk Score: ${data.session.overallScore.toInt()} / 100", MARGIN, y, boldPaint)
        y += 30f

        // Severity counts table
        val severities = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")
        val colors = mapOf(
            "CRITICAL" to Color.parseColor("#F44336"),
            "HIGH" to Color.parseColor("#FF5722"),
            "MEDIUM" to Color.parseColor("#FF9800"),
            "LOW" to Color.parseColor("#4CAF50"),
            "INFO" to Color.parseColor("#2196F3")
        )

        canvas.drawText("Severity Breakdown:", MARGIN, y, boldPaint)
        y += 25f

        for (severity in severities) {
            val count = data.findings.count { it.severity == severity }
            val dotPaint = Paint().apply { color = colors[severity] ?: Color.GRAY; isAntiAlias = true }
            canvas.drawCircle(MARGIN + 10f, y - 4f, 5f, dotPaint)
            canvas.drawText("$severity: $count", MARGIN + 25f, y, textPaint)
            y += 20f
        }

        y += 15f

        // Port scan summary
        canvas.drawText("Port Scan Results:", MARGIN, y, boldPaint)
        y += 22f
        val openPorts = data.portResults.count { it.status == "OPEN" }
        val riskyPorts = data.portResults.count { it.riskFlag }
        canvas.drawText("Open Ports: $openPorts    Risky: $riskyPorts", MARGIN + 10f, y, textPaint)
        y += 30f

        // Log analysis summary
        canvas.drawText("Log Analysis:", MARGIN, y, boldPaint)
        y += 22f
        val totalLogs = data.logEntries.size
        val anomalies = data.logEntries.count { it.isAnomaly }
        canvas.drawText("Total Logs: $totalLogs    Anomalies: $anomalies", MARGIN + 10f, y, textPaint)
        y += 40f

        // Top 3 Critical findings
        canvas.drawText("Top Critical Findings:", MARGIN, y, boldPaint)
        y += 25f

        val criticals = data.findings
            .filter { it.severity == "CRITICAL" || it.severity == "HIGH" }
            .take(3)

        if (criticals.isEmpty()) {
            canvas.drawText("No critical findings detected.", MARGIN + 10f, y, textPaint)
        } else {
            for (finding in criticals) {
                val severityPaint = Paint().apply {
                    color = colors[finding.severity] ?: Color.GRAY
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                canvas.drawText("[${finding.severity}]", MARGIN + 10f, y, severityPaint)
                canvas.drawText(finding.title, MARGIN + 80f, y, textPaint)
                y += 18f
                // Truncate description
                val desc = if (finding.description.length > 80) {
                    finding.description.take(80) + "…"
                } else finding.description
                val descPaint = Paint().apply {
                    color = Color.parseColor("#666666")
                    textSize = 11f
                    isAntiAlias = true
                }
                canvas.drawText(desc, MARGIN + 15f, y, descPaint)
                y += 25f
            }
        }
    }

    private fun drawFindingsPage(canvas: Canvas, findings: List<Finding>, pageNum: Int, aiExplanations: Map<Long, AIExplanationEntity>) {
        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1A2E")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 12f
            isAntiAlias = true
        }

        var y = 50f
        canvas.drawText("Findings Detail (Page $pageNum)", MARGIN, y, titlePaint)
        y += 35f

        val colors = mapOf(
            "CRITICAL" to Color.parseColor("#F44336"),
            "HIGH" to Color.parseColor("#FF5722"),
            "MEDIUM" to Color.parseColor("#FF9800"),
            "LOW" to Color.parseColor("#4CAF50"),
            "INFO" to Color.parseColor("#2196F3")
        )

        for (finding in findings) {
            if (y > PAGE_HEIGHT - 120) break

            val aiExplanation = aiExplanations[finding.id]

            // Severity color bar
            val barHeight = if (aiExplanation != null) 100f else 80f
            val barPaint = Paint().apply {
                color = colors[finding.severity] ?: Color.GRAY
            }
            canvas.drawRect(MARGIN, y - 12f, MARGIN + 4f, y + barHeight, barPaint)

            // Severity badge
            val badgePaint = Paint().apply {
                color = colors[finding.severity] ?: Color.GRAY
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(finding.severity, MARGIN + 12f, y, badgePaint)

            // Category & Module
            val modulePaint = Paint().apply {
                color = Color.parseColor("#888888")
                textSize = 10f
                isAntiAlias = true
            }
            canvas.drawText("[${finding.module} | ${finding.category}]", MARGIN + 80f, y, modulePaint)

            // Title
            val titleSmall = Paint().apply {
                color = Color.parseColor("#212121")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            y += 16f
            canvas.drawText(truncate(finding.title, 70), MARGIN + 12f, y, titleSmall)

            // Impact (Deterministic)
            y += 16f
            textPaint.color = Color.parseColor("#D32F2F")
            textPaint.textSize = 10f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("Impact: ${truncate(finding.impact, 85)}", MARGIN + 12f, y, textPaint)

            // AI Explanation (if available)
            if (aiExplanation != null) {
                y += 15f
                textPaint.color = Color.parseColor("#6200EE")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("AI ANALYSIS: ", MARGIN + 12f, y, textPaint)
                
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(truncate(aiExplanation.summary, 75), MARGIN + 90f, y, textPaint)
                
                y += 14f
                textPaint.textSize = 9f
                textPaint.color = Color.parseColor("#444444")
                canvas.drawText("Reasoning: ${truncate(aiExplanation.riskExplanation, 85)}", MARGIN + 12f, y, textPaint)
            }

            // Description
            y += 15f
            textPaint.color = Color.parseColor("#555555")
            textPaint.textSize = 10f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(truncate(finding.description, 90), MARGIN + 12f, y, textPaint)

            // Remediation
            y += 15f
            val remPaint = Paint().apply {
                color = Color.parseColor("#1565C0")
                textSize = 10f
                isAntiAlias = true
            }
            canvas.drawText("→ ${truncate(finding.remediation, 80)}", MARGIN + 12f, y, remPaint)

            y += 40f
        }
    }

    private fun drawRemediationPage(canvas: Canvas, remediations: List<String>) {
        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1A2E")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 12f
            isAntiAlias = true
        }
        val checkPaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 14f
            isAntiAlias = true
        }

        var y = 50f
        canvas.drawText("Remediation Checklist", MARGIN, y, titlePaint)
        y += 35f

        for (rem in remediations) {
            if (y > PAGE_HEIGHT - 50) break
            canvas.drawText("□", MARGIN, y, checkPaint)
            canvas.drawText(truncate(rem, 80), MARGIN + 20f, y, textPaint)
            y += 22f
        }
    }

    private fun truncate(text: String, maxLen: Int): String {
        return if (text.length > maxLen) text.take(maxLen) + "…" else text
    }

    suspend fun exportJson(reportData: ReportData): File = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val root = JSONObject().apply {
            put("generatedAt", dateFormat.format(Date()))
            put("deviceInfo", reportData.deviceInfo)

            val sessionJson = JSONObject().apply {
                put("id", reportData.session.id)
                put("timestamp", reportData.session.timestamp)
                put("duration", reportData.session.duration)
                put("overallScore", reportData.session.overallScore.toDouble())
                put("deviceInfo", reportData.session.deviceInfo)
                put("rootStatus", reportData.session.rootStatus)
                put("sELinuxStatus", reportData.session.sELinuxStatus)
                put("status", reportData.session.status)
            }
            put("session", sessionJson)

            val findingsArray = JSONArray()
            for (f in reportData.findings) {
                val findingJson = JSONObject().apply {
                    put("id", f.id)
                    put("module", f.module)
                    put("category", f.category)
                    put("title", f.title)
                    put("description", f.description)
                    put("evidence", f.evidence)
                    put("severity", f.severity)
                    put("affectedComponent", f.affectedComponent)
                    put("impact", f.impact)
                    put("remediation", f.remediation)
                    put("confidence", f.confidence.toDouble())
                    put("scoreContribution", f.scoreContribution)
                    put("timestamp", f.timestamp)
                    
                    reportData.aiExplanations[f.id]?.let { ai ->
                        put("aiExplanation", JSONObject().apply {
                            put("summary", ai.summary)
                            put("riskExplanation", ai.riskExplanation)
                            put("potentialImpact", ai.potentialImpact)
                            put("severityReason", ai.severityReason)
                            put("scoreExplanation", ai.scoreExplanation)
                            put("mitigation", ai.mitigation)
                            put("userActions", ai.userActions)
                            put("technicalRemediation", ai.technicalRemediation)
                            put("confidenceExplanation", ai.confidenceExplanation)
                            put("limitations", ai.limitations)
                        })
                    }
                }
                findingsArray.put(findingJson)
            }
            put("findings", findingsArray)

            val portsArray = JSONArray()
            for (p in reportData.portResults) {
                portsArray.put(JSONObject().apply {
                    put("port", p.port)
                    put("protocol", p.protocol)
                    put("status", p.status)
                    put("serviceName", p.serviceName)
                    put("riskFlag", p.riskFlag)
                })
            }
            put("portResults", portsArray)

            val logsArray = JSONArray()
            for (l in reportData.logEntries) {
                logsArray.put(JSONObject().apply {
                    put("level", l.level)
                    put("tag", l.tag)
                    put("message", l.message)
                    put("category", l.category)
                    put("isAnomaly", l.isAnomaly)
                })
            }
            put("logEntries", logsArray)
        }

        val reportsDir = File(context.getExternalFilesDir(null), "Reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(reportsDir, "Report_$timestamp.json")
        file.writeText(root.toString(2))

        file
    }
}

