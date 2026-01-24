package bin.mt2.plus.adapter

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
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
    private val selectedPositions = mutableSetOf<Int>()
    private var isSelectionMode = false
    private var firstSwipePosition: Int? = null // 记录第一个滑动选中的position

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

    fun toggleSelection(position: Int) {
        // 确保position有效（包括position 0的".."item）
        if (position < 0 || position >= files.size) {
            return
        }

        if (position in selectedPositions) {
            selectedPositions.remove(position)
            // 如果没有选中项了，退出选中模式
            if (selectedPositions.isEmpty()) {
                isSelectionMode = false
                firstSwipePosition = null // 清除第一个位置记录
            }
        } else {
            selectedPositions.add(position)
            // 进入选中模式
            isSelectionMode = true
        }
        // 使用payload避免触发完整的bind动画
        notifyItemChanged(position, "selection")
    }

    fun selectIfNotSelected(position: Int) {
        // 确保position有效
        if (position < 0 || position >= files.size) {
            return
        }

        // 如果不在选中模式，选中item并进入选中模式，同时记录为第一个位置
        if (!isSelectionMode) {
            selectedPositions.add(position)
            isSelectionMode = true
            firstSwipePosition = position // 记录第一个滑动位置
            notifyItemChanged(position, "selection")
            return
        }

        // 在选中模式下，实现范围选择
        if (firstSwipePosition == null) {
            // 第一次滑动（在选中模式下），记录位置并选中
            firstSwipePosition = position
            selectedPositions.add(position)
            notifyItemChanged(position, "selection")
        } else {
            // 第二次滑动，选中范围内的所有item（包括第一次滑动的item）
            val start = minOf(firstSwipePosition!!, position)
            val end = maxOf(firstSwipePosition!!, position)

            for (i in start..end) {
                selectedPositions.add(i)
                notifyItemChanged(i, "selection")
            }

            // 清除第一个位置的记录
            firstSwipePosition = null
        }
    }

    fun inSelectionMode(): Boolean {
        return isSelectionMode
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileTime: TextView = itemView.findViewById(R.id.file_modification_time)
        val mainContent: View = itemView.findViewById(R.id.main_content)

        //  在 ViewHolder 中缓存 View 引用
        val frameLayout: View = itemView.findViewById(R.id.file_icon_frame)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "selection") {
            // 只更新选中状态背景，不触发其他逻辑
            if (position in selectedPositions) {
                holder.mainContent.setBackgroundResource(R.drawable.item_background_selected)
                holder.itemView.setBackgroundResource(R.drawable.item_background_selected)
            } else {
                holder.mainContent.setBackgroundResource(R.drawable.item_background_normal)
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
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
                val interpolator = PathInterpolator(0.42f, 0.2f, 0.58f, 0.8f)

                // 使用ObjectAnimator和PathInterpolator
                val fadeIn = ObjectAnimator.ofFloat(holder.itemView, "alpha", 0f, 1f)
                fadeIn.duration = 100 // 设置动画持续时间
                fadeIn.interpolator = interpolator // 设置自定义插值器
                fadeIn.start()
            }, 50L)
        }

        // 设置选中状态背景（在动画之后设置，避免冲突）
        if (position in selectedPositions) {
            holder.mainContent.setBackgroundResource(R.drawable.item_background_selected)
            holder.itemView.setBackgroundResource(R.drawable.item_background_selected)
        } else {
            holder.mainContent.setBackgroundResource(R.drawable.item_background_normal)
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
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

        // 设置文件名颜色：新创建的显示绿色，其他显示黑色
        if (customFile.isNewlyCreated) {
            holder.fileName.setTextColor(0xFF00C853.toInt()) // 绿色
        } else {
            holder.fileName.setTextColor(0xFF000000.toInt()) // 黑色
        }

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
            
            // 检查是否是Android/data或Android/obb下的APP文件夹
            loadAppIconIfNeeded(holder, customFile.file)
        } else {
            holder.frameLayout.setBackgroundResource(R.drawable.rounded_rectangle_file)
            holder.fileIcon.setImageResource(R.drawable.baseline_insert_drive_file_24)
            holder.appIcon.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            // ".."item始终执行正常点击，不参与选中
            if (customFile.isParent) {
                listener.onItemClick(customFile.file, isLeft)
            } else if (isSelectionMode) {
                // 选中模式下，其他item切换选中状态
                toggleSelection(position)
            } else {
                // 非选中模式下，正常处理点击
                listener.onItemClick(customFile.file, isLeft)
            }
        }
        holder.itemView.setOnLongClickListener {
            listener.onItemLongClick(customFile.file, isLeft)
        }
    }

    override fun getItemCount() = files.size

    //  新增:使用 DiffUtil 更新数据
    fun updateData(newFiles: List<CustomFile>) {

        Log.i(TAG, "触发刷新数据: ${newFiles.size}")

        // 清空选中状态
        selectedPositions.clear()
        isSelectionMode = false
        firstSwipePosition = null // 清除第一个位置记录

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

    /**
     * 检查并加载APP图标（如果是Android/data或Android/obb下的文件夹）
     */
    private fun loadAppIconIfNeeded(holder: FileViewHolder, file: File) {
        val path = file.absolutePath
        val packageName = file.name
        
        Log.d(TAG, "========== 检查文件 ==========")
        Log.d(TAG, "文件路径: $path")
        Log.d(TAG, "文件名(包名): $packageName")
        
        // 使用File对象的parent来判断
        val parentPath = file.parent ?: ""
        Log.d(TAG, "父目录路径: $parentPath")
        
        // 移除零宽字符和其他不可见字符，用于路径匹配
        val cleanParentPath = parentPath.replace(Regex("[\u200B-\u200D\uFEFF]"), "")
        Log.d(TAG, "清理后的父目录: $cleanParentPath")
        
        // 检查父目录是否以Android/data或Android/obb结尾
        val isInAndroidData = cleanParentPath.endsWith("/Android/data") || 
                              cleanParentPath.endsWith("\\Android\\data")
        val isInAndroidObb = cleanParentPath.endsWith("/Android/obb") || 
                             cleanParentPath.endsWith("\\Android\\obb")
        
        Log.d(TAG, "是Android/data直接子目录: $isInAndroidData")
        Log.d(TAG, "是Android/obb直接子目录: $isInAndroidObb")
        
        if (isInAndroidData || isInAndroidObb) {
            Log.d(TAG, "✓ 符合条件，尝试加载APP图标")
            try {
                val pm = holder.itemView.context.packageManager
                Log.d(TAG, "获取PackageManager成功")
                
                val appInfo = pm.getApplicationInfo(packageName, 0)
                Log.d(TAG, "找到应用信息: ${appInfo.packageName}")
                
                val appIcon = pm.getApplicationIcon(appInfo)
                Log.d(TAG, "获取应用图标成功")
                
                holder.appIcon.setImageDrawable(appIcon)
                holder.appIcon.visibility = View.VISIBLE
                Log.d(TAG, "✓✓✓ 成功显示APP图标: $packageName")
            } catch (e: PackageManager.NameNotFoundException) {
                // 应用未安装，不显示图标
                holder.appIcon.visibility = View.GONE
                Log.w(TAG, "✗ 应用未安装: $packageName")
            } catch (e: Exception) {
                holder.appIcon.visibility = View.GONE
                Log.e(TAG, "✗✗ 加载图标出错: ${e.message}", e)
            }
        } else {
            holder.appIcon.visibility = View.GONE
            Log.d(TAG, "✗ 不符合条件，不显示图标")
        }
        Log.d(TAG, "================================")
    }
}
