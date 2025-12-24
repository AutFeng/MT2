package bin.mt2.plus.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class BezierCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 控制点参数
    private val controlPoint = PointF()

    // 进度参数
    var progress = 0f // 0-1之间
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateControlPoint()
        }

    // 尺寸参数
    private var width = 0
    private var height = 0
    private var maxControlPointHeight = 0f
    private var minControlPointHeight = 0f
    private var currentControlPointHeight = 0f

    // 画笔
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A90E2")
        style = Paint.Style.FILL
        strokeWidth = 5f
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 10f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
    }

    // 动画
    private var waveAnimator: ValueAnimator? = null

    init {
        controlPoint.set(0f, 0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        width = w
        height = h

        // 计算控制点高度范围
        maxControlPointHeight = height * 0.4f  // 最大高度为视图高度的40%
        minControlPointHeight = height * 0.1f  // 最小高度为视图高度的10%

        // 初始化控制点位置
        updateControlPoint()
    }

    private fun updateControlPoint() {
        if (width == 0) return

        // 横向移动：控制点x坐标从0到width
        controlPoint.x = progress * width

        // 纵向移动：基于进度计算高度
        val normalizedProgress = progress * 2 // 转换为0-2范围

        currentControlPointHeight = if (normalizedProgress <= 1) {
            // 第一阶段：0-1，线性增加
            maxControlPointHeight * normalizedProgress
        } else {
            // 第二阶段：1-2，线性减少
            val secondPhase = normalizedProgress - 1 // 0-1
            val decreasingHeight = maxControlPointHeight * (1 - secondPhase)
            decreasingHeight.coerceAtLeast(minControlPointHeight)
        }

        controlPoint.y = currentControlPointHeight

        // 重绘视图
        invalidate()
    }

    fun startWaveAnimation() {
        waveAnimator?.cancel()

        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
            }
            start()
        }
    }

    fun stopWaveAnimation() {
        waveAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // 绘制背景
        canvas.drawColor(Color.WHITE)

        // 创建贝塞尔曲线路径
        val path = Path().apply {
            // 起点：左上角
            moveTo(0f, 0f)

            // 二次贝塞尔曲线
            quadTo(controlPoint.x, controlPoint.y, width.toFloat(), 0f)

            // 闭合路径形成填充区域
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }

        // 绘制曲线区域
        canvas.drawPath(path, curvePaint)

        // 绘制控制点
        canvas.drawCircle(controlPoint.x, controlPoint.y, 15f, pointPaint)

        // 绘制文字信息
        val progressText = "进度: %.1f%%".format(progress * 100)
        val heightText = "高度: %.0fpx".format(currentControlPointHeight)

        canvas.drawText(progressText, 20f, 50f, textPaint)
        canvas.drawText(heightText, 20f, 90f, textPaint)

        // 绘制中点和峰值标记
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), pointPaint)
        canvas.drawText("峰值位置", width / 2f - 60, 130f, textPaint)
    }
}
