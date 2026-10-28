package com.example.mobilesentinel.data.repository

import com.example.mobilesentinel.data.database.dao.ScanSessionDao
import com.example.mobilesentinel.data.database.entities.ScanSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val scanSessionDao: ScanSessionDao
) {
    suspend fun insertSession(session: ScanSession): Long =
        scanSessionDao.insert(session)

    fun getAllSessions(): Flow<List<ScanSession>> =
        scanSessionDao.getAllSessions()

    suspend fun getSessionById(id: Long): ScanSession? =
        scanSessionDao.getSessionById(id)

    suspend fun updateSession(session: ScanSession) =
        scanSessionDao.updateSession(session)

    suspend fun deleteOldSessions(keepCount: Int) =
        scanSessionDao.deleteOldSessions(keepCount)

    suspend fun getLatestSession(): ScanSession? =
        scanSessionDao.getLatestSession()
}

