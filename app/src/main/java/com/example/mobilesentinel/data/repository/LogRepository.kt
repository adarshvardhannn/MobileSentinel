package com.example.mobilesentinel.data.repository

import com.example.mobilesentinel.data.database.dao.LogEntryDao
import com.example.mobilesentinel.data.database.entities.LogEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val logEntryDao: LogEntryDao
) {
    suspend fun insert(entry: LogEntry): Long =
        logEntryDao.insert(entry)

    suspend fun insertAll(entries: List<LogEntry>) =
        logEntryDao.insertAll(entries)

    fun getLogsBySession(sessionId: Long): Flow<List<LogEntry>> =
        logEntryDao.getLogsBySession(sessionId)

    suspend fun getAnomalies(sessionId: Long): List<LogEntry> =
        logEntryDao.getAnomalies(sessionId)

    suspend fun getByCategory(sessionId: Long, category: String): List<LogEntry> =
        logEntryDao.getByCategory(sessionId, category)
}

