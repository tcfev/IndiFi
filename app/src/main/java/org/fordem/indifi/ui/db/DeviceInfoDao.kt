package org.fordem.indifi.ui.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DeviceInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(deviceInfo: DeviceInfo)

    @Update
    suspend fun updateDevice(deviceInfo: DeviceInfo)

    @Delete
    suspend fun deleteDevice(deviceInfo: DeviceInfo)

    @Query("DELETE FROM device_info WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query("SELECT * FROM device_info ORDER BY timestamp DESC")
    fun getAllDevices(): LiveData<List<DeviceInfo>>

    @Query("SELECT * FROM device_info WHERE deviceId = :deviceId")
    suspend fun getDeviceById(deviceId: String): DeviceInfo?

    @Query("SELECT * FROM device_info WHERE name = :name AND ip = :ip AND (:currentTime - timestamp) < :window LIMIT 1")
    fun findRecentDevice(
        name: String,
        ip: String,
        currentTime: Long,
        window: Long = 5 * 60 * 1000
    ): LiveData<DeviceInfo?>

}
