package indie.riki.msgtracer.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import indie.riki.msgtracer.MessageTracer
import indie.riki.msgtracer.Msg
import indie.riki.msgtracer.dashboard.databinding.MtFloatMsgDashboardBinding

/**
 * @author rikiqliu@gmail.com
 */
class MsgHistoryDecorator(context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val viewBinding: MtFloatMsgDashboardBinding = MtFloatMsgDashboardBinding.inflate(LayoutInflater.from(context))
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var isShowing = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    init {
        initLayoutParams()
        initDrag()
        initClickListeners()
    }

    private fun initLayoutParams() {
        layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            gravity = Gravity.START or Gravity.TOP
            format = PixelFormat.TRANSPARENT

            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            width = metrics.widthPixels / 3 * 2
            height = metrics.heightPixels / 2
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initDrag() {
        var downX = 0f
        var downY = 0f
        var paramX = 0
        var paramY = 0
        viewBinding.root.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    paramX = layoutParams.x
                    paramY = layoutParams.y
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = (paramX + event.rawX - downX).toInt()
                    layoutParams.y = (paramY + event.rawY - downY).toInt()
                    windowManager.updateViewLayout(v, layoutParams)
                }
            }
            true
        }
    }

    private fun initClickListeners() {
        viewBinding.buttonDetail.setOnClickListener {
            MessageTracer.flush()
            MsgSnapshotActivity.run {
                historicalMsgList.clear()
                pendingMsgList.clear()
                historicalMsgList.addAll(MessageTracer.getHistoryCopy())
                pendingMsgList.addAll(MessageTracer.getPendingMsgList())
            }
            it.context.startActivity(
                Intent(it.context, MsgSnapshotActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            hide()
        }
        viewBinding.buttonClose.setOnClickListener { hide() }
    }

    fun show() {
        mainHandler.post {
            if (!isShowing) {
                isShowing = true
                windowManager.addView(viewBinding.root, layoutParams)
                MessageTracer.addMsgListener(msgListener)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            if (isShowing) {
                isShowing = false
                MessageTracer.removeMsgListener(msgListener)
                windowManager.removeView(viewBinding.root)
            }
        }
    }

    private val msgListener = object : MessageTracer.MsgListener {
        override fun onMsgRecorded(msg: Msg) {

        }
    }
}