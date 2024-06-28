package indie.riki.msgtracer.demo

import android.app.Application
import android.content.Context
import indie.riki.msgtracer.MessageTracer
import timber.log.Timber

/**
 * @author liuqing@vroadtech.com
 */
open class App : Application() {
    companion object {
        lateinit var INSTANCE: App
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Timber.plant(Timber.DebugTree())
        MessageTracer.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }
}