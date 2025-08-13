//package org.fordem.indifi.ui.activity
//
//import android.os.Bundle
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import org.fordem.indifi.R
//import org.fordem.indifi.databinding.ActivityDummyWfdLcmessageBinding
//import org.fordem.indifi.ui.utils.Constants
//import org.fordem.indifi.ui.utils.MessageRouterHelper
//
//class DummyWFD_LCMessageActivity : AppCompatActivity() {
//    private val binding: ActivityDummyWfdLcmessageBinding by lazy {
//        ActivityDummyWfdLcmessageBinding.inflate(layoutInflater)
//    }
//    private var groupOwnerAddress: String? = null
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(binding.root)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        groupOwnerAddress = intent.getStringExtra("groupOwnerAddress")
//
//        binding.send.setOnClickListener {
//
//            val msg = binding.textView.text.toString()
//            if (msg.isNotBlank()) {
//
//                groupOwnerAddress?.let {
////                        TcpHelper.sendMessageToServer(it, msg)
////                    sendMessageToGo(it, msg)
//                    MessageRouterHelper.indifiService?.sendMessageToServer(
//                        hostAddress = it,
//                        message = /*Constants.DummyLCMessage*/ msg
//                    )
//                } //Send to GO
//            }
//        }
//    }
//}