package indie.riki.msgtracer

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Message
import timber.log.Timber

/**
 * @author rikiqliu@gmail.com
 */
object ActivityThreadHacker {
    private const val TAG = "ActivityThreadHacker"

    @SuppressLint("PrivateApi")
    fun hackSysHandlerCallback() {
        try {
            val cls = Class.forName("android.app.ActivityThread")
            val filed = cls.getDeclaredField("sCurrentActivityThread")
            filed.isAccessible = true
            val activityThread = filed.get(cls)
            val mH = cls.getDeclaredField("mH")
            mH.isAccessible = true
            val handler = mH.get(activityThread)
            val handlerClass = handler.javaClass.superclass
            if (handlerClass != null) {
                val callbackField = handlerClass.getDeclaredField("mCallback")
                callbackField.isAccessible = true
                val originalCallback = callbackField.get(handler) as? Handler.Callback
                callbackField.set(handler, HackCallback(originalCallback))
            }
            Timber.tag(TAG).i("hook system handler completed. SDK_INT: ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "hook system handler failed.")
        }
    }

    private val listeners = mutableListOf<SystemMessageListener>()

    fun addListener(listener: SystemMessageListener) = listeners.add(listener)

    fun removeListener(listener: SystemMessageListener) = listeners.remove(listener)

    private class HackCallback(private val delegate: Handler.Callback?) : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            listeners.forEach { it.handleMessage(msg) }
            return delegate != null && delegate.handleMessage(msg)
        }
    }

    interface SystemMessageListener {
        fun handleMessage(msg: Message)
    }
}