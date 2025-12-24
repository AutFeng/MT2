package bin.mt2.plus.adapter

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import bin.mt2.plus.R
import bin.mt2.plus.model.CustomFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class FileAdapter(
    private var files: List<CustomFile>,
    private val listener: OnItemClickListener,
    private val isLeft: Boolean
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private val TAG = "FileAdapter"

    companion object {
        @SuppressLint("ConstantLocale")
        private val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
    }

    // 上次刷新时间
    private var lastFlashTime = 0L

    // 是否需要触发刷新动画
    private var needFlash = false

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

        Log.i(TAG, "刷新: $position 时间 ${System.currentTimeMillis()- lastFlashTime}")

        val customFile = files[position]

        if (lastFlashTime == 0L){
            needFlash = true
        }else if (System.currentTimeMillis() - lastFlashTime > 100){
            needFlash = false
        }

        if (needFlash){
            // 首先将控件隐藏掉
            holder.itemView.visibility = View.GONE
            holder.itemView.postDelayed({
                holder.itemView.visibility = View.VISIBLE
                lastFlashTime = System.currentTimeMillis()
            }, 50L)

            holder.itemView.postDelayed({
                // 创建一个PathInterpolator，使用贝塞尔曲线控制动画进度
                val interpolator = PathInterpolator(0.42f, 0.2f, 0.58f, 0.8f) // 你可以自定义贝塞尔曲线的值

                // 使用ObjectAnimator和PathInterpolator
                val fadeIn = ObjectAnimator.ofFloat(holder.itemView, "alpha", 0f, 1f)
                fadeIn.duration = 100 // 设置动画持续时间
                fadeIn.interpolator = interpolator // 设置自定义插值器
                fadeIn.start()
            }, 50L) // 每个项延迟0.1秒 (100毫秒)
        }

        // 处理文件名显示
        val fileName = customFile.file.name
        val maxLength = 30 // 最大字符数
        val displayName = if (fileName.length > maxLength) {
            fileName.substring(0, maxLength - 3) + "..."
        } else {
            fileName
        }

        holder.fileName.text = displayName

        // 处理时间显示
        if (customFile.isParent) {
            holder.fileTime.visibility = View.INVISIBLE
        } else {
            holder.fileTime.visibility = View.VISIBLE
            holder.fileTime.text = dateFormat.format(customFile.file.lastModified())
        }

        // 设置图标
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

    //  新增:使用 DiffUtil 更新数据
    fun updateData(newFiles: List<CustomFile>) {

        Log.i(TAG, "触发刷新数据: ${newFiles.size}")

        // val diffCallback = FileDiffCallback(files, newFiles)
        // val diffResult = DiffUtil.calculateDiff(diffCallback)
        // files = newFiles
        // diffResult.dispatchUpdatesTo(this)

        files = newFiles
        lastFlashTime = 0L
        notifyDataSetChanged()

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
