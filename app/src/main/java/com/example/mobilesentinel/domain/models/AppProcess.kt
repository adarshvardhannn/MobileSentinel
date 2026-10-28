package com.example.mobilesentinel.domain.models

data class AppProcess(
    val appName: String,
    val packageName: String,
    val pid: Int,
    val memoryKB: Long,
    val permissionsList: List<String>,
    val dangerousPermCount: Int,
    val installSource: String,
    val lastUpdated: Long,
    val isFlagged: Boolean,
    val flagReasons: List<String>
)

