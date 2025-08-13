package org.fordem.indifi.ui.db

import androidx.room.Database
import androidx.room.RoomDatabase
import org.fordem.indifi.ui.dao.ChatMessageDao
import org.fordem.indifi.ui.dao.DeviceInfoDao
import org.fordem.indifi.ui.dao.PeerPublicKeyDao
import org.fordem.indifi.ui.model.ChatMessage
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.model.OwnDeviceInfo
import org.fordem.indifi.ui.model.PeerPublicKeyEntity

@Database(
    entities = [DeviceInfo::class, OwnDeviceInfo::class, ChatMessage::class, PeerPublicKeyEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceInfoDao(): DeviceInfoDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun peerPublicKeyDao(): PeerPublicKeyDao
}
