package org.fordem.indifi.ui.db

import androidx.room.Database
import androidx.room.RoomDatabase
import org.fordem.indifi.ui.model.ChatMessage

@Database(
    entities = [DeviceInfo::class, OwnDeviceInfo::class, ChatMessage::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceInfoDao(): DeviceInfoDao
    abstract fun chatMessageDao(): ChatMessageDao
}
