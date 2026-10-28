package com.example.mobilesentinel.data.repository

import com.example.mobilesentinel.data.database.dao.FindingDao
import com.example.mobilesentinel.data.database.entities.Finding
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindingRepository @Inject constructor(
    private val findingDao: FindingDao
) {
    suspend fun insert(finding: Finding): Long =
        findingDao.insert(finding)

    suspend fun insertAll(findings: List<Finding>) =
        findingDao.insertAll(findings)

    fun getFindingsBySession(sessionId: Long): Flow<List<Finding>> =
        findingDao.getFindingsBySession(sessionId)

    suspend fun getFindingsBySeverity(severity: String): List<Finding> =
        findingDao.getFindingsBySeverity(severity)

    suspend fun getFindingsByModule(module: String): List<Finding> =
        findingDao.getFindingsByModule(module)

    suspend fun getCountBySeverity(sessionId: Long, severity: String): Int =
        findingDao.getCountBySeverity(sessionId, severity)
}

