package com.example.mobilesentinel.ui.scanner

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
import com.example.mobilesentinel.databinding.FragmentProcessBinding
import com.example.mobilesentinel.ui.adapters.ProcessAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProcessFragment : Fragment() {

    private var _binding: FragmentProcessBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProcessViewModel by viewModels()
    private lateinit var processAdapter: ProcessAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProcessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        setupSearch()
        observeState()
        viewModel.loadProcesses()
    }

    private fun setupRecyclerView() {
        processAdapter = ProcessAdapter()
        binding.rvProcesses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = processAdapter
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { viewModel.setFilter("ALL") }
        binding.chipFlagged.setOnClickListener { viewModel.setFilter("FLAGGED") }
        binding.chipDangerous.setOnClickListener { viewModel.setFilter("DANGEROUS") }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.searchProcesses(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.searchProcesses(newText ?: "")
                return true
            }
        })
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredProcesses.collect { processes ->
                        processAdapter.submitList(processes)
                        binding.tvEmptyState.visibility =
                            if (processes.isEmpty() && !viewModel.isLoading.value) View.VISIBLE
                            else View.GONE
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressLoading.visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.processes.collect { allProcesses ->
                        val total = allProcesses.size
                        val flagged = allProcesses.count { it.isFlagged }
                        binding.tvTotalApps.text = getString(R.string.total_apps_format, total)
                        binding.tvFlaggedApps.text = getString(R.string.flagged_apps_format, flagged)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

