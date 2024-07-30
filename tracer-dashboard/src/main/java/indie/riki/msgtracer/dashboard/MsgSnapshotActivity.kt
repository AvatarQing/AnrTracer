package indie.riki.msgtracer.dashboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import indie.riki.msgtracer.Msg
import indie.riki.msgtracer.dashboard.databinding.MtMsgSnapshotBinding

/**
 * @author rikiqliu@gmail.com
 */
class MsgSnapshotActivity : AppCompatActivity() {
    companion object {
        val historicalMsgList = mutableListOf<Msg>()
        val pendingMsgList = mutableListOf<Msg>()
    }

    private lateinit var viewBinding: MtMsgSnapshotBinding
    private lateinit var historicalMessages: List<Msg>
    private lateinit var pendingMessages: List<Msg>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = MtMsgSnapshotBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        historicalMessages = historicalMsgList.toList()
        pendingMessages = pendingMsgList.toList()
    }

    override fun onDestroy() {
        super.onDestroy()
        historicalMsgList.clear()
        pendingMsgList.clear()
    }
}