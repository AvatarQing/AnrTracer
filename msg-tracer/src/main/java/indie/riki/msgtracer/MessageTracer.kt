package indie.riki.msgtracer

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import timber.log.Timber

/**
 * @author rikiqliu@gmail.com
 */
object MessageTracer {
    private const val TAG = "MessageTracer"

    /** 聚合消息最大耗时（单位：毫秒）*/
    private const val MAX_DURATION_CLUSTER = 30L

    /** 滚动消息最大耗时（单位：毫秒）*/
    private const val MAX_DURATION_ROLLING = 300L

    /** 堆栈采样间隔时间（单位：毫秒）*/
    private const val TIMEOUT_CHECK_INTERVAL = MAX_DURATION_ROLLING + 1

    /** 检查消息超时的消息id */
    private const val MSG_WHAT_CHECK_TIMEOUT = 1

    /** 消息历史队列长度 */
    private const val HISTORY_MAX_SIZE = 500

    /** 历史消息 */
    private val histories = ArrayDeque<Msg>()

    private val monitorThread by lazy { HandlerThread("MsgTimeoutMonitor") }
    private val monitorHandler by lazy { Handler(monitorThread.looper, monitorCallback) }
    private var expectedTimeoutTime = 0L

    @Volatile
    private var mainStacktrace = ""

    /**
     * 在[Application.attachBaseContext]中初始化
     */
    fun init(context: Context) {
        LooperMonitor.register(looperListener)
        monitorThread.start()
        AnrWatcher.init(context)
        ActivityThreadHacker.hackSysHandlerCallback()
        ActivityThreadHacker.addListener(systemMessageListener)
    }

    private var startTime = 0L
    private var endTime = 0L
    private var startCpuTime = 0L
    private var startLog = ""
    private var msgCount = 0
    private var sumWallTime = 0L

    private val looperListener = object : LooperMonitor.LooperListener {
        override fun onDispatchStart(x: String) {
            startTime = SystemClock.uptimeMillis()
            startCpuTime = SystemClock.currentThreadTimeMillis()
            startLog = x
            msgCount++

            // 消息超时监控
            val msgTimeoutTime = startTime + TIMEOUT_CHECK_INTERVAL
            if (expectedTimeoutTime == 0L) monitorHandler.sendEmptyMessageAtTime(MSG_WHAT_CHECK_TIMEOUT, msgTimeoutTime)
            expectedTimeoutTime = msgTimeoutTime
        }

        override fun onDispatchEnd(x: String, startNs: Long, endNs: Long) {
            if (flushed) flushed = false
            else recordMsg()
        }
    }

    private var flushed = false

    fun flush() {
        flushed = true
        recordMsg()
    }

    private fun recordMsg() {
        endTime = SystemClock.uptimeMillis()
        val wallTime = endTime - startTime

        // 连续的短消息会聚合为一条消息记录
        if (wallTime < MAX_DURATION_CLUSTER && systemMsg == null) sumWallTime += wallTime

        // 两种情况会触发记录聚合消息：1.短消息数量超出阈值，2.当前消息不是短消息
        if (wallTime < MAX_DURATION_CLUSTER && sumWallTime > MAX_DURATION_ROLLING
            || (wallTime >= MAX_DURATION_CLUSTER || systemMsg != null) && sumWallTime > 0
        ) {
            val count = if (sumWallTime > MAX_DURATION_ROLLING) msgCount else msgCount - 1
            addMsg(Msg.cluster(wallTime = sumWallTime, count = count))
            sumWallTime = 0
            msgCount = 0
        }

        when {
            // 记录系统消息
            systemMsg != null -> {
                systemMsg?.also { msg ->
                    msg.wallTime = wallTime
                    msg.cpuTime = SystemClock.currentThreadTimeMillis() - startCpuTime
                    addMsg(msg)
                }
                systemMsg = null
            }

            // 记录长消息
            wallTime >= MAX_DURATION_CLUSTER -> {
                val cpuTime = SystemClock.currentThreadTimeMillis() - startCpuTime
                addMsg(
                    if (wallTime <= MAX_DURATION_ROLLING) Msg.rolling(wallTime = wallTime, cpuTime = cpuTime, log = startLog)
                    else Msg.fat(wallTime = wallTime, cpuTime = cpuTime, log = startLog, stacktrace = mainStacktrace)
                )
            }
        }

        // 标记消息执行结束
        expectedTimeoutTime = 0L
    }

    private val monitorCallback: Handler.Callback = Handler.Callback { msg ->
        if (msg.what == MSG_WHAT_CHECK_TIMEOUT) {
            if (expectedTimeoutTime == 0L) return@Callback true // IDLE
            if (SystemClock.uptimeMillis() >= expectedTimeoutTime) { // 消息超时
                mainStacktrace = Looper.getMainLooper().thread.stackTrace.text
            } else { // 消息未超时，对齐超时检查时机
                monitorHandler.sendEmptyMessageAtTime(MSG_WHAT_CHECK_TIMEOUT, expectedTimeoutTime)
            }
        }
        true
    }

    private var systemMsg: SystemMsg? = null

    private val systemMessageListener = object : ActivityThreadHacker.SystemMessageListener {
        override fun handleMessage(msg: Message) {
            systemMsg = Msg.system(
                target = msg.target?.toString() ?: "",
                callback = msg.callback?.toString() ?: "",
                what = msg.what
            )
        }
    }

    private fun addMsg(msg: Msg) {
        histories.addFirst(msg)
        // 消息滚动，清除最久远的消息
        if (histories.size > HISTORY_MAX_SIZE) histories.removeLast().also { it.recycle() }
        Timber.tag(TAG).d("[addMsg] $msg")
        msgListeners.forEach { it.onMsgRecorded(msg) }
    }

    fun getHistoryCopy(): List<Msg> = histories.toList()

    private val Array<StackTraceElement>.text: String
        get() {
            val sb = StringBuilder()
            forEach { sb.append(it).append("\n") }
            return sb.toString()
        }

    private val msgListeners = mutableListOf<MsgListener>()

    fun addMsgListener(listener: MsgListener) {
        msgListeners.add(listener)
    }

    fun removeMsgListener(listener: MsgListener) {
        msgListeners.remove(listener)
    }


    interface MsgListener {
        fun onMsgRecorded(msg: Msg)
    }
}
