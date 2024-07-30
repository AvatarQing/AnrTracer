package indie.riki.msgtracer.demo.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import indie.riki.msgtracer.dashboard.MsgHistoryDecorator
import indie.riki.msgtracer.demo.databinding.ActivityMainBinding

/**
 * @author rikiqliu@gmail.com
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private val floatDecorator by lazy { MsgHistoryDecorator(applicationContext) }
    private var requestOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.showFloatDashboard.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                floatDecorator.show()
            } else {
                requestOverlayPermission = true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
        viewBinding.buttonBlock.setOnClickListener {
            Thread.sleep(20000)
        }
        viewBinding.buttonSendRollingMsg.setOnClickListener {
            it.post { Thread.sleep(100) }
        }
        viewBinding.buttonSendFatMsg.setOnClickListener {
            it.post { Thread.sleep(500) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (requestOverlayPermission) {
            requestOverlayPermission = false
            if (Settings.canDrawOverlays(this)) floatDecorator.show()
        }
    }
}