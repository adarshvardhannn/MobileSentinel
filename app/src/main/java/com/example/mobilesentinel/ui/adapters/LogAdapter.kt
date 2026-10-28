package com.example.mobilesentinel.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobilesentinel.R
import com.example.mobilesentinel.data.database.entities.LogEntry
import com.example.mobilesentinel.databinding.ItemLogEntryBinding

class LogAdapter :
    ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    private val expandedPositions = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val isExpanded = position in expandedPositions
        holder.bind(getItem(position), isExpanded)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (pos in expandedPositions) {
                expandedPositions.remove(pos)
            } else {
                expandedPositions.add(pos)
            }
            notifyItemChanged(pos)
        }
    }

    class LogViewHolder(
        private val binding: ItemLogEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntry, isExpanded: Boolean) {
            // Timestamp — show raw stored value as formatted string
            binding.tvTimestamp.text = formatTimestamp(entry.timestamp)

            // Level badge
            binding.tvLevelBadge.text = entry.level
            binding.tvLevelBadge.setBackgroundResource(getLevelDrawable(entry.level))

            // Tag (truncated to 20 chars)
            val displayTag = if (entry.tag.length > 20) {
                entry.tag.take(20) + "…"
            } else {
                entry.tag
            }
            binding.tvTag.text = displayTag

            // Category chip (visible only for anomalies)
            if (entry.isAnomaly) {
                binding.tvCategoryChip.visibility = View.VISIBLE
                binding.tvCategoryChip.text = getCategoryShortName(entry.category)
            } else {
                binding.tvCategoryChip.visibility = View.GONE
            }

            // Anomaly background
            if (entry.isAnomaly) {
                binding.layoutRoot.setBackgroundColor(Color.parseColor("#1AFF0000"))
            } else {
                binding.layoutRoot.setBackgroundColor(Color.TRANSPARENT)
            }

            // Message (collapsed/expanded)
            if (isExpanded) {
                binding.tvMessage.visibility = View.GONE
                binding.tvMessageExpanded.visibility = View.VISIBLE
                binding.tvMessageExpanded.text = entry.message
            } else {
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = entry.message
                binding.tvMessageExpanded.visibility = View.GONE
            }
        }

        private fun formatTimestamp(ts: Long): String {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
            return sdf.format(java.util.Date(ts))
        }

        private fun getLevelDrawable(level: String): Int {
            return when (level.uppercase()) {
                "E", "F" -> R.drawable.bg_level_error
                "W" -> R.drawable.bg_level_warning
                "I" -> R.drawable.bg_level_info
                "D" -> R.drawable.bg_level_debug
                "V" -> R.drawable.bg_level_debug
                else -> R.drawable.bg_level_debug
            }
        }

        private fun getCategoryShortName(category: String): String {
            return when (category) {
                "AUTH_FAILURE" -> "AUTH"
                "ROOT_ATTEMPT" -> "ROOT"
                "PACKAGE_EVENT" -> "PKG"
                "USB_DEBUG" -> "USB"
                "APP_CRASH" -> "CRASH"
                "NETWORK_ANOMALY" -> "NET"
                else -> category
            }
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}

