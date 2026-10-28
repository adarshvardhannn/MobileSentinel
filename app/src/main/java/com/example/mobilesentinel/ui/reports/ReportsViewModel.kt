package com.example.mobilesentinel.ui.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilesentinel.data.repository.ScanRepository
import com.example.mobilesentinel.utils.ReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class GenerationState {
    data object IDLE : GenerationState()
    data object GENERATING : GenerationState()
    data class SUCCESS(val file: File) : GenerationState()
    data class ERROR(val message: String) : GenerationState()
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportGenerator: ReportGenerator,
    private val scanRepository: ScanRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _savedReports = MutableStateFlow<List<File>>(emptyList())
    val savedReports: StateFlow<List<File>> = _savedReports.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.IDLE)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    init {
        loadSavedReports()
    }

    fun generateReport() {
        viewModelScope.launch {
            _generationState.value = GenerationState.GENERATING
            try {
                val session = scanRepository.getLatestSession()
                if (session == null) {
                    _generationState.value = GenerationState.ERROR(
                        "No scan data available. Run a scan first."
                    )
                    return@launch
                }

                val reportData = reportGenerator.collectReportData(session.id)
                val pdfFile = reportGenerator.generatePdf(reportData)
                // Also export JSON alongside
                reportGenerator.exportJson(reportData)

                _generationState.value = GenerationState.SUCCESS(pdfFile)
                loadSavedReports()
            } catch (e: Exception) {
                _generationState.value = GenerationState.ERROR(
                    e.message ?: "Failed to generate report"
                )
            }
        }
    }

    fun loadSavedReports() {
        viewModelScope.launch {
            val reportsDir = File(context.getExternalFilesDir(null), "Reports")
            val files = if (reportsDir.exists()) {
                reportsDir.listFiles { file -> file.extension == "pdf" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            _savedReports.value = files
        }
    }

    fun getShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getViewIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun deleteReport(file: File) {
        viewModelScope.launch {
            if (file.exists()) {
                file.delete()
                // Also delete matching JSON if exists
                val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                if (jsonFile.exists()) jsonFile.delete()
            }
            loadSavedReports()
        }
    }

    fun resetState() {
        _generationState.value = GenerationState.IDLE
    }
}

