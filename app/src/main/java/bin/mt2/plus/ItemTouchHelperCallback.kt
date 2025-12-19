package bin.mt2.plus

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ItemTouchHelperCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private var maxSwipeDistance = 35f // dp - 限制在35dp内
    private var pixelsPerDp: Float = 1f
    private var isDragging = false

    init {
        pixelsPerDp = android.content.res.Resources.getSystem().displayMetrics.density
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
            isDragging = isCurrentlyActive

            val maxDistancePx = maxSwipeDistance * pixelsPerDp
            // 限制滑动距离在 [-32dp, 32dp] 范围内
            val clampedDX = dX.coerceIn(-maxDistancePx, maxDistancePx)

            val itemView = viewHolder.itemView
            val mainContent = itemView.findViewById<android.view.View>(R.id.main_content)
            mainContent.translationX = clampedDX

            // 不调用父类的onChildDraw,避免不必要的绘制
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val itemView = viewHolder.itemView
        val mainContent = itemView.findViewById<android.view.View>(R.id.main_content)

        if (isDragging) {
            // 如果用户正在拖动,使用动画平滑复位
            mainContent.animate()
                .translationX(0f)
                .setDuration(150)
                .start()
        } else {
            // 立即复位
            mainContent.translationX = 0f
        }

        isDragging = false
    }

    // 确保永远不会触发onSwiped
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2.0f
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE
    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE
}
