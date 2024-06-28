package indie.riki.msgtracer

import android.annotation.SuppressLint
import android.os.Build
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import android.util.Printer
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * @author rikiqliu@gmail.com
 */
class LooperMonitor(
    private val looper: Looper
) {
    companion object {
        private const val TAG = "LooperMonitor"
        private const val CHECK_TIME = 60 * 1000L
        private val looperMonitors = ConcurrentHashMap<Looper, LooperMonitor>()
        val mainMonitor = of(Looper.getMainLooper())

        fun of(looper: Looper): LooperMonitor {
            return looperMonitors[looper] ?: LooperMonitor(looper).also {
                looperMonitors[looper] = it
            }
        }

        fun register(listener: LooperListener) {
            mainMonitor.addListener(listener)
        }

        fun unregister(listener: LooperListener) {
            mainMonitor.removeListener(listener)
        }
    }

    private var printer: LooperPrinter? = null
    private val listeners = mutableMapOf<LooperListener, LooperListenerWrapper>()

    private var lastCheckPrinterTime: Long = 0
    private val idleHandler = MessageQueue.IdleHandler {
        val now = SystemClock.uptimeMillis()
        if (now - lastCheckPrinterTime >= CHECK_TIME) {
            resetPrinter()
            lastCheckPrinterTime = now
        }
        true
    }

    init {
        resetPrinter()
        addIdleHandler()
    }

    private var isReplaceLoggingError = false

    @SuppressLint("PrivateApi")
    @Synchronized
    private fun resetPrinter() {
        // 获取Looper中原来的Printer
        var originPrinter: Printer? = null
        try {
            if (!isReplaceLoggingError) {
                val mLoggingField = looper.javaClass.getDeclaredField("mLogging")
                mLoggingField.isAccessible = true
                originPrinter = mLoggingField.get(looper) as? Printer
            }
        } catch (e: Exception) {
            isReplaceLoggingError = true
            Timber.tag(TAG).w(e, "[resetPrinter] fail to get Looper.mLogging")
        }
        // 给Looper设置自定义的Printer
        looper.setMessageLogging(LooperPrinter(originPrinter).also { printer = it })
        Timber.tag(TAG).i("[resetPrinter] originPrinter $originPrinter was replaced by $printer in thread ${looper.thread.name}")
    }

    @SuppressLint("PrivateApi")
    @Synchronized
    private fun addIdleHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            looper.queue.addIdleHandler(idleHandler)
        } else {
            try {
                val mQueueField = looper.javaClass.getDeclaredField("mQueue")
                val queue = mQueueField.get(looper) as? MessageQueue
                queue?.addIdleHandler(idleHandler)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "fail to addIdleHandler")
            }
        }
    }

    @SuppressLint("PrivateApi")
    @Synchronized
    private fun removeIdleHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            looper.queue.removeIdleHandler(idleHandler)
        } else {
            try {
                val mQueueField = looper.javaClass.getDeclaredField("mQueue")
                val queue = mQueueField.get(looper) as? MessageQueue
                queue?.removeIdleHandler(idleHandler)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "fail to removeIdleHandler")
            }
        }
    }

    private fun onDispatchStart(x: String) {
        listeners.values.forEach { it.onDispatchStart(x) }
    }

    private fun onDispatchEnd(x: String) {
        listeners.values.forEach { it.onDispatchEnd(x) }
    }

    private inner class LooperPrinter(val origin: Printer?) : Printer {
        private var hasChecked = false
        private var isValid = false
        override fun println(x: String?) {
            origin?.println(x)
            if (origin == this || x == null) return
            if (!hasChecked) {
                isValid = x[0] == '>' || x[0] == '<'
                hasChecked = true
                if (!isValid) {
                    Timber.tag(TAG).e("[println] Printer is not valid! x: $x")
                }
            }
            if (isValid) {
                if (x[0] == '>') onDispatchStart(x)
                else onDispatchEnd(x)
            }
        }
    }

    @Synchronized
    private fun release() {
        val p = printer ?: return
        listeners.clear()
        removeIdleHandler()
        looper.setMessageLogging(p.origin)
        printer = null
    }

    fun addListener(listener: LooperListener) {
        synchronized(listeners) {
            listeners[listener] = LooperListenerWrapper(listener)
        }
    }

    fun removeListener(listener: LooperListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    interface LooperListener {
        fun onDispatchStart(x: String)
        fun onDispatchEnd(x: String, startNs: Long, endNs: Long)
    }

    private class LooperListenerWrapper(
        private val listener: LooperListener
    ) {
        private var isDispatchStarted = false
        private var startNs: Long = 0
        fun onDispatchStart(x: String) {
            if (!isDispatchStarted) {
                isDispatchStarted = true
                startNs = System.nanoTime()
                listener.onDispatchStart(x)
            }
        }

        fun onDispatchEnd(x: String) {
            if (isDispatchStarted) {
                isDispatchStarted = false
                listener.onDispatchEnd(x, startNs, System.nanoTime())
            }
        }
    }
}