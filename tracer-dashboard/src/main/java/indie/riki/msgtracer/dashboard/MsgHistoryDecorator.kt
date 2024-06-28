package indie.riki.msgtracer.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import indie.riki.msgtracer.dashboard.databinding.MtFloatMsgDashboardBinding

/**
 * @author liuqing@vroadtech.com
 */
class MsgHistoryDecorator(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private lateinit var viewBinding: MtFloatMsgDashboardBinding
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var isShowing = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    init {
        viewBinding = MtFloatMsgDashboardBinding.inflate(LayoutInflater.from(context))
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

        }
        viewBinding.buttonClose.setOnClickListener { hide() }
    }

    fun show() {
        mainHandler.post {
            if (!isShowing) {
                isShowing = true
                windowManager.addView(viewBinding.root, layoutParams)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            if (isShowing) {
                isShowing = false
                windowManager.removeView(viewBinding.root)
            }
        }
    }
}