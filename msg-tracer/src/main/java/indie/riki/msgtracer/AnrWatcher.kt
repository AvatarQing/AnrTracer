package indie.riki.msgtracer

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.ProcessErrorStateInfo
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.ArrayMap
import androidx.annotation.Keep
import indie.riki.msgtracer.MessageTracer.getPendingMessagesInMessageQueue
import indie.riki.msgtracer.MessageTracer.getPendingMsgList
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * @author rikiqliu@gmail.com
 */
@SuppressLint("StaticFieldLeak")
object AnrWatcher {
    private const val TAG = "AnrWatcher"
    private const val FOREGROUND_MSG_THRESHOLD = -2_000L
    private const val BACKGROUND_MSG_THRESHOLD = -10_000L
    private const val CHECK_ERROR_STATE_INTERVAL = 500L
    private const val ANR_DUMP_MAX_TIME = 20000L
    private const val CHECK_ERROR_STATE_COUNT = ANR_DUMP_MAX_TIME / CHECK_ERROR_STATE_INTERVAL

    init {
        System.loadLibrary("anrwatcher")
    }

    external fun startWatchAnrSignal()
    external fun stopWatchAnrSignal()

    private lateinit var context: Context

    fun init(context: Context) {
        AnrWatcher.context = context
        startWatchAnrSignal()
    }

    @JvmStatic
    @Keep
    fun onANRDumped() {
        Timber.tag(TAG).d("onANRDumped, thread: ${Thread.currentThread()}")
        runBlocking {
            val job = async(Dispatchers.IO + CoroutineName("ANR-Dump")) {
                if (isMainThreadBlocked() || checkErrorStateRepeatedly()) {
                    Timber.tag(TAG).d("发生了ANR")
                    reportANR()
                }
            }
            job.await()
            delay(ANR_DUMP_MAX_TIME)
            job.cancelAndJoin()
        }
    }

    @SuppressLint("PrivateApi")
    private fun reportANR() {
        Timber.tag(TAG).d(">>>>>>>>>>>>>>>>>>>>> 主线程历史消息")
        MessageTracer.flush()
        MessageTracer.getHistoryCopy().forEach { Timber.tag(TAG).d(it.toString()) }
        Timber.tag(TAG).d("<<<<<<<<<<<<<<<<<<<<< 主线程历史消息")

        Timber.tag(TAG).d(">>>>>>>>>>>>>>>>>>>>> 主线程待执行消息")
        getPendingMsgList().forEach { Timber.tag(TAG).d(it.toString()) }
        Timber.tag(TAG).d("<<<<<<<<<<<<<<<<<<<<< 主线程待执行消息")
    }

    private suspend fun checkErrorStateRepeatedly(): Boolean {
        var checkErrorStateCount = 0
        while (checkErrorStateCount < CHECK_ERROR_STATE_COUNT) {
            checkErrorStateCount++
            if (checkErrorState()) return true
            delay(CHECK_ERROR_STATE_INTERVAL)
        }
        return false
    }

    private fun checkErrorState(): Boolean {
        try {
            Timber.tag(TAG).d("[checkErrorState] start")
            val am = context.getSystemService(ActivityManager::class.java)
            val processes = am.processesInErrorState
            if (processes == null) {
                Timber.tag(TAG).d("[checkErrorState] procs == null")
                return false
            }
            for (process in processes) {
                Timber.tag(TAG).d("[checkErrorState] found Error State process name = %s, condition = %d", process.processName, process.condition)
                if (process.uid != Process.myUid() && process.condition == ProcessErrorStateInfo.NOT_RESPONDING) {
                    Timber.tag(TAG).d("maybe received other apps ANR signal")
                    return false
                }
                if (process.pid != Process.myPid()) continue
                if (process.condition != ProcessErrorStateInfo.NOT_RESPONDING) continue
                Timber.tag(TAG).d("error sate longMsg = %s", process.longMsg)
                return true
            }
            return false
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "[checkErrorState] error")
        }
        return false
    }

    @SuppressLint("PrivateApi")
    private fun isMainThreadBlocked(): Boolean {
        try {
            val msg = getPendingMessagesInMessageQueue()
            if (msg == null) Timber.tag(TAG).d("mMessages is null")
            else {
                Timber.tag(TAG).d("ANR Message: $msg")
                if (msg.`when` == 0L) return false
                val time = msg.`when` - SystemClock.uptimeMillis()
                val threshold = if (isForeground()) FOREGROUND_MSG_THRESHOLD else BACKGROUND_MSG_THRESHOLD
                Timber.tag(TAG).d("time: $time, $threshold, isMainThreadBlocked: ${time < threshold}")
                return time < threshold
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "[isMainThreadBlocked] error")
        }
        return false
    }

    @SuppressLint("PrivateApi")
    private fun isForeground(): Boolean {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField = activityThreadClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            val activities = activitiesField.get(activityThread) as ArrayMap<Any, Any>
            if (activities.isEmpty()) return false
            for (activityRecord in activities.values) {
                val activityRecordClass: Class<*> = activityRecord.javaClass
                val pausedField = activityRecordClass.getDeclaredField("paused")
                pausedField.isAccessible = true
                if (!pausedField.getBoolean(activityRecord)) return true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e)
        }
        return false
    }
}