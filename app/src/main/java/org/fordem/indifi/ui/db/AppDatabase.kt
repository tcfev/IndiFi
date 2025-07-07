package org.fordem.indifi.ui.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DeviceInfo::class, OwnDeviceInfo::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceInfoDao(): DeviceInfoDao
}
