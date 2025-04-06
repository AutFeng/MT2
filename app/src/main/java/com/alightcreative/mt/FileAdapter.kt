package com.alightcreative.mt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(
    private val files: List<CustomFile>,
    private val listener: OnItemClickListener,
    private val isLeft: Boolean
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(file: File, isLeft: Boolean)
        fun onItemLongClick(file: File, isLeft: Boolean): Boolean
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileTime: TextView = itemView.findViewById(R.id.file_modification_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val customFile = files[position]
        holder.fileName.text = customFile.file.name
        if (customFile.isParent) {
            holder.fileTime.visibility = View.INVISIBLE
        } else {
            holder.fileTime.visibility = View.VISIBLE
            val lastModified = customFile.file.lastModified()
            val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
            holder.fileTime.text = dateFormat.format(Date(lastModified))
        }
        val frameLayout = holder.itemView.findViewById<View>(R.id.file_icon_frame)
        val fileIcon = holder.itemView.findViewById<ImageView>(R.id.file_icon)
        if (customFile.file.isDirectory) {
            frameLayout.setBackgroundResource(R.drawable.rounded_rectangle)
            fileIcon.setImageResource(R.drawable.baseline_folder_24)
        } else {
            frameLayout.setBackgroundResource(R.drawable.rounded_rectangle_file)
            fileIcon.setImageResource(R.drawable.baseline_insert_drive_file_24)
        }
        holder.itemView.setOnClickListener {
            listener.onItemClick(customFile.file, isLeft)
        }
        holder.itemView.setOnLongClickListener {
            listener.onItemLongClick(customFile.file, isLeft)
        }
    }

    override fun getItemCount() = files.size
}