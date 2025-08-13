package org.fordem.indifi.ui.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_info")
data class DeviceInfo(
    @PrimaryKey(autoGenerate = true) val deviceId: Int = 0,  // e.g., GM_1, GO_xT, etc.
    val name: String,
    val wfdIp: String,
    val lcIp: String,
    val androidId: String,
    val groupId: String, // Group ID (same for all devices in same WFD/LC group)
    val isGroupOwner: Boolean, // Is this device the GO?
    val isRelayDevice: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val base64Key: String
)
