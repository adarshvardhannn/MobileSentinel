package com.example.mobilesentinel.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.data.repository.FindingRepository
import com.example.mobilesentinel.data.repository.ScanRepository
import com.example.mobilesentinel.domain.models.AppProcess
import com.example.mobilesentinel.utils.ProcessAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProcessViewModel @Inject constructor(
    private val processAnalyzer: ProcessAnalyzer,
    private val findingRepository: FindingRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _processes = MutableStateFlow<List<AppProcess>>(emptyList())
    val processes: StateFlow<List<AppProcess>> = _processes.asStateFlow()

    private val _filteredProcesses = MutableStateFlow<List<AppProcess>>(emptyList())
    val filteredProcesses: StateFlow<List<AppProcess>> = _filteredProcesses.asStateFlow()

    private val _filterMode = MutableStateFlow("ALL")
    val filterMode: StateFlow<String> = _filterMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentQuery: String = ""

    fun loadProcesses() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apps = processAnalyzer.getRunningApps()
                _processes.value = apps
                applyFilters()
            } catch (e: Exception) {
                _processes.value = emptyList()
                _filteredProcesses.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilter(mode: String) {
        _filterMode.value = mode
        applyFilters()
    }

    fun searchProcesses(query: String) {
        currentQuery = query
        applyFilters()
    }

    private fun applyFilters() {
        var result = _processes.value

        // Apply filter mode
        result = when (_filterMode.value) {
            "FLAGGED" -> result.filter { it.isFlagged }
            "DANGEROUS" -> result.filter { it.dangerousPermCount > 0 }
            else -> result
        }

        // Apply search query
        if (currentQuery.isNotBlank()) {
            val q = currentQuery.lowercase()
            result = result.filter {
                it.appName.lowercase().contains(q) ||
                        it.packageName.lowercase().contains(q)
            }
        }

        _filteredProcesses.value = result
    }

    fun convertFlaggedToFindings(sessionId: Long) {
        viewModelScope.launch {
            val flaggedApps = _processes.value.filter { it.isFlagged }
            val findings = flaggedApps.map { app ->
                val severity = if (app.dangerousPermCount >= 5) "HIGH" else "MEDIUM"
                Finding(
                    sessionId = sessionId,
                    module = "PROCESS",
                    category = "PERMISSION",
                    title = "Flagged App: ${app.appName}",
                    description = app.flagReasons.joinToString("; "),
                    evidence = "Package: ${app.packageName}",
                    severity = severity,
                    affectedComponent = app.packageName,
                    impact = "Application exhibits multiple risk indicators.",
                    remediation = "Review app permissions and consider uninstalling if not needed.",
                    confidence = 0.8f,
                    scoreContribution = if (app.dangerousPermCount >= 5) 20 else 10,
                    timestamp = System.currentTimeMillis()
                )
            }
            if (findings.isNotEmpty()) {
                findingRepository.insertAll(findings)
            }
        }
    }
}

