package com.vrpirate.installeradb.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.vrpirate.installeradb.R
import com.vrpirate.installeradb.data.model.ArchiveItem
import com.vrpirate.installeradb.data.model.InstallStatus
import com.vrpirate.installeradb.utils.SizeFormatter

class ArchiveAdapter(
    private val archives: List<ArchiveItem>,
    private val onInstallClick: (ArchiveItem) -> Unit,
    private val onCancelClick: (ArchiveItem) -> Unit
) : RecyclerView.Adapter<ArchiveAdapter.ArchiveViewHolder>() {
    
    class ArchiveViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
        val tvArchiveName: TextView = view.findViewById(R.id.tvArchiveName)
        val tvMultipartInfo: TextView = view.findViewById(R.id.tvMultipartInfo)
        val tvArchiveSize: TextView = view.findViewById(R.id.tvArchiveSize)
        val btnAction: Button = view.findViewById(R.id.btnAction)
        val progressSection: ViewGroup = view.findViewById(R.id.progressSection)
        val tvProgressText: TextView = view.findViewById(R.id.tvProgressText)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val multipartDetails: ViewGroup = view.findViewById(R.id.multipartDetails)
        val tvMultipartList: TextView = view.findViewById(R.id.tvMultipartList)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArchiveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archive, parent, false)
        return ArchiveViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ArchiveViewHolder, position: Int) {
        val archive = archives[position]
        val context = holder.itemView.context
        
        // Имя
        holder.tvArchiveName.text = archive.name
        
        // Размер
        holder.tvArchiveSize.text = SizeFormatter.formatMbGb(archive.totalSize)
        
        // Многотомный архив
        if (archive.isMultipart) {
            holder.tvMultipartInfo.visibility = View.VISIBLE
            holder.tvMultipartInfo.text = context.getString(
                R.string.multipart_archive,
                archive.parts.size
            )
        } else {
            holder.tvMultipartInfo.visibility = View.GONE
        }
        
        // Статус индикатор
        val statusColor = when (archive.status) {
            InstallStatus.NEW -> R.color.status_new
            InstallStatus.IN_QUEUE, InstallStatus.EXTRACTING, 
            InstallStatus.INSTALLING, InstallStatus.COPYING_CACHE -> R.color.status_in_progress
            InstallStatus.SUCCESS -> R.color.status_success
            InstallStatus.ERROR -> R.color.status_error
        }
        holder.statusIndicator.backgroundTintList = 
            ContextCompat.getColorStateList(context, statusColor)
        
        // Кнопка действия
        when (archive.status) {
            InstallStatus.NEW -> {
                holder.btnAction.text = context.getString(R.string.button_install)
                holder.btnAction.isEnabled = true
                holder.btnAction.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_primary)
                )
                holder.btnAction.setOnClickListener { onInstallClick(archive) }
            }
            InstallStatus.IN_QUEUE -> {
                holder.btnAction.text = context.getString(
                    R.string.button_in_queue, 
                    archive.queuePosition
                )
                holder.btnAction.isEnabled = true
                holder.btnAction.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_disabled)
                )
                holder.btnAction.setOnClickListener { onCancelClick(archive) }
            }
            InstallStatus.EXTRACTING, InstallStatus.INSTALLING, InstallStatus.COPYING_CACHE -> {
                holder.btnAction.text = context.getString(R.string.status_in_progress)
                holder.btnAction.isEnabled = false
                holder.btnAction.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_disabled)
                )
            }
            InstallStatus.SUCCESS -> {
                holder.btnAction.text = context.getString(R.string.button_installed)
                holder.btnAction.isEnabled = false
                holder.btnAction.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_success)
                )
            }
            InstallStatus.ERROR -> {
                holder.btnAction.text = context.getString(R.string.button_error)
                holder.btnAction.isEnabled = true
                holder.btnAction.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_error)
                )
                holder.btnAction.setOnClickListener { onInstallClick(archive) }
            }
        }
        
        // Прогресс
        val showProgress = archive.status in listOf(
            InstallStatus.EXTRACTING,
            InstallStatus.INSTALLING,
            InstallStatus.COPYING_CACHE
        )
        
        if (showProgress) {
            holder.progressSection.visibility = View.VISIBLE
            holder.tvProgressText.text = archive.progressText
            holder.progressBar.progress = archive.progress
        } else {
            holder.progressSection.visibility = View.GONE
        }
        
        // Детали многотомного архива (пока скрыты)
        holder.multipartDetails.visibility = View.GONE
    }
    
    override fun getItemCount() = archives.size
}
