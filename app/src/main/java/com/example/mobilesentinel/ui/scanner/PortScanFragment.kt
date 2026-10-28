package com.example.mobilesentinel.ui.scanner

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobilesentinel.R
import com.example.mobilesentinel.databinding.FragmentPortScanBinding
import com.example.mobilesentinel.ui.adapters.PortResultAdapter
import com.example.mobilesentinel.utils.NetworkInfo
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PortScanFragment : Fragment() {

    private var _binding: FragmentPortScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScannerViewModel by viewModels()
    private lateinit var portResultAdapter: PortResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPortScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeScanState()
        observeNetworkInfo()
        observeSecurityScanState()
        // Auto-populate host with detected device IP
        autoPopulateHost()
    }

    private fun setupRecyclerView() {
        portResultAdapter = PortResultAdapter()
        binding.rvPortResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = portResultAdapter
        }
    }

    private fun setupListeners() {
        binding.btnScan.setOnClickListener {
            val currentState = viewModel.scanState.value
            if (currentState is ScanState.SCANNING) {
                viewModel.stopScan()
            } else {
                startScan()
            }
        }

        binding.btnAutoDetect.setOnClickListener {
            viewModel.detectDeviceIp()
            autoPopulateHost()
        }

        binding.btnSecurityScan.setOnClickListener {
            val currentState = viewModel.securityScanState.value
            if (currentState is SecurityScanState.Scanning) {
                viewModel.cancelSecurityScan()
            } else {
                viewModel.startSecurityScan()
            }
        }
    }

    private fun autoPopulateHost() {
        val primaryIp = viewModel.primaryIp.value
        binding.etHost.setText(primaryIp)
    }

    private fun observeNetworkInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.detectedAddresses.collect { addresses ->
                        updateIpChips(addresses)
                    }
                }
                launch {
                    viewModel.primaryIp.collect { ip ->
                        // Update host field if user hasn't manually typed something different
                        val currentHost = binding.etHost.text?.toString()?.trim() ?: ""
                        if (currentHost == "127.0.0.1" || currentHost.isEmpty()) {
                            binding.etHost.setText(ip)
                        }
                    }
                }
                launch {
                    viewModel.connectionType.collect { type ->
                        if (type != "Unknown" && type != "Not Connected") {
                            binding.layoutNetworkInfo.visibility = View.VISIBLE
                            binding.tvNetworkStatus.text = getString(
                                R.string.network_status_format, type, viewModel.primaryIp.value
                            )
                        } else {
                            binding.layoutNetworkInfo.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun updateIpChips(addresses: List<NetworkInfo>) {
        binding.chipGroupIps.removeAllViews()
        val nonLoopback = addresses.filter { it.type != "Loopback" }
        if (nonLoopback.isEmpty()) {
            binding.scrollIpChips.visibility = View.GONE
            return
        }
        binding.scrollIpChips.visibility = View.VISIBLE
        for (info in addresses) {
            val chip = Chip(requireContext()).apply {
                text = getString(R.string.ip_chip_format, info.ipAddress, info.type)
                isCheckable = true
                isCheckedIconVisible = true
                isChecked = info.ipAddress == binding.etHost.text?.toString()?.trim()
                setOnClickListener {
                    binding.etHost.setText(info.ipAddress)
                }
            }
            binding.chipGroupIps.addView(chip)
        }
    }

    // --- Security Scan State Observer ---

    private fun observeSecurityScanState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.securityScanState.collect { state ->
                    handleSecurityScanState(state)
                }
            }
        }
    }

    private fun handleSecurityScanState(state: SecurityScanState) {
        when (state) {
            is SecurityScanState.Idle -> {
                binding.btnSecurityScan.text = getString(R.string.security_scan_btn)
                binding.btnSecurityScan.isEnabled = true
                binding.layoutSecurityProgress.visibility = View.GONE
            }

            is SecurityScanState.Scanning -> {
                binding.btnSecurityScan.text = getString(R.string.security_scan_cancel)
                binding.btnSecurityScan.isEnabled = true
                binding.layoutSecurityProgress.visibility = View.VISIBLE
                binding.layoutScoreResult.visibility = View.GONE
                binding.progressSecurityScan.progress = state.progress
                binding.tvSecurityScanStep.text = state.step
            }

            is SecurityScanState.Complete -> {
                binding.btnSecurityScan.text = getString(R.string.security_scan_btn)
                binding.btnSecurityScan.isEnabled = true
                binding.layoutSecurityProgress.visibility = View.GONE
                binding.layoutScoreResult.visibility = View.VISIBLE

                // Animate the circular progress
                val scoreAnimator = ObjectAnimator.ofInt(
                    binding.progressSecurityScore, "progress", 0, state.score
                )
                scoreAnimator.duration = 1200
                scoreAnimator.interpolator = DecelerateInterpolator()
                scoreAnimator.start()

                binding.tvSecurityScoreValue.text = state.score.toString()

                // Set score label and color based on score
                val (label, color) = when {
                    state.score > 70 -> Pair(
                        getString(R.string.security_status_secure),
                        "#4CAF50".toColorInt()
                    )
                    state.score in 40..70 -> Pair(
                        getString(R.string.security_status_at_risk),
                        "#FF9800".toColorInt()
                    )
                    else -> Pair(
                        getString(R.string.security_status_critical),
                        "#F44336".toColorInt()
                    )
                }
                binding.tvScoreLabel.text = label
                binding.tvScoreLabel.setTextColor(color)
                binding.tvSecurityScoreValue.setTextColor(color)
                binding.tvScoreSummary.text = getString(R.string.security_score_summary)

                // Severity badges
                if (state.criticalCount > 0) {
                    binding.tvCriticalCount.visibility = View.VISIBLE
                    binding.tvCriticalCount.text = getString(
                        R.string.critical_count_format, state.criticalCount
                    )
                } else {
                    binding.tvCriticalCount.visibility = View.GONE
                }

                if (state.highCount > 0) {
                    binding.tvHighCount.visibility = View.VISIBLE
                    binding.tvHighCount.text = getString(
                        R.string.high_count_format, state.highCount
                    )
                } else {
                    binding.tvHighCount.visibility = View.GONE
                }

                Snackbar.make(
                    binding.root,
                    getString(R.string.full_scan_complete, state.score),
                    Snackbar.LENGTH_LONG
                ).show()

                viewModel.resetSecurityScanState()
            }

            is SecurityScanState.Error -> {
                binding.btnSecurityScan.text = getString(R.string.security_scan_btn)
                binding.btnSecurityScan.isEnabled = true
                binding.layoutSecurityProgress.visibility = View.GONE

                Snackbar.make(
                    binding.root,
                    getString(R.string.full_scan_failed, state.message),
                    Snackbar.LENGTH_LONG
                ).show()

                viewModel.resetSecurityScanState()
            }

            is SecurityScanState.Cancelled -> {
                binding.btnSecurityScan.text = getString(R.string.security_scan_btn)
                binding.btnSecurityScan.isEnabled = true
                binding.layoutSecurityProgress.visibility = View.GONE

                Snackbar.make(
                    binding.root,
                    getString(R.string.full_scan_cancelled),
                    Snackbar.LENGTH_SHORT
                ).show()

                viewModel.resetSecurityScanState()
            }
        }
    }

    private fun startScan() {
        val host = binding.etHost.text?.toString()?.trim() ?: "127.0.0.1"
        val startPortText = binding.etStartPort.text?.toString()?.trim()
        val endPortText = binding.etEndPort.text?.toString()?.trim()

        if (host.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_empty_host, Toast.LENGTH_SHORT).show()
            return
        }

        val startPort = startPortText?.toIntOrNull()
        val endPort = endPortText?.toIntOrNull()

        if (startPort == null || endPort == null) {
            Toast.makeText(requireContext(), R.string.error_invalid_ports, Toast.LENGTH_SHORT).show()
            return
        }

        if (startPort < 1 || startPort > 65535 || endPort < 1 || endPort > 65535) {
            Toast.makeText(requireContext(), R.string.error_port_range, Toast.LENGTH_SHORT).show()
            return
        }

        if (startPort >= endPort) {
            Toast.makeText(requireContext(), R.string.error_start_greater, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.startScan(host, startPort, endPort)
    }

    private fun observeScanState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: ScanState) {
        when (state) {
            is ScanState.IDLE -> {
                binding.btnScan.text = getString(R.string.start_scan)
                binding.cardProgress.visibility = View.GONE
                binding.cardSummary.visibility = View.GONE
                binding.cardError.visibility = View.GONE
                binding.tvResultsHeader.visibility = View.GONE
                binding.rvPortResults.visibility = View.GONE
                binding.tvNoResults.visibility = View.GONE
                setInputsEnabled(true)
            }

            is ScanState.SCANNING -> {
                binding.btnScan.text = getString(R.string.stop_scan)
                binding.cardProgress.visibility = View.VISIBLE
                binding.cardSummary.visibility = View.GONE
                binding.cardError.visibility = View.GONE
                binding.tvResultsHeader.visibility = View.GONE
                binding.rvPortResults.visibility = View.GONE
                binding.tvNoResults.visibility = View.GONE
                setInputsEnabled(false)

                val percent = if (state.total > 0) {
                    (state.progress * 100) / state.total
                } else 0

                binding.progressScan.max = state.total
                binding.progressScan.progress = state.progress
                binding.tvScanStatus.text = getString(
                    R.string.scanning_port_status, state.currentPort, state.total
                )
                binding.tvProgressPercent.text = getString(R.string.percent_format, percent)
            }

            is ScanState.COMPLETE -> {
                binding.btnScan.text = getString(R.string.start_scan)
                binding.cardProgress.visibility = View.GONE
                binding.cardError.visibility = View.GONE
                setInputsEnabled(true)

                val results = state.results
                val startPort = binding.etStartPort.text?.toString()?.toIntOrNull() ?: 1
                val endPort = binding.etEndPort.text?.toString()?.toIntOrNull() ?: 1024
                val totalScanned = endPort - startPort + 1
                val openCount = results.size
                val riskyCount = results.count { it.riskFlag }

                binding.cardSummary.visibility = View.VISIBLE
                binding.tvTotalScanned.text = totalScanned.toString()
                binding.tvOpenCount.text = openCount.toString()
                binding.tvRiskyCount.text = riskyCount.toString()

                if (results.isNotEmpty()) {
                    binding.tvResultsHeader.visibility = View.VISIBLE
                    binding.rvPortResults.visibility = View.VISIBLE
                    binding.tvNoResults.visibility = View.GONE
                    portResultAdapter.submitList(results)
                } else {
                    binding.tvResultsHeader.visibility = View.GONE
                    binding.rvPortResults.visibility = View.GONE
                    binding.tvNoResults.visibility = View.VISIBLE
                }
            }

            is ScanState.ERROR -> {
                binding.btnScan.text = getString(R.string.start_scan)
                binding.cardProgress.visibility = View.GONE
                binding.cardSummary.visibility = View.GONE
                binding.cardError.visibility = View.VISIBLE
                binding.tvError.text = state.message
                binding.tvResultsHeader.visibility = View.GONE
                binding.rvPortResults.visibility = View.GONE
                binding.tvNoResults.visibility = View.GONE
                setInputsEnabled(true)
            }
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.etHost.isEnabled = enabled
        binding.etStartPort.isEnabled = enabled
        binding.etEndPort.isEnabled = enabled
        binding.btnAutoDetect.isEnabled = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

