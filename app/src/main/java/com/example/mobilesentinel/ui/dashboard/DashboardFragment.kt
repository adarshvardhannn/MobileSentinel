package com.example.mobilesentinel.ui.dashboard

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobilesentinel.R
import com.example.mobilesentinel.databinding.FragmentDashboardBinding
import com.example.mobilesentinel.domain.models.DashboardStats
import com.example.mobilesentinel.service.BootReceiver
import com.example.mobilesentinel.service.ScanForegroundService
import com.example.mobilesentinel.ui.adapters.RecentAlertAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var alertAdapter: RecentAlertAdapter
    private var progressDialog: FullScanProgressDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupSettings()
        observeState()
    }

    private fun setupRecyclerView() {
        alertAdapter = RecentAlertAdapter()
        binding.rvRecentAlerts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alertAdapter
        }
    }

    private fun setupFab() {
        binding.fabStartScan.setOnClickListener {
            viewModel.startFullScan()
        }
    }

    private fun setupSettings() {
        val prefs = requireContext().getSharedPreferences("sentinel_settings", Context.MODE_PRIVATE)

        // Background Monitoring Switch
        binding.switchMonitoring.isChecked = prefs.getBoolean("background_monitoring", false)
        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_monitoring", isChecked).apply()
            if (isChecked) {
                ScanForegroundService.start(requireContext())
                BootReceiver.enqueuePeriodicWork(requireContext(),
                    prefs.getInt("scan_interval_minutes", 15).toLong())
            } else {
                ScanForegroundService.stop(requireContext())
            }
        }

        // Scan Interval Spinner
        val intervals = arrayOf("15 min", "30 min", "1 hour", "6 hours")
        val intervalValues = intArrayOf(15, 30, 60, 360)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter

        val savedInterval = prefs.getInt("scan_interval_minutes", 15)
        val selectedIndex = intervalValues.indexOf(savedInterval).coerceAtLeast(0)
        binding.spinnerInterval.setSelection(selectedIndex)

        binding.spinnerInterval.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val minutes = intervalValues[pos]
                prefs.edit().putInt("scan_interval_minutes", minutes).apply()
                if (prefs.getBoolean("background_monitoring", false)) {
                    BootReceiver.enqueuePeriodicWork(requireContext(), minutes.toLong())
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Critical Alerts Only Switch
        binding.switchCriticalOnly.isChecked = prefs.getBoolean("critical_alerts_only", false)
        binding.switchCriticalOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("critical_alerts_only", isChecked).apply()
        }

        // Theme Spinner
        val themes = arrayOf("Light", "Dark", "System")
        val themeValues = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        val themeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = themeAdapter

        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val themeIndex = themeValues.indexOf(savedTheme).coerceAtLeast(2)
        binding.spinnerTheme.setSelection(themeIndex)

        binding.spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val mode = themeValues[pos]
                if (prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != mode) {
                    prefs.edit().putInt("theme_mode", mode).apply()
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dashboardStats.collect { stats ->
                        updateDashboard(stats)
                    }
                }
                launch {
                    viewModel.recentFindings.collect { findings ->
                        if (findings.isEmpty()) {
                            binding.tvEmptyAlerts.visibility = View.VISIBLE
                            binding.rvRecentAlerts.visibility = View.GONE
                        } else {
                            binding.tvEmptyAlerts.visibility = View.GONE
                            binding.rvRecentAlerts.visibility = View.VISIBLE
                            alertAdapter.submitList(findings)
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressLoading.visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.fullScanState.collect { state ->
                        handleScanState(state)
                    }
                }
            }
        }
    }

    private fun handleScanState(state: FullScanState) {
        when (state) {
            is FullScanState.Idle -> {
                progressDialog?.dismiss()
                progressDialog = null
            }
            is FullScanState.Scanning -> {
                if (progressDialog == null) {
                    progressDialog = FullScanProgressDialog().apply {
                        onCancel = { viewModel.cancelFullScan() }
                    }
                    progressDialog?.show(childFragmentManager, FullScanProgressDialog.TAG)
                }
                progressDialog?.updateProgress(state.step, state.progress)
            }
            is FullScanState.Complete -> {
                progressDialog?.dismiss()
                progressDialog = null
                Snackbar.make(
                    binding.root,
                    getString(R.string.full_scan_complete, state.score),
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.resetScanState()
            }
            is FullScanState.Error -> {
                progressDialog?.dismiss()
                progressDialog = null
                Snackbar.make(
                    binding.root,
                    getString(R.string.full_scan_failed, state.message),
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.resetScanState()
            }
            is FullScanState.Cancelled -> {
                progressDialog?.dismiss()
                progressDialog = null
                Snackbar.make(
                    binding.root,
                    getString(R.string.full_scan_cancelled),
                    Snackbar.LENGTH_SHORT
                ).show()
                viewModel.resetScanState()
            }
        }
    }

    private fun updateDashboard(stats: DashboardStats) {
        val scoreInt = stats.overallScore.toInt().coerceIn(0, 100)

        val animator = ObjectAnimator.ofInt(binding.progressScore, "progress", 0, scoreInt)
        animator.duration = 1000
        animator.interpolator = DecelerateInterpolator()
        animator.start()

        binding.tvScoreValue.text = getString(R.string.score_format, scoreInt)

        if (stats.lastScanTime > 0) {
            binding.tvLastScan.text = getString(R.string.last_scan_format, formatRelativeTime(stats.lastScanTime))
        } else {
            binding.tvLastScan.text = getString(R.string.no_scans_yet)
        }

        val (bannerColor, bannerTextRes) = when (stats.riskStatus) {
            "SECURE" -> Pair("#4CAF50".toColorInt(), R.string.status_secure)
            "AT RISK" -> Pair("#FF9800".toColorInt(), R.string.status_at_risk)
            "CRITICAL" -> Pair("#F44336".toColorInt(), R.string.status_critical)
            else -> Pair("#9E9E9E".toColorInt(), R.string.status_unknown)
        }
        binding.tvRiskBanner.setBackgroundColor(bannerColor)
        binding.tvRiskBanner.text = getString(bannerTextRes)

        binding.tvOpenPortsCount.text = stats.openPortsCount.toString()
        binding.tvSuspiciousAppsCount.text = stats.suspiciousAppsCount.toString()
        binding.tvVulnerabilitiesCount.text = stats.unresolvedVulnerabilities.toString()
    }

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp == 0L) return "N/A"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "just now"
            minutes < 60 -> "$minutes mins ago"
            hours < 24 -> "$hours hrs ago"
            days < 7 -> "$days days ago"
            else -> "${days / 7} weeks ago"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
