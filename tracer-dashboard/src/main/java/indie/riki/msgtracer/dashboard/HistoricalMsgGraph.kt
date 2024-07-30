package indie.riki.msgtracer.dashboard

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import indie.riki.msgtracer.ClusterMsg
import indie.riki.msgtracer.FatMsg
import indie.riki.msgtracer.MessageTracer
import indie.riki.msgtracer.Msg
import indie.riki.msgtracer.RollingMsg
import indie.riki.msgtracer.SystemMsg
import java.util.ArrayDeque

/**
 * @author rikiqliu@gmail.com
 */
class HistoricalMsgGraph @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val leftEdgeSpacing = 12f.dp
    private val rightEdgeSpacing = 12f.dp
    private var barHeight = 8f.dp
    private val barSpacing = 2f.dp
    private val barScaleSpacing = 20f.dp
    private var maxMsgCount = 0
    private val msgDeque by lazy { ArrayDeque<Msg>() }
    private var maxWallTime = 0L
    private val wallTimeScaleCount = 5
    private val barColorCluster by lazy { context.getColor(R.color.mt_msg_type_cluster) }
    private val barColorRolling by lazy { context.getColor(R.color.mt_msg_type_rolling) }
    private val barColorFat by lazy { context.getColor(R.color.mt_msg_type_fat) }
    private val barColorSystem by lazy { context.getColor(R.color.mt_msg_type_system) }

    private val scaleLinePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80CCCCCC.toInt()
            textSize = 12f.sp
        }
    }
    private val scaleTextPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f.sp
        }
    }
    private val barPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }

    private val msgListener = object : MessageTracer.MsgListener {
        override fun onMsgRecorded(msg: Msg) {
            msgDeque.addFirst(msg)
            while (msgDeque.size > maxMsgCount) msgDeque.removeLast()
            if (msg.wallTime > maxWallTime) maxWallTime = msg.wallTime
            postInvalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxMsgCount = (h / (barHeight + barSpacing)).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MessageTracer.addMsgListener(msgListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        MessageTracer.removeMsgListener(msgListener)
        msgDeque.clear()
    }

    override fun onDraw(canvas: Canvas) {
        val leftEdge = paddingStart + leftEdgeSpacing
        val rightEdge = width - paddingEnd - rightEdgeSpacing
        val topEdge = paddingTop.toFloat()
        val bottomEdge = (height - paddingBottom).toFloat()
        val barsLeft = leftEdge + barScaleSpacing

        // draw wallTime scale
        val textBaselineY = scaleTextPaint.fontMetrics.run { -top }
        val wallTimeScaleTextHeight = scaleTextPaint.fontMetrics.run { bottom - top }
        val wallTimeScaleSpacing = (rightEdge - barsLeft) / wallTimeScaleCount
        val wallTimeDelta = maxWallTime / wallTimeScaleCount
        val barsTop = topEdge + wallTimeScaleTextHeight + 4f.dp
        for (i in 0..wallTimeScaleCount) {
            val x = barsLeft + wallTimeScaleSpacing * i
            val text = "${wallTimeDelta * i}"
            canvas.drawText(text, x - scaleTextPaint.measureText(text) / 2, textBaselineY, scaleTextPaint)
            canvas.drawLine(x, barsTop, x, bottomEdge, scaleLinePaint)
        }

        // draw msg bar
        val indexBaselineDelta = scaleTextPaint.fontMetrics.run { (bottom - top) / 2 - bottom }
        msgDeque.forEachIndexed { i, msg ->
            val barWidth = 1f * msg.wallTime / maxWallTime * (rightEdge - barsLeft)
            val barTop = barsTop + i * (barHeight + barSpacing)
            val barBottom = barTop + barHeight
            barPaint.color = when (msg) {
                is ClusterMsg -> barColorCluster
                is RollingMsg -> barColorRolling
                is FatMsg -> barColorFat
                is SystemMsg -> barColorSystem
                else -> Color.TRANSPARENT
            }
            canvas.drawRect(barsLeft, barTop, barsLeft + barWidth, barBottom, barPaint)
            if ((i + 1) % 10 == 0) {
                canvas.drawText("${i + 1}", leftEdge, barTop + barHeight / 2 + indexBaselineDelta, scaleTextPaint)
            }
        }
    }

    private val Msg.wallTime: Long
        get() = when (this) {
            is ClusterMsg -> wallTime
            is FatMsg -> wallTime
            is RollingMsg -> wallTime
            is SystemMsg -> wallTime
            else -> 0L
        }

    private val Float.sp: Float
        get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics)

    private val Float.dp: Float
        get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics)
}
