package org.fordem.indifi.ui.db

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
    fun getAllDevices(): Flow<List<DeviceInfo>>

    @Query("SELECT * FROM device_info")
    suspend fun getAllDevicesOnce(): List<DeviceInfo>

    @Query("SELECT * FROM device_info WHERE deviceId = :deviceId")
    suspend fun getDeviceById(deviceId: String): DeviceInfo?

    @Query("SELECT * FROM device_info WHERE name = :name AND ip = :ip AND (:currentTime - timestamp) < :window LIMIT 1")
    fun findRecentDevice(
        name: String,
        ip: String,
        currentTime: Long,
        window: Long = 5 * 60 * 1000
    ): LiveData<DeviceInfo?>

    @Query("SELECT * FROM device_info WHERE name = :name")
    suspend fun findByName(name: String): List<DeviceInfo>

    @Query("SELECT * FROM device_info WHERE ip = :ip")
    suspend fun findByIp(ip: String): List<DeviceInfo>

    @Query("SELECT * FROM device_info WHERE ABS(:currentTime - timestamp) < :window")
    suspend fun findRecent(currentTime: Long, window: Long = 5 * 60 * 1000): List<DeviceInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOwnDeviceInfo(info: OwnDeviceInfo)

    @Query("SELECT * FROM own_device_info WHERE id = 1 LIMIT 1")
    fun getOwnInfo(): Flow<OwnDeviceInfo?>

    @Query("SELECT * FROM own_device_info WHERE id = 1 LIMIT 1")
    suspend fun getOwnInfoDirect(): OwnDeviceInfo?

}
