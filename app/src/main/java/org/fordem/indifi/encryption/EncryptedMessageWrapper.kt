package org.fordem.indifi.encryption

import android.util.Base64
import org.json.JSONObject

object EncryptedMessageWrapper {
    fun createJson(cipherText: ByteArray, iv: ByteArray): String {
        val json = JSONObject()
        json.put("ciphertext", Base64.encodeToString(cipherText, Base64.NO_WRAP))
        json.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        return json.toString()
    }
}
