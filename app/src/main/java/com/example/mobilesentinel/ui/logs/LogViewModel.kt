package com.example.mobilesentinel.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilesentinel.data.database.entities.LogEntry
import com.example.mobilesentinel.data.database.entities.ScanSession
import com.example.mobilesentinel.data.repository.LogRepository
import com.example.mobilesentinel.data.repository.ScanRepository
import com.example.mobilesentinel.utils.LogAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logAnalyzer: LogAnalyzer,
    private val logRepository: LogRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _allLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val allLogs: StateFlow<List<LogEntry>> = _allLogs.asStateFlow()

    private val _filteredLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val filteredLogs: StateFlow<List<LogEntry>> = _filteredLogs.asStateFlow()

    private val _activeCategory = MutableStateFlow("ALL")
    val activeCategory: StateFlow<String> = _activeCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _categoryCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<String, Int>> = _categoryCounts.asStateFlow()

    init {
        // Combine category and search filters reactively
        viewModelScope.launch {
            combine(_allLogs, _activeCategory, _searchQuery) { logs, category, query ->
                var filtered = logs

                // Apply category filter
                if (category != "ALL") {
                    filtered = filtered.filter { it.category == category }
                }

                // Apply search query
                if (query.isNotBlank()) {
                    val q = query.lowercase()
                    filtered = filtered.filter {
                        it.tag.lowercase().contains(q) ||
                                it.message.lowercase().contains(q) ||
                                it.category.lowercase().contains(q)
                    }
                }

                filtered
            }.collect { filtered ->
                _filteredLogs.value = filtered
            }
        }
    }

    fun loadLogs(sessionId: Long = 0L) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val actualSessionId = if (sessionId == 0L) {
                    val session = ScanSession(
                        timestamp = System.currentTimeMillis(),
                        duration = 0,
                        overallScore = 0f,
                        deviceInfo = android.os.Build.MODEL,
                        rootStatus = false,
                        sELinuxStatus = "Unknown",
                        status = "COMPLETE"
                    )
                    scanRepository.insertSession(session)
                } else {
                    sessionId
                }

                val entries = logAnalyzer.analyzeAndSave(actualSessionId)
                _allLogs.value = entries
                updateCategoryCounts(entries)
            } catch (e: Exception) {
                _allLogs.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterByCategory(category: String) {
        _activeCategory.value = category
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    private fun updateCategoryCounts(logs: List<LogEntry>) {
        val counts = mutableMapOf(
            "ALL" to logs.size,
            "AUTH_FAILURE" to 0,
            "ROOT_ATTEMPT" to 0,
            "PACKAGE_EVENT" to 0,
            "USB_DEBUG" to 0,
            "APP_CRASH" to 0,
            "NETWORK_ANOMALY" to 0,
            "GENERAL" to 0
        )
        for (log in logs) {
            counts[log.category] = (counts[log.category] ?: 0) + 1
        }
        _categoryCounts.value = counts
    }
}

