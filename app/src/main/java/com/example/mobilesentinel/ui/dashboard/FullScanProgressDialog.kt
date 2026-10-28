package com.example.mobilesentinel.ui.dashboard

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.mobilesentinel.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FullScanProgressDialog : DialogFragment() {

    private var progressBar: ProgressBar? = null
    private var tvStep: TextView? = null
    private var tvPercent: TextView? = null
    var onCancel: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_full_scan_progress, null)

        progressBar = view.findViewById(R.id.progressScan)
        tvStep = view.findViewById(R.id.tvScanStep)
        tvPercent = view.findViewById(R.id.tvScanPercent)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelScan)

        btnCancel.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }

        isCancelable = false

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    fun updateProgress(step: String, progress: Int) {
        progressBar?.progress = progress
        tvStep?.text = step
        tvPercent?.text = "$progress%"
    }

    companion object {
        const val TAG = "FullScanProgressDialog"
    }
}

