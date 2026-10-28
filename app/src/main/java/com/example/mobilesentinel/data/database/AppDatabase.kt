package com.example.mobilesentinel.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mobilesentinel.data.database.dao.AIExplanationDao
import com.example.mobilesentinel.data.database.dao.FindingDao
import com.example.mobilesentinel.data.database.dao.LogEntryDao
import com.example.mobilesentinel.data.database.dao.PortResultDao
import com.example.mobilesentinel.data.database.dao.ScanSessionDao
import com.example.mobilesentinel.data.database.entities.AIExplanationEntity
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.data.database.entities.LogEntry
import com.example.mobilesentinel.data.database.entities.PortResult
import com.example.mobilesentinel.data.database.entities.ScanSession

@Database(
    entities = [
        ScanSession::class,
        Finding::class,
        LogEntry::class,
        PortResult::class,
        AIExplanationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun findingDao(): FindingDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun portResultDao(): PortResultDao
    abstract fun aiExplanationDao(): AIExplanationDao
}

