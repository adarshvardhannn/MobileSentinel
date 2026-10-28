package com.example.mobilesentinel.ui.adapters

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobilesentinel.data.database.entities.Finding
import com.example.mobilesentinel.databinding.ItemRecentAlertBinding

class RecentAlertAdapter :
    ListAdapter<Finding, RecentAlertAdapter.AlertViewHolder>(FindingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemRecentAlertBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(
        private val binding: ItemRecentAlertBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(finding: Finding) {
            binding.tvAlertTitle.text = finding.title
            binding.tvModuleBadge.text = finding.module

            // Severity dot color
            val severityColor = getSeverityColor(finding.severity)
            val dotDrawable = binding.viewSeverityDot.background
            if (dotDrawable is GradientDrawable) {
                dotDrawable.setColor(severityColor)
            } else {
                val shape = GradientDrawable()
                shape.shape = GradientDrawable.OVAL
                shape.setColor(severityColor)
                binding.viewSeverityDot.background = shape
            }

            // Timestamp formatted as relative time
            binding.tvTimestamp.text = formatRelativeTime(finding.timestamp)
        }

        private fun getSeverityColor(severity: String): Int {
            return when (severity.uppercase()) {
                "CRITICAL" -> "#F44336".toColorInt()
                "HIGH" -> "#FF5722".toColorInt()
                "MEDIUM" -> "#FF9800".toColorInt()
                "LOW" -> "#4CAF50".toColorInt()
                "INFO" -> "#2196F3".toColorInt()
                else -> "#9E9E9E".toColorInt()
            }
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
    }

    private class FindingDiffCallback : DiffUtil.ItemCallback<Finding>() {
        override fun areItemsTheSame(oldItem: Finding, newItem: Finding): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Finding, newItem: Finding): Boolean {
            return oldItem == newItem
        }
    }
}

