package org.fordem.indifi.ui.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_info")
data class DeviceInfo(
    @PrimaryKey(autoGenerate = true) val deviceId: Int = 0,  // e.g., GM_1, GO_xT, etc.
    val name: String,
    val ip: String,
    val isGroupOwner: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
