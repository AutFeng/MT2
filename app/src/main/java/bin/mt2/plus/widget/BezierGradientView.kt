package bin.mt2.plus.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import bin.mt2.plus.R

class BezierGradientView(context: Context, attrs: AttributeSet) :
    View(context, attrs) {
    private var direction = TOP_TO_BOTTOM
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        context.obtainStyledAttributes(attrs, R.styleable.BezierGradientView).apply {
            direction = getInt(R.styleable.BezierGradientView_direction, TOP_TO_BOTTOM)
            recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.shader = createShader(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun createShader(width: Int, height: Int): Shader {
        val (x0, y0, x1, y1) = when (direction) {
            BOTTOM_TO_TOP -> floatArrayOf(0f, height.toFloat(), 0f, 0f)
            LEFT_TO_RIGHT -> floatArrayOf(0f, 0f, width.toFloat(), 0f)
            RIGHT_TO_LEFT -> floatArrayOf(width.toFloat(), 0f, 0f, 0f)
            else -> floatArrayOf(0f, 0f, 0f, height.toFloat())
        }
        return LinearGradient(x0, y0, x1, y1, COLORS, POSITIONS, Shader.TileMode.CLAMP)
    }

    companion object {
        private const val TOP_TO_BOTTOM = 0
        private const val BOTTOM_TO_TOP = 1
        private const val LEFT_TO_RIGHT = 2
        private const val RIGHT_TO_LEFT = 3
        private const val STEPS = 24

        private val COLORS = IntArray(STEPS) { i ->
            val t = i / (STEPS - 1f)
            val u = 1 - t
            val eased = u * u * u * 0f + 3 * u * u * t * 0.42f + 3 * u * t * t * 0.58f + t * t * t * 1f
            val gray = (255 * eased).toInt()
            Color.rgb(gray, gray, gray)
        }

        private val POSITIONS = FloatArray(STEPS) { it / (STEPS - 1f) }
    }
}