package bin.mt2.plus.callback

import android.graphics.Canvas
import android.graphics.Color
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import bin.mt2.plus.R

/**
 * RecyclerView滑动手势回调
 * 限制滑动距离，滑动松手后item变色表示选中
 */
class ItemTouchHelperCallback(
    private val onItemSelected: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private var maxSwipeDistance = 35f // dp - 限制在35dp内
    private var pixelsPerDp: Float = 1f
    private var isDragging = false

    init {
        pixelsPerDp = android.content.res.Resources.getSystem().displayMetrics.density
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // 禁用position 0（".."item）的滑动
        if (viewHolder.adapterPosition == 0) {
            return makeMovementFlags(0, 0)
        }
        // 其他item可以左右滑动
        return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // 什么都不做,只是为了接口实现
    }

    override fun onChildDraw(
        c: Canvas,
        canvas: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // 只要进入滑动状态就标记（不管dX是否为0）
            if (isCurrentlyActive) {
                isDragging = true
            }

            val maxDistancePx = maxSwipeDistance * pixelsPerDp
            val clampedDX = dX.coerceIn(-maxDistancePx, maxDistancePx)

            val itemView = viewHolder.itemView
            val mainContent = itemView.findViewById<android.view.View>(R.id.main_content)
            mainContent.translationX = clampedDX
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val itemView = viewHolder.itemView
        val mainContent = itemView.findViewById<android.view.View>(R.id.main_content)

        // 立即复位位置（无动画）
        mainContent.translationX = 0f

        // 立即通知选中（无动画）
        if (isDragging) {
            val position = viewHolder.adapterPosition
            // 确保position有效（包括position 0）
            if (position != RecyclerView.NO_POSITION && position >= 0) {
                onItemSelected(position)
            }
        }

        isDragging = false
    }

    // 确保永远不会触发onSwiped
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2.0f
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE
    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE
}
