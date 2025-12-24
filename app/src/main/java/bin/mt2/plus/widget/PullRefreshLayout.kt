package bin.mt2.plus.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast

/**
 * 自定义下拉刷新布局
 *
 * 功能特性：
 * - 支持贝塞尔曲线水滴效果的下拉刷新
 * - 支持左右两个方向的刷新动画
 * - 当水滴甩到最右侧时立即触发刷新
 * - 刷新触发后列表从第三个item位置渐入到顶部
 *
 * 使用方式：
 * ```xml
 * <PullRefreshLayout>
 *     <RecyclerView />
 * </PullRefreshLayout>
 * ```
 *
 * @author MT2 Team
 * @since 1.0
 */
class PullRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // ==================== 常量定义 ====================

    companion object {
        /** 下拉阻尼系数：控制下拉的"重量感"，值越小越难拉 */
        private const val PULL_RESISTANCE = 0.3f

        /** 最大下拉距离（约3个item的高度，单位：dp） */
        private const val MAX_PULL_DISTANCE = 240f

        /** 刷新触发后的渐入动画时长（毫秒） */
        private const val REFRESH_ANIMATION_DURATION = 300L
    }

    // ==================== 视图组件 ====================

    /** 贝塞尔曲线水滴效果的头部视图 */
    private val headerView = BezierHeaderView(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }

    /** 内容视图（通常是RecyclerView） */
    private var contentView: View? = null

    // ==================== 状态变量 ====================

    /** 上一次触摸的Y坐标 */
    private var lastY = 0f

    /** 是否正在刷新中 */
    private var isRefreshing = false

    /** 是否已触发刷新（防止重复触发） */
    private var hasTriggeredRefresh = false

    /** 列表侧边名称（用于显示刷新提示） */
    private var sideName: String = ""

    // ==================== 监听器 ====================

    /** 刷新事件监听器 */
    var refreshListener: OnRefreshListener? = null

    /** 开始下拉监听器（只要开始下拉就调用，用于切换当前列表） */
    var onPullStartListener: (() -> Unit)? = null

    // ==================== 初始化 ====================

    init {
        // 添加贝塞尔曲线头部视图作为第一个子View
        addView(headerView)
    }

    /**
     * 布局填充完成后的回调
     * 获取内容视图（RecyclerView）的引用
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        // 获取RecyclerView（第二个子View，第一个是headerView）
        if (childCount > 1) {
            contentView = getChildAt(1)
        }
    }

    // ==================== 触摸事件处理 ====================

    /**
     * 拦截触摸事件
     * 决定是否拦截子View的触摸事件
     *
     * @param ev 触摸事件
     * @return true表示拦截，false表示不拦截
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 正在刷新时拦截所有事件
        if (isRefreshing) return true

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // 记录初始触摸位置
                lastY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - lastY
                // 只有向下拉且RecyclerView在顶部时才拦截
                // dy > 0 表示向下拉
                // !canScrollUp() 表示RecyclerView已经滚动到顶部
                if (dy > 0 && !canScrollUp()) {
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * 检查内容视图是否可以向上滚动
     *
     * @return true表示可以向上滚动（未到顶部），false表示已到顶部
     */
    private fun canScrollUp(): Boolean {
        // canScrollVertically(-1) 检查是否可以向上滚动
        // -1 表示向上方向，1 表示向下方向
        return contentView?.canScrollVertically(-1) ?: false
    }

    /**
     * 处理触摸事件
     * 实现下拉刷新的核心逻辑
     *
     * @param event 触摸事件
     * @return true表示消费事件，false表示不消费
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 正在刷新时消费所有事件但不处理
        if (isRefreshing) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 记录初始触摸位置
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                // 计算下拉距离，应用阻尼系数
                // 阻尼系数使下拉有"重量感"，不会太轻飘
                var dy = (event.y - lastY) * PULL_RESISTANCE

                // 限制最大下拉距离，防止拉得太远
                dy = dy.coerceAtMost(MAX_PULL_DISTANCE)

                if (dy > 0) {
                    // 第一次开始下拉时，通知切换当前列表
                    if (headerView.pullHeight == 0f) {
                        onPullStartListener?.invoke()
                    }

                    // 更新贝塞尔曲线高度
                    headerView.setPullHeight(dy)
                    // 更新内容视图位置
                    contentView?.translationY = dy

                    // ⭐ 关键逻辑：触发刷新的条件
                    // 1. 水滴甩到最右侧（isAtRightEnd()）
                    // 2. 或者拉到最大距离（dy >= MAX_PULL_DISTANCE）
                    if (!hasTriggeredRefresh && (headerView.isAtRightEnd() || dy >= MAX_PULL_DISTANCE)) {
                        triggerRefreshImmediately()
                        return true  // 立即返回，阻止后续事件
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 手指抬起或事件取消时
                if (!hasTriggeredRefresh) {
                    // 如果未触发刷新，则重置到初始状态
                    reset()
                }
            }
        }
        return true
    }

    // ==================== 刷新逻辑 ====================

    /**
     * 立即触发刷新
     * 当水滴甩到最右侧时调用
     *
     * 执行流程：
     * 1. 贝塞尔曲线立即消失
     * 2. 列表从第三个item位置渐入到顶部（300ms动画）
     * 3. 显示"触发刷新"提示
     * 4. 回调刷新监听器
     */
    private fun triggerRefreshImmediately() {
        hasTriggeredRefresh = true
        isRefreshing = true

        // 贝塞尔曲线直接消失
        headerView.setPullHeight(0f)

        // 列表从第三个item位置向上移动到顶部的渐入动画
        // 先将列表位置设置为maxPullDistance（约3个item的高度）
        contentView?.translationY = MAX_PULL_DISTANCE

        // 创建从maxPullDistance到0的动画
        ValueAnimator.ofFloat(MAX_PULL_DISTANCE, 0f).apply {
            duration = REFRESH_ANIMATION_DURATION
            addUpdateListener {
                // 更新列表位置，产生向上滑动的渐入效果
                contentView?.translationY = it.animatedValue as Float
            }
            start()
        }

        // 显示提示和触发刷新回调
        val message = if (sideName.isNotEmpty()) {
            "刷新${sideName}列表…"
        } else {
            "触发刷新…"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        // 触发刷新回调
        refreshListener?.onRefresh()
    }

    /**
     * 完成刷新
     * 外部调用此方法通知刷新完成
     *
     * 使用示例：
     * ```kotlin
     * pullRefreshLayout.setOnRefreshListener {
     *     // 执行刷新操作
     *     loadData()
     *     // 刷新完成后调用
     *     pullRefreshLayout.finishRefresh()
     * }
     * ```
     */
    fun finishRefresh() {
        isRefreshing = false
        hasTriggeredRefresh = false
        headerView.reset()  // 重置贝塞尔曲线状态
    }

    /**
     * 重置到初始状态
     * 当手指抬起但未触发刷新时调用
     */
    private fun reset() {
        animateTo(0f)
    }

    /**
     * 动画移动到指定位置
     *
     * @param end 目标位置
     */
    private fun animateTo(end: Float) {
        // 如果正在刷新，不执行回溯动画
        if (isRefreshing) return

        val start = contentView?.translationY ?: 0f

        // 如果起始位置和目标位置相同，直接返回
        if (start == end) return

        // 根据距离动态调整动画时长（距离越短，时长越短）
        // 增加时长让动画更柔和：最短250ms，最长450ms
        val distance = kotlin.math.abs(start - end)
        val duration = (distance / MAX_PULL_DISTANCE * 450).toLong().coerceAtLeast(250)

        ValueAnimator.ofFloat(start, end).apply {
            this.duration = duration
            // 使用更强的减速效果（因子2.0），让动画更柔和
            interpolator = android.view.animation.DecelerateInterpolator(2.0f)
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                headerView.setPullHeight(value)
                contentView?.translationY = value
            }
            start()
        }
    }

    // ==================== 公共API ====================

    /**
     * 设置水滴移动方向
     *
     * @param leftToRight true表示从左到右，false表示从右到左
     */
    fun setLeftToRight(leftToRight: Boolean) {
        headerView.setLeftToRight(leftToRight)
    }

    /**
     * 设置列表侧边名称
     * 用于在刷新提示中显示对应的信息
     *
     * @param name 侧边名称，如"左侧"、"右侧"
     */
    fun setSideName(name: String) {
        sideName = name
    }

    /**
     * 设置刷新监听器（Kotlin风格的便捷方法）
     *
     * 使用示例：
     * ```kotlin
     * pullRefreshLayout.setOnRefreshListener {
     *     // 刷新逻辑
     *     loadData()
     *     pullRefreshLayout.finishRefresh()
     * }
     * ```
     *
     * @param listener 刷新回调函数
     */
    fun setOnRefreshListener(listener: () -> Unit) {
        refreshListener = object : OnRefreshListener {
            override fun onRefresh() {
                listener()
            }
        }
    }

    /**
     * 刷新监听器接口
     */
    interface OnRefreshListener {
        /**
         * 刷新触发时的回调
         */
        fun onRefresh()
    }
}
