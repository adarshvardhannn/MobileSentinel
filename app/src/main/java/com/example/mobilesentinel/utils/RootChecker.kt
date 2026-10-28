package com.example.mobilesentinel.utils

import java.io.File

object RootChecker {

    fun isRooted(): Boolean {
        return checkSuBinaries() || checkPathSu()
    }

    private fun checkSuBinaries(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su")
        return paths.any { File(it).exists() }
    }

    private fun checkPathSu(): Boolean {
        return try {
            val pathEnv = System.getenv("PATH") ?: return false
            pathEnv.split(":").any { File("$it/su").exists() }
        } catch (_: Exception) {
            false
        }
    }
}

