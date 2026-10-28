package com.example.mobilesentinel.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mobilesentinel.data.database.entities.LogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LogEntry>)

    @Query("SELECT * FROM log_entries WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getLogsBySession(sessionId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE sessionId = :sessionId AND isAnomaly = 1 ORDER BY timestamp DESC")
    suspend fun getAnomalies(sessionId: Long): List<LogEntry>

    @Query("SELECT * FROM log_entries WHERE sessionId = :sessionId AND category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(sessionId: Long, category: String): List<LogEntry>
}

