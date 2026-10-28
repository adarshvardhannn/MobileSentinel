package com.example.mobilesentinel.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobilesentinel.R
import com.example.mobilesentinel.databinding.FragmentReportsBinding
import com.example.mobilesentinel.ui.adapters.ReportAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels()
    private lateinit var reportAdapter: ReportAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        reportAdapter = ReportAdapter(
            onPreview = { file ->
                try {
                    val intent = viewModel.getViewIntent(file)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        R.string.report_no_pdf_viewer,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onShare = { file ->
                try {
                    val intent = viewModel.getShareIntent(file)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        R.string.report_share_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDelete = { file ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.report_delete_title)
                    .setMessage(getString(R.string.report_delete_msg, file.name))
                    .setPositiveButton(R.string.report_delete_confirm) { _, _ ->
                        viewModel.deleteReport(file)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        )
        binding.rvReports.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reportAdapter
        }
    }

    private fun setupListeners() {
        binding.btnGenerateReport.setOnClickListener {
            viewModel.generateReport()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.generationState.collect { state ->
                        updateGenerationUI(state)
                    }
                }
                launch {
                    viewModel.savedReports.collect { reports ->
                        reportAdapter.submitList(reports)
                        binding.layoutEmptyState.visibility =
                            if (reports.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvReports.visibility =
                            if (reports.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun updateGenerationUI(state: GenerationState) {
        when (state) {
            is GenerationState.IDLE -> {
                binding.btnGenerateReport.isEnabled = true
                binding.progressGenerating.visibility = View.GONE
                binding.tvGeneratingStatus.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                binding.tvSuccess.visibility = View.GONE
            }
            is GenerationState.GENERATING -> {
                binding.btnGenerateReport.isEnabled = false
                binding.progressGenerating.visibility = View.VISIBLE
                binding.tvGeneratingStatus.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                binding.tvSuccess.visibility = View.GONE
            }
            is GenerationState.SUCCESS -> {
                binding.btnGenerateReport.isEnabled = true
                binding.progressGenerating.visibility = View.GONE
                binding.tvGeneratingStatus.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                binding.tvSuccess.visibility = View.VISIBLE
                binding.tvSuccess.text = getString(
                    R.string.report_success, state.file.name
                )
            }
            is GenerationState.ERROR -> {
                binding.btnGenerateReport.isEnabled = true
                binding.progressGenerating.visibility = View.GONE
                binding.tvGeneratingStatus.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = state.message
                binding.tvSuccess.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
