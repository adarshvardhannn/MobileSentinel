package com.example.mobilesentinel.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobilesentinel.R
import com.example.mobilesentinel.databinding.ItemReportBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportAdapter(
    private val onPreview: (File) -> Unit,
    private val onShare: (File) -> Unit,
    private val onDelete: (File) -> Unit
) : ListAdapter<File, ReportAdapter.ReportViewHolder>(ReportDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val binding = ItemReportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ReportViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReportViewHolder(
        private val binding: ItemReportBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            binding.tvFileName.text = file.name

            // File size + date
            val sizeKB = file.length() / 1024
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
            val dateStr = dateFormat.format(Date(file.lastModified()))
            binding.tvFileInfo.text = binding.root.context.getString(
                R.string.report_file_info_format, sizeKB, dateStr
            )

            binding.btnPreview.setOnClickListener { onPreview(file) }
            binding.btnShare.setOnClickListener { onShare(file) }
            binding.btnDelete.setOnClickListener { onDelete(file) }
        }
    }

    class ReportDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath &&
                    oldItem.lastModified() == newItem.lastModified()
        }
    }
}

