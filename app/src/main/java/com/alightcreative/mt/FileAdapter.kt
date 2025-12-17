package com.alightcreative.mt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(
    private var files: List<CustomFile>,
    private val listener: OnItemClickListener,
    private val isLeft: Boolean
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    companion object {
        private val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
    }

    interface OnItemClickListener {
        fun onItemClick(file: File, isLeft: Boolean)
        fun onItemLongClick(file: File, isLeft: Boolean): Boolean
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileTime: TextView = itemView.findViewById(R.id.file_modification_time)
        //  在 ViewHolder 中缓存 View 引用
        val frameLayout: View = itemView.findViewById(R.id.file_icon_frame)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val customFile = files[position]
        holder.fileName.text = customFile.file.name

        // 处理时间显示
        if (customFile.isParent) {
            holder.fileTime.visibility = View.INVISIBLE
        } else {
            holder.fileTime.visibility = View.VISIBLE
            //  直接使用 companion 的 dateFormat，避免重复创建
            holder.fileTime.text = dateFormat.format(customFile.file.lastModified())
        }

        //  使用缓存的 View 引用
        if (customFile.file.isDirectory) {
            holder.frameLayout.setBackgroundResource(R.drawable.rounded_rectangle)
            holder.fileIcon.setImageResource(R.drawable.baseline_folder_24)
        } else {
            holder.frameLayout.setBackgroundResource(R.drawable.rounded_rectangle_file)
            holder.fileIcon.setImageResource(R.drawable.baseline_insert_drive_file_24)
        }

        holder.itemView.setOnClickListener {
            listener.onItemClick(customFile.file, isLeft)
        }
        holder.itemView.setOnLongClickListener {
            listener.onItemLongClick(customFile.file, isLeft)
        }
    }

    override fun getItemCount() = files.size

    //  新增：使用 DiffUtil 更新数据
    fun updateData(newFiles: List<CustomFile>) {
        val diffCallback = FileDiffCallback(files, newFiles)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        files = newFiles
        diffResult.dispatchUpdatesTo(this)
    }

    //  DiffUtil 回调
    private class FileDiffCallback(
        private val oldList: List<CustomFile>,
        private val newList: List<CustomFile>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].file.absolutePath ==
                    newList[newItemPosition].file.absolutePath
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.file.name == new.file.name &&
                    old.file.lastModified() == new.file.lastModified() &&
                    old.isParent == new.isParent
        }
    }
}