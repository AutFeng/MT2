package bin.mt2.plus.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 滑块覆盖层View，用于在阴影之上绘制滑块
 * 完全透明的容器，只绘制滑块本身
 */
class ScrollbarOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 滑块矩形
    private val scrollbarRect: RectF = RectF()
    
    // 滑块是否可见
    private var isVisible: Boolean = false
    
    // 滑块是否正在拖动
    private var isDragging: Boolean = false
    
    // 透明度（用于动画）
    private var alpha: Float = 1f
    
    // 滑块配置
    private var scrollbarWidth: Int = dpToPx(8)
    private var scrollbarCornerRadius: Int = dpToPx(0)
    
    // 画笔
    private val normalPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
    }
    
    private val draggingPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#42A5F5")
    }
    
    init {
        // 设置为完全透明的背景
        setBackgroundColor(Color.TRANSPARENT)
        // 不拦截触摸事件
        isClickable = false
        isFocusable = false
    }
    
    /**
     * 更新滑块位置和状态
     */
    fun updateScrollbar(rect: RectF, visible: Boolean, dragging: Boolean, alpha: Float = 1f) {
        this.scrollbarRect.set(rect)
        this.isVisible = visible
        this.isDragging = dragging
        this.alpha = alpha
        invalidate()
    }
    
    /**
     * 隐藏滑块
     */
    fun hide() {
        isVisible = false
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 只在可见时绘制滑块
        if (isVisible && !scrollbarRect.isEmpty) {
            val paint = if (isDragging) draggingPaint else normalPaint
            paint.alpha = (alpha * 255).toInt()
            
            canvas.drawRoundRect(
                scrollbarRect,
                scrollbarCornerRadius.toFloat(),
                scrollbarCornerRadius.toFloat(),
                paint
            )
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
