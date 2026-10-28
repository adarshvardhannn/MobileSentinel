package com.example.mobilesentinel.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilesentinel.data.database.entities.PortResult
import com.example.mobilesentinel.data.database.entities.ScanSession
import com.example.mobilesentinel.data.repository.FindingRepository
import com.example.mobilesentinel.data.repository.PortRepository
import com.example.mobilesentinel.data.repository.ScanRepository
import com.example.mobilesentinel.utils.DeviceNetworkHelper
import com.example.mobilesentinel.utils.MasterScanCoordinator
import com.example.mobilesentinel.utils.NetworkInfo
import com.example.mobilesentinel.utils.PortScannerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanState {
    data object IDLE : ScanState()
    data class SCANNING(val progress: Int, val total: Int, val currentPort: Int) : ScanState()
    data class COMPLETE(val results: List<PortResult>) : ScanState()
    data class ERROR(val message: String) : ScanState()
}

sealed class SecurityScanState {
    data object Idle : SecurityScanState()
    data class Scanning(val step: String, val progress: Int) : SecurityScanState()
    data class Complete(val score: Int, val criticalCount: Int, val highCount: Int, val mediumCount: Int) : SecurityScanState()
    data class Error(val message: String) : SecurityScanState()
    data object Cancelled : SecurityScanState()
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val portScannerEngine: PortScannerEngine,
    private val portRepository: PortRepository,
    private val scanRepository: ScanRepository,
    private val deviceNetworkHelper: DeviceNetworkHelper,
    private val masterScanCoordinator: MasterScanCoordinator,
    private val findingRepository: FindingRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _detectedAddresses = MutableStateFlow<List<NetworkInfo>>(emptyList())
    val detectedAddresses: StateFlow<List<NetworkInfo>> = _detectedAddresses.asStateFlow()

    private val _primaryIp = MutableStateFlow("127.0.0.1")
    val primaryIp: StateFlow<String> = _primaryIp.asStateFlow()

    private val _connectionType = MutableStateFlow("Unknown")
    val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    // Full security scan state
    private val _securityScanState = MutableStateFlow<SecurityScanState>(SecurityScanState.Idle)
    val securityScanState: StateFlow<SecurityScanState> = _securityScanState.asStateFlow()

    private var scanJob: Job? = null
    private var securityScanJob: Job? = null

    init {
        detectDeviceIp()
    }

    fun detectDeviceIp() {
        viewModelScope.launch {
            val addresses = deviceNetworkHelper.getAllNetworkAddresses()
            _detectedAddresses.value = addresses
            _primaryIp.value = deviceNetworkHelper.getPrimaryIpAddress()
            _connectionType.value = deviceNetworkHelper.getConnectionType()
        }
    }

    fun startScan(host: String = "127.0.0.1", startPort: Int = 1, endPort: Int = 1024) {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _scanState.value = ScanState.SCANNING(0, endPort - startPort + 1, startPort)

                val startTime = System.currentTimeMillis()

                val openPorts = portScannerEngine.scanRange(
                    host = host,
                    start = startPort,
                    end = endPort,
                    onProgress = { scanned, total ->
                        val currentPort = startPort + scanned - 1
                        _scanState.value = ScanState.SCANNING(scanned, total, currentPort)
                    }
                )

                val duration = System.currentTimeMillis() - startTime

                // Create scan session
                val connectionType = deviceNetworkHelper.getConnectionType()
                val session = ScanSession(
                    timestamp = System.currentTimeMillis(),
                    duration = duration,
                    overallScore = calculateScore(openPorts),
                    deviceInfo = "$connectionType ($host)",
                    rootStatus = false,
                    sELinuxStatus = "Unknown",
                    status = "COMPLETE"
                )
                val sessionId = scanRepository.insertSession(session)

                // Save port results with sessionId
                val resultsWithSession = openPorts.map { it.copy(sessionId = sessionId) }
                if (resultsWithSession.isNotEmpty()) {
                    portRepository.insertAll(resultsWithSession)
                }

                _scanState.value = ScanState.COMPLETE(resultsWithSession)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _scanState.value = ScanState.IDLE
                } else {
                    _scanState.value = ScanState.ERROR(e.message ?: "Unknown error occurred")
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState.IDLE
    }

    fun resetState() {
        _scanState.value = ScanState.IDLE
    }

    private fun calculateScore(openPorts: List<PortResult>): Float {
        if (openPorts.isEmpty()) return 100f
        val riskyCount = openPorts.count { it.riskFlag }
        val openCount = openPorts.size
        val penalty = (openCount * 3) + (riskyCount * 10)
        return (100f - penalty).coerceIn(0f, 100f)
    }

    // --- Full Security Scan ---

    fun startSecurityScan() {
        if (securityScanJob?.isActive == true) return

        securityScanJob = viewModelScope.launch {
            _securityScanState.value = SecurityScanState.Scanning("Initializing...", 0)
            try {
                val sessionId = masterScanCoordinator.runFullScan { step, progress ->
                    _securityScanState.value = SecurityScanState.Scanning(step, progress)
                }
                val session = scanRepository.getSessionById(sessionId)
                val score = session?.overallScore?.toInt() ?: 0

                val criticalCount = findingRepository.getCountBySeverity(sessionId, "CRITICAL")
                val highCount = findingRepository.getCountBySeverity(sessionId, "HIGH")
                val mediumCount = findingRepository.getCountBySeverity(sessionId, "MEDIUM")

                _securityScanState.value = SecurityScanState.Complete(
                    score = score,
                    criticalCount = criticalCount,
                    highCount = highCount,
                    mediumCount = mediumCount
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _securityScanState.value = SecurityScanState.Cancelled
            } catch (e: Exception) {
                _securityScanState.value = SecurityScanState.Error(
                    e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun cancelSecurityScan() {
        securityScanJob?.cancel()
        securityScanJob = null
        _securityScanState.value = SecurityScanState.Cancelled
    }

    fun resetSecurityScanState() {
        _securityScanState.value = SecurityScanState.Idle
    }
}

