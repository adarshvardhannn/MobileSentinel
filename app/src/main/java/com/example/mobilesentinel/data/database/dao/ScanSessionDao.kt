package com.example.mobilesentinel.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mobilesentinel.data.database.entities.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ScanSession): Long

    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ScanSession>>

    @Query("SELECT * FROM scan_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ScanSession?

    @Update
    suspend fun updateSession(session: ScanSession)

    @Query(
        "DELETE FROM scan_sessions WHERE id NOT IN " +
        "(SELECT id FROM scan_sessions ORDER BY timestamp DESC LIMIT :keepCount)"
    )
    suspend fun deleteOldSessions(keepCount: Int)

    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSession(): ScanSession?
}

