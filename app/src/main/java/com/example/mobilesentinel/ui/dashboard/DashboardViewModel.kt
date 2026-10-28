package com.example.mobilesentinel.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.data.repository.FindingRepository
import com.example.mobilesentinel.data.repository.ScanRepository
import com.example.mobilesentinel.domain.models.DashboardStats
import com.example.mobilesentinel.utils.MasterScanCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FullScanState {
    data object Idle : FullScanState()
    data class Scanning(val step: String, val progress: Int) : FullScanState()
    data class Complete(val score: Int) : FullScanState()
    data class Error(val message: String) : FullScanState()
    data object Cancelled : FullScanState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val findingRepository: FindingRepository,
    private val masterScanCoordinator: MasterScanCoordinator
) : ViewModel() {

    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats.asStateFlow()

    private val _recentFindings = MutableStateFlow<List<Finding>>(emptyList())
    val recentFindings: StateFlow<List<Finding>> = _recentFindings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _fullScanState = MutableStateFlow<FullScanState>(FullScanState.Idle)
    val fullScanState: StateFlow<FullScanState> = _fullScanState.asStateFlow()

    private var scanJob: Job? = null

    init {
        loadDashboard()
    }

    fun startFullScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _fullScanState.value = FullScanState.Scanning("Initializing...", 0)
            try {
                val sessionId = masterScanCoordinator.runFullScan { step, progress ->
                    _fullScanState.value = FullScanState.Scanning(step, progress)
                }
                val session = scanRepository.getSessionById(sessionId)
                val score = session?.overallScore?.toInt() ?: 0
                _fullScanState.value = FullScanState.Complete(score)
                loadDashboard()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _fullScanState.value = FullScanState.Cancelled
            } catch (e: Exception) {
                _fullScanState.value = FullScanState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelFullScan() {
        scanJob?.cancel()
        _fullScanState.value = FullScanState.Cancelled
    }

    fun resetScanState() {
        _fullScanState.value = FullScanState.Idle
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val latestSession = scanRepository.getLatestSession()

                if (latestSession != null) {
                    val criticalCount = findingRepository.getCountBySeverity(
                        latestSession.id, "CRITICAL"
                    )
                    val highCount = findingRepository.getCountBySeverity(
                        latestSession.id, "HIGH"
                    )
                    val mediumCount = findingRepository.getCountBySeverity(
                        latestSession.id, "MEDIUM"
                    )

                    val totalVulnerabilities = criticalCount + highCount + mediumCount

                    val portFindings = findingRepository.getFindingsByModule("PORT")
                    val openPortsCount = portFindings.size

                    val processFindings = findingRepository.getFindingsByModule("PROCESS")
                    val suspiciousAppsCount = processFindings.size

                    val score = latestSession.overallScore
                    val riskStatus = getRiskStatus(score)

                    _dashboardStats.value = DashboardStats(
                        overallScore = score,
                        riskStatus = riskStatus,
                        openPortsCount = openPortsCount,
                        suspiciousAppsCount = suspiciousAppsCount,
                        unresolvedVulnerabilities = totalVulnerabilities,
                        lastScanTime = latestSession.timestamp
                    )

                    // Collect recent findings (take last 5)
                    val allFindings = findingRepository.getFindingsBySeverity("CRITICAL") +
                            findingRepository.getFindingsBySeverity("HIGH") +
                            findingRepository.getFindingsBySeverity("MEDIUM")
                    _recentFindings.value = allFindings
                        .sortedByDescending { it.timestamp }
                        .take(5)
                } else {
                    _dashboardStats.value = DashboardStats()
                    _recentFindings.value = emptyList()
                }
            } catch (_: Exception) {
                _dashboardStats.value = DashboardStats()
                _recentFindings.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getRiskStatus(score: Float): String {
        return when {
            score > 70f -> "SECURE"
            score >= 40f -> "AT RISK"
            else -> "CRITICAL"
        }
    }
}
