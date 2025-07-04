package org.fordem.indifi.ui.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

    fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (keyStore.containsAlias("ECDH_KEY")) {
            val entry = keyStore.getEntry("ECDH_KEY", null)
            if (entry is KeyStore.PrivateKeyEntry) {
                val publicKey = entry.certificate.publicKey
                val privateKey = entry.privateKey
                return KeyPair(publicKey, privateKey)
            } else {
                throw IllegalStateException("Entry for alias ECDH_KEY is not a PrivateKeyEntry")
            }
        }

        val keyPairGenerator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
        val parameterSpec = KeyGenParameterSpec.Builder(
            "ECDH_KEY",
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY // Use sign/verify for EC keys
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    fun getOrCreateSharedAESKey(context: Context, peerIp: String, peerPublicKey: PublicKey): SecretKey {
        val ownKeyPair = getOrCreateKeyPair()
        val keyAgreement = KeyAgreement.getInstance("ECDH").apply {
            init(ownKeyPair.private)
            doPhase(peerPublicKey, true)
        }

        val sharedSecret = keyAgreement.generateSecret()
        val derivedKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)

        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    fun getPublicKey(): PublicKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            getOrCreateKeyPair() // Automatically generate key pair
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Entry for alias $KEY_ALIAS is not a PrivateKeyEntry")

        return entry.certificate.publicKey
    }


    // Store and retrieve peer public keys (from server, QR, or exchange message)
    private val peerPublicKeyMap = mutableMapOf<String, PublicKey>()

    fun addPeerPublicKey(ip: String, publicKey: PublicKey) {
        peerPublicKeyMap[ip] = publicKey
    }

    fun getPeerPublicKey(ip: String): PublicKey? = peerPublicKeyMap[ip]

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


    fun getPrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey
    }
}
