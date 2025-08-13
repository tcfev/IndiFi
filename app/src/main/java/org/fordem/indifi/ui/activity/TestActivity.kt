//package org.fordem.indifi.ui.activity
//
//import android.content.Intent
//import android.os.Bundle
//import android.util.Base64
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
//import org.fordem.indifi.ui.model.PeerPublicKeyEntity
//import org.fordem.indifi.ui.viewmodel.ChatViewModel
//import java.security.KeyPairGenerator
//import java.security.spec.ECGenParameterSpec
//
//@AndroidEntryPoint
//class TestActivity : AppCompatActivity() {
//
//    private lateinit var db: AppDatabase
//    private val receiverIp = "192.168.1.5" // simulate target IP
//    private val chatViewModel: ChatViewModel by viewModels()
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_test)
//
////        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
////            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
////            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
////            insets
////        }
//
////        db = AppDatabase.getDatabase(this)
//
//        CoroutineScope(Dispatchers.IO).launch {
//            val receiverKeyPair = KeyPairGenerator.getInstance("EC").apply {
//                initialize(ECGenParameterSpec("secp256r1"))
//            }.generateKeyPair()
//
//            val base64Public = Base64.encodeToString(receiverKeyPair.public.encoded, Base64.NO_WRAP)
//
//            // Save to Room
//            chatViewModel.saveKey(receiverIp, base64Public)
//
//
//            // Derive AES key using local private + receiver public
//            val receiverPublicKey = KeyStoreManager.base64ToPublicKey(base64Public)
//            val sharedAESKey = KeyStoreManager.deriveSharedAESKey(receiverPublicKey)
//
//            // Encrypt "Hello World!"
//            val (cipherBytes, iv) = AESGCMHelper.encrypt(sharedAESKey, "Hello World!")
//            val encryptedBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
//            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
//
//            // Move to second screen
//            val intent = Intent(this@TestActivity, TestActivity2::class.java).apply {
//                putExtra("targetIp", receiverIp)
//                putExtra("cipher", encryptedBase64)
//                putExtra("iv", ivBase64)
//            }
//
//            withContext(Dispatchers.Main) {
//                startActivity(intent)
//            }
//        }
//    }
//}