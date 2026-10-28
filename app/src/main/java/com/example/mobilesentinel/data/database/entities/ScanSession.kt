package com.example.mobilesentinel.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val duration: Long,
    val overallScore: Float,
    val deviceInfo: String,
    val rootStatus: Boolean,
    val sELinuxStatus: String,
    val status: String // COMPLETE / INCOMPLETE
)

