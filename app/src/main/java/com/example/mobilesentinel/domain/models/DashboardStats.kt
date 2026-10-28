package com.example.mobilesentinel.domain.models

data class DashboardStats(
    val overallScore: Float = 0f,
    val riskStatus: String = "UNKNOWN",
    val openPortsCount: Int = 0,
    val suspiciousAppsCount: Int = 0,
    val unresolvedVulnerabilities: Int = 0,
    val lastScanTime: Long = 0L
)

