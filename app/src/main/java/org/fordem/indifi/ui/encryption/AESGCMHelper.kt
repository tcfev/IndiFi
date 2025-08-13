package org.fordem.indifi.ui.encryption

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AESGCMHelper {
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12

//    fun generateKey(): SecretKey {
//        val keyGen = KeyGenerator.getInstance("AES")
//        keyGen.init(256)
//        return keyGen.generateKey()
//    }

    fun encrypt(secretKey: SecretKey, plainText: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Pair(encrypted, iv)
    }

    fun decrypt(secretKey: SecretKey, ciphertext: ByteArray, iv: ByteArray): String {
        if (iv.size != 12) throw IllegalArgumentException("IV must be 12 bytes")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

}
