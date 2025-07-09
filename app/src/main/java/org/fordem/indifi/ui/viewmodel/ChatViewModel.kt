package org.fordem.indifi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.fordem.indifi.ui.db.ChatMessageDao
import org.fordem.indifi.ui.db.DeviceInfoDao
import org.fordem.indifi.ui.model.ChatMessage
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val dao: ChatMessageDao
) : ViewModel() {
    val allMessages: Flow<List<ChatMessage>> = dao.getAllMessages()

    fun insertMessage(message: ChatMessage) {
        viewModelScope.launch {
            dao.insertMessage(message)
        }
    }
}
