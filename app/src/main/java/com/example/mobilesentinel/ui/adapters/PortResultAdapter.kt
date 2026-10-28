package com.example.mobilesentinel.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobilesentinel.R
import com.example.mobilesentinel.data.database.entities.PortResult
import com.example.mobilesentinel.databinding.ItemPortResultBinding

class PortResultAdapter :
    ListAdapter<PortResult, PortResultAdapter.PortViewHolder>(PortResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val binding = ItemPortResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PortViewHolder(
        private val binding: ItemPortResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(portResult: PortResult) {
            binding.tvPortNumber.text = binding.root.context.getString(
                R.string.port_number_format, portResult.port
            )
            binding.tvServiceName.text = portResult.serviceName

            // Status chip
            binding.tvStatus.text = portResult.status
            if (portResult.status == "OPEN") {
                binding.tvStatus.setBackgroundResource(R.drawable.bg_status_open)
            } else {
                binding.tvStatus.setBackgroundResource(R.drawable.bg_status_closed)
            }

            // Risky badge
            binding.tvRiskyBadge.visibility = if (portResult.riskFlag) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    class PortResultDiffCallback : DiffUtil.ItemCallback<PortResult>() {
        override fun areItemsTheSame(oldItem: PortResult, newItem: PortResult): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PortResult, newItem: PortResult): Boolean {
            return oldItem == newItem
        }
    }
}

