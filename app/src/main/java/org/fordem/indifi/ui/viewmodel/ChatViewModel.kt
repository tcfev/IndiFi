package org.fordem.indifi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.fordem.indifi.ui.db.ChatMessageDao
import org.fordem.indifi.ui.db.DeviceInfoDao
import org.fordem.indifi.ui.db.PeerPublicKeyDao
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.model.ChatMessage
import org.fordem.indifi.ui.model.PeerPublicKeyEntity
import java.security.PublicKey
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val dao: ChatMessageDao,
    val peerPublicKeyDao: PeerPublicKeyDao
) : ViewModel() {
    val allMessages: Flow<List<ChatMessage>> = dao.getAllMessages()

    fun insertMessage(message: ChatMessage) {
        viewModelScope.launch {
            dao.insertMessage(message)
        }
    }

    // Expose DAO access to ChatActivity
    suspend fun getSavedPublicKey(ip: String): PublicKey? {
        val entity = peerPublicKeyDao.getKeyByIp(ip)
        return entity?.let {
            KeyStoreManager.base64ToPublicKey(it.base64Key)
        }
    }

    fun saveKey(ip: String, base64: String) {
        viewModelScope.launch(Dispatchers.IO) {
            peerPublicKeyDao.insertKey(
                PeerPublicKeyEntity(ip = ip, base64Key = base64)
            )
        }
    }

    suspend fun getKey(ip: String): PeerPublicKeyEntity? {
        return peerPublicKeyDao.getKeyByIp(ip)
    }
}
