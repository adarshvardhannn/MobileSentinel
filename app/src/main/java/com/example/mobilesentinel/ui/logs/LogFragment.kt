package com.example.mobilesentinel.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobilesentinel.R
import com.example.mobilesentinel.databinding.FragmentLogBinding
import com.example.mobilesentinel.ui.adapters.LogAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LogViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupChips()
        setupSearch()
        setupCaptureButton()
        observeState()

        // Auto-load on first visit
        if (viewModel.allLogs.value.isEmpty()) {
            viewModel.loadLogs()
        }
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter()
        binding.rvLogs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
        }
    }

    private fun setupChips() {
        binding.chipAll.setOnClickListener { viewModel.filterByCategory("ALL") }
        binding.chipAuth.setOnClickListener { viewModel.filterByCategory("AUTH_FAILURE") }
        binding.chipRoot.setOnClickListener { viewModel.filterByCategory("ROOT_ATTEMPT") }
        binding.chipPackages.setOnClickListener { viewModel.filterByCategory("PACKAGE_EVENT") }
        binding.chipUsb.setOnClickListener { viewModel.filterByCategory("USB_DEBUG") }
        binding.chipCrashes.setOnClickListener { viewModel.filterByCategory("APP_CRASH") }
        binding.chipNetwork.setOnClickListener { viewModel.filterByCategory("NETWORK_ANOMALY") }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.search(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText ?: "")
                return true
            }
        })
    }

    private fun setupCaptureButton() {
        binding.btnCaptureLogs.setOnClickListener {
            viewModel.loadLogs()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredLogs.collect { logs ->
                        logAdapter.submitList(logs)
                        val hasLogs = viewModel.allLogs.value.isNotEmpty()
                        binding.rvLogs.visibility =
                            if (logs.isNotEmpty()) View.VISIBLE else View.GONE
                        binding.tvEmptyState.visibility =
                            if (!hasLogs && !viewModel.isLoading.value) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressLoading.visibility =
                            if (loading) View.VISIBLE else View.GONE
                        binding.btnCaptureLogs.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.allLogs.collect { allLogs ->
                        val total = allLogs.size
                        val anomalies = allLogs.count { it.isAnomaly }
                        binding.tvLogSummary.text = getString(
                            R.string.log_summary_format, total, anomalies
                        )
                    }
                }
                launch {
                    viewModel.categoryCounts.collect { counts ->
                        updateChipCounts(counts)
                    }
                }
            }
        }
    }

    private fun updateChipCounts(counts: Map<String, Int>) {
        val allCount = counts["ALL"] ?: 0
        val authCount = counts["AUTH_FAILURE"] ?: 0
        val rootCount = counts["ROOT_ATTEMPT"] ?: 0
        val pkgCount = counts["PACKAGE_EVENT"] ?: 0
        val usbCount = counts["USB_DEBUG"] ?: 0
        val crashCount = counts["APP_CRASH"] ?: 0
        val netCount = counts["NETWORK_ANOMALY"] ?: 0

        binding.chipAll.text = getString(R.string.log_chip_all_count, allCount)
        binding.chipAuth.text = getString(R.string.log_chip_count_format, "Auth", authCount)
        binding.chipRoot.text = getString(R.string.log_chip_count_format, "Root", rootCount)
        binding.chipPackages.text = getString(R.string.log_chip_count_format, "Pkg", pkgCount)
        binding.chipUsb.text = getString(R.string.log_chip_count_format, "USB", usbCount)
        binding.chipCrashes.text = getString(R.string.log_chip_count_format, "Crash", crashCount)
        binding.chipNetwork.text = getString(R.string.log_chip_count_format, "Net", netCount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
