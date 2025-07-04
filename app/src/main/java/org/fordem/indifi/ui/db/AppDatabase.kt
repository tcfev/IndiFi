package org.fordem.indifi.ui.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DeviceInfo::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceInfoDao(): DeviceInfoDao
}
