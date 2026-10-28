package com.example.mobilesentinel.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "findings",
    foreignKeys = [
        ForeignKey(
            entity = ScanSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class Finding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val module: String, // PORT / PROCESS / VULNERABILITY / LOG
    val category: String = "GENERAL", // PRIVACY / PERMISSION / CONFIG / NETWORK
    val title: String,
    val description: String,
    val evidence: String = "",
    val severity: String, // CRITICAL / HIGH / MEDIUM / LOW / INFO
    val affectedComponent: String = "",
    val impact: String = "",
    val remediation: String,
    val confidence: Float = 1.0f,
    val scoreContribution: Int = 0,
    val timestamp: Long
)

