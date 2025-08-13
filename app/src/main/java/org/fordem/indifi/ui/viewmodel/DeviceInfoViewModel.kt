package org.fordem.indifi.ui.viewmodel

import android.util.Log
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fordem.indifi.ui.dao.DeviceInfoDao
import org.fordem.indifi.ui.model.OwnDeviceInfo
import org.fordem.indifi.ui.model.DeviceInfo
import javax.inject.Inject

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val dao: DeviceInfoDao
) : ViewModel() {

    val TAG = "DeviceInfoViewModel"

    val ownDeviceInfo: Flow<OwnDeviceInfo?> = dao.getOwnInfo()

    val allDevices: Flow<List<DeviceInfo>> = dao.getAllDevices().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getOwnInfoDirect(): OwnDeviceInfo? {
        return dao.getOwnInfoDirect()
    }

    fun insertOwnDevice(ownDeviceInfo: OwnDeviceInfo) = viewModelScope.launch {
        Log.d(TAG, "Inserting own device info: ${ownDeviceInfo.name} with IP: ${ownDeviceInfo.ip}")
        Log.d(TAG, "Own device timestamp: ${ownDeviceInfo.timestamp}")
        Log.d(TAG, "Own device isGroupOwner: ${ownDeviceInfo.isGroupOwner}")
        dao.insertOwnDeviceInfo(ownDeviceInfo)
    }

    fun insert(deviceInfo: DeviceInfo) = viewModelScope.launch {
        Log.d(TAG, "Inserting device: ${deviceInfo.name} with IP: ${deviceInfo.ip}")
        Log.d(TAG, "Device timestamp: ${deviceInfo.timestamp}")
        Log.d(TAG, "Device ID: ${deviceInfo.deviceId}")
        Log.d(TAG, "Device isGroupOwner: ${deviceInfo.isGroupOwner}")
        dao.insertDevice(deviceInfo)
    }

    fun update(deviceInfo: DeviceInfo) = viewModelScope.launch {
        dao.updateDevice(deviceInfo)
    }

    fun delete(deviceInfo: DeviceInfo) = viewModelScope.launch {
        dao.deleteDevice(deviceInfo)
    }

    fun deleteById(deviceId: String) = viewModelScope.launch {
        dao.deleteById(deviceId)
    }

    fun deleteDevices() = viewModelScope.launch {
        Log.d(TAG, "Deleting all devices")
        dao.deleteAllDevices()
    }

    fun getById(deviceId: String, callback: (DeviceInfo?) -> Unit) {
        viewModelScope.launch {
            callback(dao.getDeviceById(deviceId))
        }
    }

    fun findRecentDevice(name: String, ip: String, currentTime: Long): LiveData<DeviceInfo?> {
        return dao.findRecentDevice(name, ip, currentTime)
    }

    suspend fun isDuplicateDevice(name: String, ip: String, timestamp: Long): Boolean {
        Log.d("DeviceInfoViewModel", "Checking for duplicate device: name=$name, ip=$ip, timestamp=$timestamp")
        val byName = dao.findByName(name)
        val byIp = dao.findByIp(ip)
        val recent = dao.findRecent(timestamp)

        return byName.any { d ->
            byIp.any { it.deviceId == d.deviceId } &&
                    recent.any { it.deviceId == d.deviceId }
        }
    }
}
