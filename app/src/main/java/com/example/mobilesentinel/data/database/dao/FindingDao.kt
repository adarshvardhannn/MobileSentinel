package com.example.mobilesentinel.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mobilesentinel.data.database.entities.Finding
import kotlinx.coroutines.flow.Flow

@Dao
interface FindingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(finding: Finding): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(findings: List<Finding>)

    @Query("SELECT * FROM findings WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getFindingsBySession(sessionId: Long): Flow<List<Finding>>

    @Query("SELECT * FROM findings WHERE severity = :severity ORDER BY timestamp DESC")
    suspend fun getFindingsBySeverity(severity: String): List<Finding>

    @Query("SELECT * FROM findings WHERE module = :module ORDER BY timestamp DESC")
    suspend fun getFindingsByModule(module: String): List<Finding>

    @Query("SELECT COUNT(*) FROM findings WHERE sessionId = :sessionId AND severity = :severity")
    suspend fun getCountBySeverity(sessionId: Long, severity: String): Int
}

