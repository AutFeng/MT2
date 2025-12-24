package bin.mt2.plus.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * 自定义 RecyclerView,支持快速滚动条功能
 */
class FastScrollRecyclerView : RecyclerView {

    // ==================== 滚动条尺寸配置 ====================
    private var fastScrollerWidth: Int = dpToPx(8)
    private var fastScrollerMargin: Int = dpToPx(0)
    private var fastScrollerCornerRadius: Int = dpToPx(0)
    private var fastScrollerMinHeight: Int = dpToPx(34)
    private var fastScrollerMaxHeight: Int = dpToPx(48)
    private var touchAreaExtension: Int = dpToPx(6) // 触摸区域扩展像素

    // ==================== 配置参数 ====================
    private var itemCountThreshold: Int = 45 // item数量阈值,超过此值才显示滚动条
    private var isScrollbarEnabled: Boolean = true // 是否启用滚动条功能

    // ==================== 画笔对象 ====================
    private val fastScrollerPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888") // 正常状态:灰色
    }

    private val fastScrollerDraggingPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5") // 拖动状态:蓝色
    }

    // ==================== 滚动条矩形区域 ====================
    private val fastScrollerRect: RectF = RectF()
    private val currentFastScrollerRect: RectF = RectF() // 动画中的矩形

    // ==================== 状态标志 ====================
    private var isFastScrollerVisible: Boolean = false // 是否可见
    private var isDraggingFastScroller: Boolean = false // 是否正在拖动
    private var isTouchingRecyclerView: Boolean = false // 是否正在触摸列表
    private var isHiding: Boolean = false // 是否正在隐藏动画中

    // ==================== 动画相关 ====================
    private var hideAnimationProgress: Float = 0f // 隐藏动画进度
    private var hideAnimator: android.animation.ValueAnimator? = null

    // ==================== 滚动控制 ====================
    private var lastScrollPosition: Int = -1 // 上次滚动位置,避免频繁滚动

    // ==================== 定时器相关 ====================
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val hideFastScrollerRunnable: Runnable = Runnable { startHideAnimation() }

    // ==================== 构造函数 ====================
    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init() }

    /**
     * 初始化方法
     * 设置滚动监听,初始化矩形区域
     */
    private fun init() {
        currentFastScrollerRect.set(fastScrollerRect)
        isFastScrollerVisible = false // 默认隐藏

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // 总是更新滚动条位置
                updateFastScrollerPosition()

                // 有滚动时才显示滚动条(确保不在拖动状态时且item数量足够)
                if (abs(dy) > 0 && shouldShowFastScroller() && !isDraggingFastScroller) {
                    showFastScrollerOnScroll()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    SCROLL_STATE_IDLE -> {
                        // 停止滚动后开始隐藏计时
                        startHideFastScrollerTimer()
                    }
                    SCROLL_STATE_DRAGGING -> {
                        // 手动拖动时立即显示滚动条
                        cancelHideFastScrollerTimer()
                        cancelHideAnimation()
                        if (shouldShowFastScroller() && !isFastScrollerVisible) {
                            isFastScrollerVisible = true
                            isHiding = false
                            hideAnimationProgress = 0f
                            updateFastScrollerPosition()
                        }
                    }
                    SCROLL_STATE_SETTLING -> {
                        // 惯性滚动时取消隐藏
                        cancelHideFastScrollerTimer()
                        cancelHideAnimation()
                    }
                }
            }
        })
    }

    /**
     * 判断是否应该显示滚动条
     * 条件:1.功能启用 2.有可滚动内容 3.item数量超过阈值
     */
    private fun shouldShowFastScroller(): Boolean {
        return isScrollbarEnabled &&
                hasScrollableContent() &&
                getItemCount() > itemCountThreshold
    }

    /**
     * 获取当前item数量
     */
    private fun getItemCount(): Int {
        return adapter?.itemCount ?: 0
    }

    /**
     * 滚动时显示滚动条
     */
    private fun showFastScrollerOnScroll() {
        cancelHideFastScrollerTimer()
        cancelHideAnimation()

        if (shouldShowFastScroller() && !isFastScrollerVisible) {
            isFastScrollerVisible = true
            isHiding = false
            hideAnimationProgress = 0f
            updateFastScrollerPosition()
            invalidateFastScrollerArea()
        }
    }

    // ==================== 绘制相关 ====================
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        // 只在需要时绘制滚动条
        if (shouldDrawFastScroller()) {
            val paint = getCurrentPaint()
            canvas.drawRoundRect(
                getCurrentRect(),
                fastScrollerCornerRadius.toFloat(),
                fastScrollerCornerRadius.toFloat(),
                paint
            )
        }
    }

    /**
     * 判断是否需要绘制滚动条
     */
    private fun shouldDrawFastScroller(): Boolean {
        return (isFastScrollerVisible || isHiding) && shouldShowFastScroller()
    }

    /**
     * 获取当前绘制的矩形(考虑动画)
     */
    private fun getCurrentRect(): RectF {
        return if (isHiding) currentFastScrollerRect else fastScrollerRect
    }

    /**
     * 获取当前绘制的画笔(考虑状态)
     */
    private fun getCurrentPaint(): Paint {
        val paint = if (isDraggingFastScroller) fastScrollerDraggingPaint else fastScrollerPaint
        paint.alpha = if (isHiding) ((1 - hideAnimationProgress) * 255).toInt() else 255
        return paint
    }

    // ==================== 触摸事件处理 ====================
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // 如果滚动条功能未启用或item数量不足,直接使用父类处理
        if (!shouldShowFastScroller()) {
            return super.onTouchEvent(e)
        }

        val x = e.x
        val y = e.y

        return when (e.action) {
            MotionEvent.ACTION_DOWN -> handleActionDown(e, x, y)
            MotionEvent.ACTION_MOVE -> handleActionMove(e, x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleActionUpOrCancel(e)
            else -> super.onTouchEvent(e)
        }
    }

    /**
     * 处理按下事件
     */
    private fun handleActionDown(event: MotionEvent, x: Float, y: Float): Boolean {
        isTouchingRecyclerView = true
        return if (isInFastScrollerArea(x, y)) {
            startDragging(y)
            true
        } else {
            showFastScrollerOnTouch()
            super.onTouchEvent(event)
        }
    }

    /**
     * 处理移动事件
     */
    private fun handleActionMove(event: MotionEvent, x: Float, y: Float): Boolean {
        isTouchingRecyclerView = true
        return if (isDraggingFastScroller) {
            handleFastScrollerTouch(y, false)
            true
        } else if (isNearFastScrollerArea(x, y) && hasScrollableContent()) {
            startDragging(y)
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    /**
     * 处理抬起或取消事件
     */
    private fun handleActionUpOrCancel(event: MotionEvent): Boolean {
        isTouchingRecyclerView = false
        if (isDraggingFastScroller) {
            isDraggingFastScroller = false
            lastScrollPosition = -1
            invalidateFastScrollerArea()
            startHideFastScrollerTimer()
            return true
        } else {
            startHideFastScrollerTimer()
            return super.onTouchEvent(event)
        }
    }

    /**
     * 开始拖动滚动条
     */
    private fun startDragging(y: Float) {
        isDraggingFastScroller = true
        cancelHideAnimation()
        handleFastScrollerTouch(y, true)
        invalidateFastScrollerArea()
    }

    // ==================== 滚动条显示控制 ====================
    /**
     * 触摸时显示滚动条
     */
    private fun showFastScrollerOnTouch() {
        cancelHideFastScrollerTimer()
        cancelHideAnimation()

        if (shouldShowFastScroller() && !isFastScrollerVisible) {
            isFastScrollerVisible = true
            isHiding = false
            hideAnimationProgress = 0f
            updateFastScrollerPosition()
            invalidateFastScrollerArea()
        }
    }

    /**
     * 判断是否有可滚动内容
     */
    private fun hasScrollableContent(): Boolean {
        return computeVerticalScrollRange() > computeVerticalScrollExtent()
    }

    // ==================== 区域检测 ====================
    /**
     * 判断是否在滚动条区域内
     */
    private fun isInFastScrollerArea(x: Float, y: Float): Boolean {
        return shouldShowFastScroller() && isCoordinateInExtendedArea(x, y, touchAreaExtension)
    }

    /**
     * 判断是否在滚动条附近区域
     */
    private fun isNearFastScrollerArea(x: Float, y: Float): Boolean {
        return shouldShowFastScroller() && isCoordinateInExtendedArea(x, y, touchAreaExtension * 2)
    }

    /**
     * 通用方法:判断坐标是否在扩展区域内
     * @param extension 扩展像素数
     */
    private fun isCoordinateInExtendedArea(x: Float, y: Float, extension: Int): Boolean {
        val right = (width - fastScrollerMargin).toFloat()
        val left = right - fastScrollerWidth
        val extendedLeft = left - extension
        val extendedRight = right + extension

        val checkRect = getCurrentRect()
        val extendedTop = checkRect.top - extension
        val extendedBottom = checkRect.bottom + extension

        return x in extendedLeft..extendedRight &&
                y >= extendedTop && y <= extendedBottom &&
                (isFastScrollerVisible || isHiding)
    }

    // ==================== 滚动条触摸处理 ====================
    /**
     * 处理滚动条触摸
     * @param y 触摸点Y坐标
     * @param isInitial 是否为初始触摸
     */
    private fun handleFastScrollerTouch(y: Float, isInitial: Boolean) {
        if (!shouldShowFastScroller()) return

        val scrollRange = computeVerticalScrollRange()
        val scrollExtent = computeVerticalScrollExtent()
        val thumbHeight = calculateThumbHeight(scrollRange, scrollExtent)

        // 计算触摸点的有效范围
        val minY = fastScrollerMargin + thumbHeight / 2
        val maxY = height - fastScrollerMargin - thumbHeight / 2
        val touchY = y.coerceIn(minY, maxY)

        // 计算滚动位置
        val scrollPercentage = (touchY - minY) / (maxY - minY)
        val targetScrollY = (scrollPercentage * (scrollRange - scrollExtent)).toInt()

        // 避免频繁滚动
        if (abs(targetScrollY - lastScrollPosition) > 5 || isInitial) {
            directScrollTo(targetScrollY)
            lastScrollPosition = targetScrollY
        }

        // 更新滚动条位置
        updateFastScrollerPositionDuringDrag(y)

        // 确保滚动条显示
        if (!isFastScrollerVisible) {
            isFastScrollerVisible = true
            isHiding = false
            hideAnimationProgress = 0f
        }
    }

    /**
     * 计算滑块高度
     */
    private fun calculateThumbHeight(scrollRange: Int, scrollExtent: Int): Float {
        val availableHeight = height - 2 * fastScrollerMargin
        return (scrollExtent.toFloat() / scrollRange * availableHeight)
            .coerceAtLeast(fastScrollerMinHeight.toFloat())
            .coerceAtMost(fastScrollerMaxHeight.toFloat())
            .coerceAtMost(availableHeight * 0.4f)
    }

    /**
     * 直接滚动到指定位置(无动画)
     */
    private fun directScrollTo(scrollY: Int) {
        val layoutManager = layoutManager
        val adapter = adapter ?: return

        when (layoutManager) {
            is LinearLayoutManager -> {
                val totalItemCount = adapter.itemCount
                if (totalItemCount > 0) {
                    val scrollRange = computeVerticalScrollRange()
                    val scrollExtent = computeVerticalScrollExtent()
                    val maxScrollY = scrollRange - scrollExtent

                    // 如果目标滚动位置接近底部(超过95%),直接滚动到最底部
                    if (scrollY >= maxScrollY * 0.95f) {
                        val currentOffset = computeVerticalScrollOffset()
                        val diff = maxScrollY - currentOffset
                        if (abs(diff) > 0) scrollBy(0, diff)
                    } else {
                        // 否则使用 scrollToPositionWithOffset 精确控制位置
                        val targetPosition = (scrollY.toFloat() / scrollRange * totalItemCount).toInt()
                        val safePosition = targetPosition.coerceIn(0, totalItemCount - 1)
                        layoutManager.scrollToPositionWithOffset(safePosition, 0)
                    }
                }
            }
            else -> {
                // 回退方法:使用 scrollBy
                val diff = scrollY - computeVerticalScrollOffset()
                if (abs(diff) > 0) scrollBy(0, diff)
            }
        }
    }

    // ==================== 滚动条位置更新 ====================
    /**
     * 拖动过程中更新滚动条位置
     */
    private fun updateFastScrollerPositionDuringDrag(y: Float) {
        if (!shouldShowFastScroller()) return

        val scrollRange = computeVerticalScrollRange()
        val scrollExtent = computeVerticalScrollExtent()
        val thumbHeight = calculateThumbHeight(scrollRange, scrollExtent)

        // 计算新位置
        val minY = fastScrollerMargin
        val maxY = height - fastScrollerMargin - thumbHeight
        val touchY = y.coerceIn(minY + thumbHeight / 2, maxY + thumbHeight / 2)
        val newTop = (touchY - thumbHeight / 2).coerceIn(minY.toFloat(), maxY)

        val right = width - fastScrollerMargin
        val left = right - fastScrollerWidth

        fastScrollerRect.set(left.toFloat(), newTop, right.toFloat(), newTop + thumbHeight)
        currentFastScrollerRect.set(fastScrollerRect)
        invalidateFastScrollerArea()
    }

    /**
     * 常规更新滚动条位置(基于滚动偏移量)
     */
    private fun updateFastScrollerPosition() {
        if (!shouldShowFastScroller()) {
            isFastScrollerVisible = false
            isHiding = false
            hideAnimationProgress = 0f
            invalidateFastScrollerArea()
            return
        }

        val scrollRange = computeVerticalScrollRange()
        val scrollExtent = computeVerticalScrollExtent()
        val scrollOffset = computeVerticalScrollOffset()
        val thumbHeight = calculateThumbHeight(scrollRange, scrollExtent)

        // 计算滑块位置
        val availableHeight = height - 2 * fastScrollerMargin
        val thumbTop = fastScrollerMargin +
                (scrollOffset.toFloat() / (scrollRange - scrollExtent)) *
                (availableHeight - thumbHeight)

        val right = width - fastScrollerMargin
        val left = right - fastScrollerWidth

        fastScrollerRect.set(left.toFloat(), thumbTop, right.toFloat(), thumbTop + thumbHeight)
        currentFastScrollerRect.set(fastScrollerRect)

        if (isFastScrollerVisible) {
            isHiding = false
            hideAnimationProgress = 0f
        }
        invalidateFastScrollerArea()
    }

    /**
     * 重绘滚动条区域(性能优化)
     */
    private fun invalidateFastScrollerArea() {
        val left = width - fastScrollerMargin - fastScrollerWidth - touchAreaExtension
        val right = width
        val checkRect = getCurrentRect()
        val top = checkRect.top.toInt() - touchAreaExtension
        val bottom = checkRect.bottom.toInt() + touchAreaExtension

        postInvalidate(left, top, right, bottom)
    }

    // ==================== 动画控制 ====================
    /**
     * 开始隐藏动画(向右移动淡出)
     */
    private fun startHideAnimation() {
        if (!shouldStartHideAnimation()) return

        isHiding = true
        hideAnimationProgress = 0f
        hideAnimator?.cancel()

        hideAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                hideAnimationProgress = animation.animatedValue as Float
                updateHideAnimationRect()
                invalidateFastScrollerArea()
            }
            addListener(createHideAnimationListener())
            start()
        }
    }

    /**
     * 判断是否应该开始隐藏动画
     */
    private fun shouldStartHideAnimation(): Boolean {
        return isFastScrollerVisible && !isHiding && !isTouchingRecyclerView && shouldShowFastScroller()
    }

    /**
     * 更新隐藏动画中的矩形位置
     */
    private fun updateHideAnimationRect() {
        val originalRight = width - fastScrollerMargin
        val originalLeft = originalRight - fastScrollerWidth
        val moveDistance = fastScrollerWidth * 1.5f

        currentFastScrollerRect.set(
            originalLeft + moveDistance * hideAnimationProgress,
            fastScrollerRect.top,
            originalRight + moveDistance * hideAnimationProgress,
            fastScrollerRect.bottom
        )
    }

    /**
     * 创建隐藏动画监听器
     */
    private fun createHideAnimationListener(): android.animation.AnimatorListenerAdapter {
        return object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isFastScrollerVisible = false
                isHiding = false
                resetRectToOriginal()
                invalidateFastScrollerArea()
            }

            override fun onAnimationCancel(animation: android.animation.Animator) {
                isHiding = false
                hideAnimationProgress = 0f
                resetRectToOriginal()
                invalidateFastScrollerArea()
            }
        }
    }

    /**
     * 重置矩形到原始位置
     */
    private fun resetRectToOriginal() {
        currentFastScrollerRect.set(fastScrollerRect)
    }

    /**
     * 取消隐藏动画
     */
    private fun cancelHideAnimation() {
        hideAnimator?.cancel()
        isHiding = false
        hideAnimationProgress = 0f
        resetRectToOriginal()
    }

    // ==================== 定时器控制 ====================
    private fun startHideFastScrollerTimer() {
        cancelHideFastScrollerTimer()
        if (shouldStartHideTimer()) {
            handler.postDelayed(hideFastScrollerRunnable, 1500)
        }
    }

    private fun cancelHideFastScrollerTimer() {
        handler.removeCallbacks(hideFastScrollerRunnable)
    }

    /**
     * 判断是否应该启动隐藏定时器
     */
    private fun shouldStartHideTimer(): Boolean {
        return !isDraggingFastScroller && isFastScrollerVisible && !isTouchingRecyclerView && shouldShowFastScroller()
    }

    // ==================== 生命周期 ====================
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelHideFastScrollerTimer()
        cancelHideAnimation()
        hideAnimator?.cancel()
    }

    // ==================== 工具方法 ====================
    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
