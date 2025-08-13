package org.fordem.indifi.ui.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderName: String,
    val senderIp: String,
    val message: String,
    val timestamp: Long,
    val isIncoming: Boolean // true = received, false = sent
)
