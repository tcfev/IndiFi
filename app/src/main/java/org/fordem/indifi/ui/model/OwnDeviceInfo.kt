package org.fordem.indifi.ui.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "own_device_info")
data class OwnDeviceInfo(
    @PrimaryKey val id: Int = 1, // Always only one row
    val name: String,
    val ip: String,
    val isGroupOwner: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
