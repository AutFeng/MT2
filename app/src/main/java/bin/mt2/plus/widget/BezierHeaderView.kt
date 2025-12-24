package bin.mt2.plus.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import bin.mt2.plus.R

class BezierHeaderView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.toolbar)
        style = Paint.Style.FILL
    }

    private val path = Path()

    init {
        // 启用硬件加速，提升绘制性能，避免动画卡顿
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // 当前下拉高度
    var pullHeight = 0f
        private set

    // 鼓起最大基础高度
    private val maxBulgeHeight = 95f

    // 用于左右移动的最大下拉值（与PullRefreshLayout的maxPullDistance对应）
    private val maxPullHeight = 240f

    // 控制点左右移动范围（从右到左）
    private val leftLimit = 0.90f   // 起始位置（右侧）
    private val rightLimit = 0.10f  // 结束位置（左侧）

    // 中点增强系数
    private val peakBoost = 2.2f

    // 当前控制点横向比例
    private var currentControlXRatio = leftLimit

    // 是否从左到右（true=从左到右，false=从右到左）
    private var isLeftToRight = false

    fun setPullHeight(height: Float) {
        pullHeight = height.coerceAtLeast(0f)
        invalidate()
    }

    // 重置状态
    fun reset() {
        pullHeight = 0f
        currentControlXRatio = if (isLeftToRight) rightLimit else leftLimit
        invalidate()
    }

    // 设置移动方向
    fun setLeftToRight(leftToRight: Boolean) {
        isLeftToRight = leftToRight
        currentControlXRatio = if (leftToRight) rightLimit else leftLimit
    }

    // 是否已经到达触发点
    fun isAtRightEnd(): Boolean {
        val epsilon = 0.01f  // 容差值
        val result = if (isLeftToRight) {
            currentControlXRatio >= (leftLimit - epsilon)
        } else {
            currentControlXRatio <= (rightLimit + epsilon)
        }
        Log.d("BezierHeaderView", "isAtRightEnd: isLeftToRight=$isLeftToRight, currentControlXRatio=$currentControlXRatio, leftLimit=$leftLimit, rightLimit=$rightLimit, result=$result")
        return result
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pullHeight <= 0f) return

        val width = width.toFloat()

        val baseBulgeHeight = pullHeight.coerceAtMost(maxBulgeHeight)

        // 原始进度（线性）
        val rawProgress = (pullHeight / maxPullHeight).coerceAtMost(1f)

        // 应用缓动函数：二次easeIn，让动画开始时更柔和
        // 公式：progress² 使得开始时变化缓慢，后期加速
        val moveProgress = rawProgress * rawProgress

        // 根据方向计算控制点位置
        currentControlXRatio = if (isLeftToRight) {
            // 从左到右：从rightLimit移动到leftLimit
            rightLimit + (leftLimit - rightLimit) * moveProgress
        } else {
            // 从右到左：从leftLimit移动到rightLimit
            leftLimit + (rightLimit - leftLimit) * moveProgress
        }

        val controlX = width * currentControlXRatio

        // 根据moveProgress计算高度系数
        val heightFactor = if (moveProgress <= 0.5f) {
            // 前半段：从起始到中点，高度从1.4增加到peakBoost
            val t = moveProgress / 0.5f
            1.4f + (peakBoost - 1.4f) * t
        } else {
            // 后半段：从中点到结束，高度从peakBoost减少
            val t = (moveProgress - 0.5f) / 0.5f
            peakBoost * (1f - t.coerceAtMost(1f))
        }

        val finalBulgeHeight = baseBulgeHeight * heightFactor

        // 高度为 0，水滴消失
        if (finalBulgeHeight <= 0f) return

        // 使用三次贝塞尔曲线（cubicTo），通过两个控制点实现水滴重心偏移
        // 让控制点的X坐标也跟随水滴位置移动，实现真正的侧重效果

        // 根据当前控制点位置计算进度（用于控制重心）
        val progress = (currentControlXRatio - rightLimit) / (leftLimit - rightLimit)

        // 根据方向调整控制点位置
        val (control1X, control2X) = if (isLeftToRight) {
            // 从左到右：起始时重心在左，结束时重心在右
            width * (0.05f + progress * 0.5f) to width * (0.3f + progress * 0.65f)
        } else {
            // 从右到左：起始时重心在右，结束时重心在左
            width * (0.15f + progress * 0.5f) to width * (0.7f + progress * 0.25f)
        }

        // 根据方向调整控制点高度
        val (control1Y, control2Y) = if (isLeftToRight) {
            // 从左到右：起始时左侧高，右侧低；结束时右侧高，左侧低
            finalBulgeHeight * (1f - progress * 0.4f) to finalBulgeHeight * (0.6f + progress * 0.4f)
        } else {
            // 从右到左：起始时右侧高，左侧低；结束时左侧高，右侧低
            finalBulgeHeight * (1f - progress * 0.4f) to finalBulgeHeight * (0.6f + progress * 0.4f)
        }

        path.reset()
        path.moveTo(0f, 0f)
        path.quadTo(controlX, finalBulgeHeight, width, 0f)
        path.close()

        canvas.drawPath(path, paint)
    }
}
