package org.fordem.indifi.ui.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.fordem.indifi.ui.db.PeerPublicKeyDao
import org.fordem.indifi.ui.model.PeerPublicKeyEntity
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object KeyStoreManager {
    private const val KEY_ALIAS = "ECDH_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private var cachedKeyPair: KeyPair? = null


    fun getOrCreateKeyPair(): KeyPair {
        if (cachedKeyPair != null) return cachedKeyPair!!

        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        cachedKeyPair = keyPair // store in memory if needed
        return keyPair
    }

//    fun getOrCreateSharedAESKey(context: Context, peerIp: String, peerPublicKey: PublicKey): SecretKey {
//        val ownKeyPair = getOrCreateKeyPair()
//        val keyAgreement = KeyAgreement.getInstance("ECDH").apply {
//            init(ownKeyPair.private)
//            doPhase(peerPublicKey, true)
//        }
//
//        val sharedSecret = keyAgreement.generateSecret()
//        val derivedKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
//
//        return SecretKeySpec(derivedKeyBytes, "AES")
//    }

    // Store and retrieve peer public keys (from server, QR, or exchange message)
    private val peerPublicKeyMap = mutableMapOf<String, PublicKey>()

    fun addPeerPublicKey(ip: String, publicKey: PublicKey) {
        peerPublicKeyMap[ip] = publicKey
    }

    fun getPeerPublicKey(ip: String): PublicKey? {
        Log.d("E2EE", "getPeerPublicKey() called for: $ip")
        Log.d("E2EE", "Available keys: ${peerPublicKeyMap.keys}")
        return peerPublicKeyMap[ip]
    }

    // Helper to convert PublicKey to Base64 string
    fun PublicKey.toBase64(): String {
        return Base64.encodeToString(this.encoded, Base64.NO_WRAP)
    }

    // Helper to convert Base64 string to PublicKey
    fun base64ToPublicKey(base64: String): PublicKey {
        val decoded = Base64.decode(base64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance("EC").generatePublic(keySpec)
    }

    // Derive AES key directly from peer public key
    fun deriveSharedAESKey(peerPublicKey: PublicKey): SecretKey {
        val ownKeyPair = getOrCreateKeyPair()
        val keyAgreement = KeyAgreement.getInstance("ECDH").apply {
            init(ownKeyPair.private)
            doPhase(peerPublicKey, true)
        }
        val sharedSecret = keyAgreement.generateSecret()
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun debugPrintStoredKeys() {
        Log.d("E2EE", "Stored peer keys:")
        peerPublicKeyMap.forEach { (ip, key) ->
            Log.d("E2EE", "IP: $ip → Key: $key")
        }
    }

    fun getOwnPublicKeyBase64(): String {
        val keyPair = getOrCreateKeyPair()
        val publicKey = keyPair.public
        val encoded = publicKey.encoded
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

//    suspend fun savePeerPublicKeyToRoom(ip: String, key: PublicKey, dao: PeerPublicKeyDao) {
//        val base64 = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
//        dao.insertKey(PeerPublicKeyEntity(ip, base64))
//    }

//    suspend fun loadPeerPublicKeyFromRoom(ip: String, dao: PeerPublicKeyDao): PublicKey? {
//        val entity = dao.getKeyByIp(ip) ?: return null
//        return try {
//            val decoded = Base64.decode(entity.base64Key, Base64.NO_WRAP)
//            val spec = X509EncodedKeySpec(decoded)
//            KeyFactory.getInstance("EC").generatePublic(spec)
//        } catch (e: Exception) {
//            null
//        }
//    }

}
