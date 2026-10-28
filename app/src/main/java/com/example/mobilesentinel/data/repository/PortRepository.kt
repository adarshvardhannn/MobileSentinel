package com.example.mobilesentinel.data.repository

import com.example.mobilesentinel.data.database.dao.PortResultDao
import com.example.mobilesentinel.data.database.entities.PortResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortRepository @Inject constructor(
    private val portResultDao: PortResultDao
) {
    suspend fun insert(result: PortResult): Long =
        portResultDao.insert(result)

    suspend fun insertAll(results: List<PortResult>) =
        portResultDao.insertAll(results)

    fun getResultsBySession(sessionId: Long): Flow<List<PortResult>> =
        portResultDao.getResultsBySession(sessionId)

    suspend fun getOpenPorts(sessionId: Long): List<PortResult> =
        portResultDao.getOpenPorts(sessionId)

    suspend fun getRiskyPorts(sessionId: Long): List<PortResult> =
        portResultDao.getRiskyPorts(sessionId)
}

