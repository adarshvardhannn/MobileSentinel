package com.example.mobilesentinel.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class AIExplanationRequest(
    val appName: String,
    val packageName: String,
    val version: String,
    val targetSdk: Int,
    val installerSource: String,
    val riskScore: Int,
    val severity: String,
    val detectedPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val suspiciousPermissionCombinations: List<String>,
    val debuggableStatus: Boolean,
    val backupStatus: Boolean,
    val exportedComponentsCount: Int,
    val outdatedStatus: Boolean,
    val ruleBasedDescription: String,
    val ruleBasedImpact: String,
    val ruleBasedRemediation: String,
    val confidence: Float,
    val scoreContribution: Int
)

@Serializable
data class AIExplanationResult(
    val summary: String,
    val riskExplanation: String,
    val evidenceAnalysis: List<String>,
    val potentialImpact: String,
    val severityReason: String,
    val scoreExplanation: String,
    val mitigation: String,
    val userActions: List<String>,
    val technicalRemediation: String,
    val confidenceExplanation: String,
    val limitations: String
)
