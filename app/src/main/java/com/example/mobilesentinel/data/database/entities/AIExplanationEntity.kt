package com.example.mobilesentinel.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_explanations",
    foreignKeys = [
        ForeignKey(
            entity = Finding::class,
            parentColumns = ["id"],
            childColumns = ["findingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["findingId"])]
)
data class AIExplanationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val findingId: Long,
    val summary: String,
    val riskExplanation: String,
    val potentialImpact: String,
    val severityReason: String,
    val scoreExplanation: String,
    val mitigation: String,
    val userActions: String, // Stored as newline separated string
    val technicalRemediation: String,
    val confidenceExplanation: String,
    val limitations: String,
    val generatedAt: Long,
    val provider: String,
    val model: String,
    val status: String // SUCCESS / ERROR
)
