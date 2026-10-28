package com.example.mobilesentinel.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobilesentinel.R
import com.example.mobilesentinel.databinding.ItemProcessBinding
import com.example.mobilesentinel.domain.models.AppProcess

class ProcessAdapter :
    ListAdapter<AppProcess, ProcessAdapter.ProcessViewHolder>(ProcessDiffCallback()) {

    private val expandedPositions = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProcessViewHolder {
        val binding = ItemProcessBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProcessViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProcessViewHolder, position: Int) {
        val isExpanded = position in expandedPositions
        holder.bind(getItem(position), isExpanded)
        holder.itemView.setOnClickListener {
            val adapterPos = holder.adapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (adapterPos in expandedPositions) {
                expandedPositions.remove(adapterPos)
            } else {
                expandedPositions.add(adapterPos)
            }
            notifyItemChanged(adapterPos)
        }
    }

    class ProcessViewHolder(
        private val binding: ItemProcessBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(process: AppProcess, isExpanded: Boolean) {
            val context = binding.root.context

            // App icon
            try {
                val icon = context.packageManager.getApplicationIcon(process.packageName)
                binding.ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                binding.ivAppIcon.setImageResource(R.mipmap.ic_launcher)
            }

            // App name & package
            binding.tvAppName.text = process.appName
            binding.tvPackageName.text = process.packageName

            // Flagged tag
            binding.tvFlaggedTag.visibility = if (process.isFlagged) View.VISIBLE else View.GONE

            // Memory badge
            val memMB = process.memoryKB / 1024
            binding.tvMemoryBadge.text = context.getString(R.string.memory_mb_format, memMB)

            // Dangerous perm count badge
            val dangerousCount = process.dangerousPermCount
            binding.tvDangerousCount.text = context.getString(
                R.string.dangerous_perm_count_format, dangerousCount
            )
            if (dangerousCount > 2) {
                binding.tvDangerousCount.setBackgroundResource(R.drawable.bg_danger_badge)
                binding.tvDangerousCount.setTextColor(
                    ContextCompat.getColor(context, android.R.color.holo_red_dark)
                )
            } else {
                binding.tvDangerousCount.setBackgroundResource(R.drawable.bg_safe_badge)
                binding.tvDangerousCount.setTextColor(
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)
                )
            }

            // Expand arrow
            binding.ivExpandArrow.rotation = if (isExpanded) 180f else 0f

            // Expandable section
            binding.layoutExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE

            if (isExpanded) {
                // Flag reasons
                if (process.flagReasons.isNotEmpty()) {
                    binding.tvFlagReasonsHeader.visibility = View.VISIBLE
                    binding.tvFlagReasons.visibility = View.VISIBLE
                    binding.tvFlagReasons.text = process.flagReasons.joinToString("\n") { "• $it" }
                } else {
                    binding.tvFlagReasonsHeader.visibility = View.GONE
                    binding.tvFlagReasons.visibility = View.GONE
                }

                // Dangerous permissions
                if (process.dangerousPermCount > 0) {
                    binding.tvDangerousPermsHeader.visibility = View.VISIBLE
                    binding.tvDangerousPerms.visibility = View.VISIBLE
                    // Just show the top permissions that are considered dangerous by the engine logic
                    binding.tvDangerousPerms.text = process.permissionsList
                        .filter { perm -> 
                            val p = perm.substringAfterLast(".")
                            listOf("SMS", "CONTACTS", "LOCATION", "CAMERA", "AUDIO", "PHONE", "ACCOUNTS").any { 
                                p.contains(it, ignoreCase = true) 
                            }
                        }
                        .take(5)
                        .joinToString("\n") { "• ${it.substringAfterLast(".")}" }
                } else {
                    binding.tvDangerousPermsHeader.visibility = View.VISIBLE
                    binding.tvDangerousPerms.visibility = View.VISIBLE
                    binding.tvDangerousPerms.text = context.getString(R.string.no_dangerous_perms)
                }

                // Install source
                binding.tvInstallSource.text = context.getString(
                    R.string.install_source_format, process.installSource
                )
            }
        }
    }

    class ProcessDiffCallback : DiffUtil.ItemCallback<AppProcess>() {
        override fun areItemsTheSame(oldItem: AppProcess, newItem: AppProcess): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppProcess, newItem: AppProcess): Boolean {
            return oldItem == newItem
        }
    }
}

