package com.example.mobilesentinel.utils

import android.content.Context
import com.example.mobilesentinel.data.database.entities.LogEntry
import com.example.mobilesentinel.data.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository
) {

    data class ParsedLog(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    companion object {
        // Pattern: "02-28 14:30:05.123  1234  1234 I ActivityManager: Start proc..."
        // Alternative: "02-28 14:30:05.123 I/ActivityManager( 1234): Start proc..."
        private val LOG_PATTERN_THREADTIME = Regex(
            """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+\d+\s+\d+\s+([VDIWEFS])\s+(\S+)\s*:\s*(.+)$"""
        )
        private val LOG_PATTERN_BRIEF = Regex(
            """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+([VDIWEFS])/(\S+)\(\s*\d+\):\s*(.+)$"""
        )
    }

    suspend fun readDeviceLogs(maxLines: Int = 500): List<ParsedLog> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<ParsedLog>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", maxLines.toString()))
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            reader.useLines { lines ->
                lines.forEach { line ->
                    parseLine(line)?.let { logs.add(it) }
                }
            }

            process.waitFor()
        } catch (e: Exception) {
            // If logcat fails, return empty list
        }
        logs
    }

    private fun parseLine(line: String): ParsedLog? {
        // Try threadtime format first (default on most devices)
        LOG_PATTERN_THREADTIME.matchEntire(line.trim())?.let { match ->
            return ParsedLog(
                timestamp = match.groupValues[1],
                level = match.groupValues[2],
                tag = match.groupValues[3],
                message = match.groupValues[4].trim()
            )
        }

        // Try brief format
        LOG_PATTERN_BRIEF.matchEntire(line.trim())?.let { match ->
            return ParsedLog(
                timestamp = match.groupValues[1],
                level = match.groupValues[2],
                tag = match.groupValues[3],
                message = match.groupValues[4].trim()
            )
        }

        return null
    }

    fun categorizeLog(log: ParsedLog): String {
        val msgLower = log.message.lowercase()
        val tagLower = log.tag.lowercase()

        return when {
            // AUTH_FAILURE
            msgLower.contains("permission denied") ||
            msgLower.contains("authentication failed") ||
            msgLower.contains("uid denied") ||
            msgLower.contains("access denied") -> "AUTH_FAILURE"

            // ROOT_ATTEMPT
            tagLower == "su" || tagLower.contains("su") ||
            msgLower.contains("superuser") ||
            msgLower.contains("root access") -> "ROOT_ATTEMPT"

            // PACKAGE_EVENT
            log.tag == "PackageManager" -> "PACKAGE_EVENT"

            // USB_DEBUG
            msgLower.contains("usb debugging") ||
            msgLower.contains("adbd") -> "USB_DEBUG"

            // APP_CRASH
            log.tag == "AndroidRuntime" && msgLower.contains("fatal exception") -> "APP_CRASH"

            // NETWORK_ANOMALY
            msgLower.contains("sslhandshakeexception") ||
            msgLower.contains("cleartext") -> "NETWORK_ANOMALY"

            else -> "GENERAL"
        }
    }

    fun isAnomaly(log: ParsedLog): Boolean {
        return categorizeLog(log) != "GENERAL"
    }

    suspend fun analyzeAndSave(sessionId: Long): List<LogEntry> = withContext(Dispatchers.IO) {
        val parsedLogs = readDeviceLogs()
        val now = System.currentTimeMillis()

        val logEntries = parsedLogs.map { parsed ->
            val category = categorizeLog(parsed)
            LogEntry(
                sessionId = sessionId,
                timestamp = now,
                level = parsed.level,
                tag = parsed.tag,
                message = parsed.message,
                category = category,
                isAnomaly = category != "GENERAL"
            )
        }

        if (logEntries.isNotEmpty()) {
            logRepository.insertAll(logEntries)
        }

        logEntries
    }
}

