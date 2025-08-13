//package org.fordem.indifi.ui.activity
//
//import android.annotation.SuppressLint
//import android.os.Bundle
//import android.util.Base64
//import android.widget.TextView
//import androidx.activity.enableEdgeToEdge
//import androidx.activity.viewModels
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import org.fordem.indifi.R
//import org.fordem.indifi.ui.db.AppDatabase
//import org.fordem.indifi.ui.encryption.AESGCMHelper
//import org.fordem.indifi.ui.encryption.KeyStoreManager
//import org.fordem.indifi.ui.viewmodel.ChatViewModel
//
//@AndroidEntryPoint
//class TestActivity2 : AppCompatActivity() {
//
//    private lateinit var db: AppDatabase
//    private lateinit var decryptedText: TextView
//    private val chatViewModel: ChatViewModel by viewModels()
//
//    @SuppressLint("MissingInflatedId")
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_test2)
////        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
////            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
////            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
////            insets
////        }
//
//        decryptedText = findViewById(R.id.decryptedText)
//
//        val targetIp = intent.getStringExtra("targetIp") ?: return
//        val cipherBase64 = intent.getStringExtra("cipher") ?: return
//        val ivBase64 = intent.getStringExtra("iv") ?: return
//
//
//        CoroutineScope(Dispatchers.IO).launch {
////            val entity = db.peerPublicKeyDao().getKeyByIp(targetIp)
//            val entity = chatViewModel.getKey(targetIp)
//
//            if (entity != null) {
//                val publicKey = KeyStoreManager.base64ToPublicKey(entity.base64Key)
//                val aesKey = KeyStoreManager.deriveSharedAESKey(publicKey)
//
//                val decrypted = AESGCMHelper.decrypt(
//                    aesKey,
//                    Base64.decode(cipherBase64, Base64.NO_WRAP),
//                    Base64.decode(ivBase64, Base64.NO_WRAP)
//                )
//
//                withContext(Dispatchers.Main) {
//                    decryptedText.text = decrypted
//                }
//            } else {
//                withContext(Dispatchers.Main) {
//                    decryptedText.text = "❌ Public key not found"
//                }
//            }
//        }
//    }
//}