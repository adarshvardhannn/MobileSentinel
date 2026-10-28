package com.example.mobilesentinel.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mobilesentinel.data.database.entities.PortResult
import kotlinx.coroutines.flow.Flow

@Dao
interface PortResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: PortResult): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<PortResult>)

    @Query("SELECT * FROM port_results WHERE sessionId = :sessionId ORDER BY port ASC")
    fun getResultsBySession(sessionId: Long): Flow<List<PortResult>>

    @Query("SELECT * FROM port_results WHERE sessionId = :sessionId AND status = 'OPEN' ORDER BY port ASC")
    suspend fun getOpenPorts(sessionId: Long): List<PortResult>

    @Query("SELECT * FROM port_results WHERE sessionId = :sessionId AND riskFlag = 1 ORDER BY port ASC")
    suspend fun getRiskyPorts(sessionId: Long): List<PortResult>
}

