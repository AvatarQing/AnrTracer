package indie.riki.msgtracer

import java.util.LinkedList


sealed class Msg {
    companion object {
        private const val POOL_SIZE = 20
        private val clusterPool by lazy { LinkedList<ClusterMsg>() }
        private val rollingPool by lazy { LinkedList<RollingMsg>() }
        private val fatPool by lazy { LinkedList<FatMsg>() }
        private val systemPool by lazy { LinkedList<SystemMsg>() }

        fun cluster(wallTime: Long = 0, count: Int = 0): ClusterMsg {
            return if (clusterPool.isNotEmpty()) clusterPool.removeFirst()
            else ClusterMsg(wallTime = wallTime, count = count)
        }

        fun rolling(wallTime: Long = 0, cpuTime: Long = 0, log: String = ""): RollingMsg {
            return if (rollingPool.isNotEmpty()) rollingPool.removeFirst()
            else RollingMsg(wallTime = wallTime, cpuTime = cpuTime, log = log)
        }

        fun fat(wallTime: Long = 0, cpuTime: Long = 0, log: String = "", stacktrace: String = ""): FatMsg {
            return if (fatPool.isNotEmpty()) fatPool.removeFirst()
            else FatMsg(wallTime = wallTime, cpuTime = cpuTime, log = log, stacktrace = stacktrace)
        }

        fun system(wallTime: Long = 0, cpuTime: Long = 0, target: String = "", callback: String = "", what: Int? = null): SystemMsg {
            return if (systemPool.isNotEmpty()) systemPool.removeFirst()
            else SystemMsg(wallTime = wallTime, cpuTime = cpuTime, target = target, callback = callback, what = what)
        }
    }

    fun recycle() {
        when (this) {
            is ClusterMsg -> {
                wallTime = 0
                count = 0
                if (clusterPool.size < POOL_SIZE) clusterPool.add(this)
            }

            is RollingMsg -> {
                wallTime = 0
                cpuTime = 0
                log = ""
                if (rollingPool.size < POOL_SIZE) rollingPool.add(this)
            }

            is FatMsg -> {
                wallTime = 0
                cpuTime = 0
                log = ""
                stacktrace = ""
                if (fatPool.size < POOL_SIZE) fatPool.add(this)
            }

            is SystemMsg -> {
                wallTime = 0
                cpuTime = 0
                target = ""
                target = ""
                what = null
                if (systemPool.size < POOL_SIZE) systemPool.add(this)
            }

            else -> {}
        }
    }
}

data class ClusterMsg(
    var wallTime: Long = 0,
    var count: Int = 0,
) : Msg()

data class RollingMsg(
    var wallTime: Long = 0,
    var cpuTime: Long = 0,
    var log: String = "",
) : Msg()


data class FatMsg(
    var wallTime: Long = 0,
    var cpuTime: Long = 0,
    var log: String = "",
    var stacktrace: String = "",
) : Msg()

data class SystemMsg(
    var wallTime: Long = 0,
    var cpuTime: Long = 0,
    var target: String = "",
    var callback: String = "",
    var what: Int? = null,
) : Msg()

data class PendingMsg(
    var waitingTime: Long = 0L,
    var isSystem: Boolean = false,
    var target: String = "",
    var callback: String = "",
    var what: Int? = null,
) : Msg()