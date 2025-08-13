package org.fordem.indifi.ui.viewmodel

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

    val ownDeviceInfo: Flow<OwnDeviceInfo?> = dao.getOwnInfo()

    val allDevices: Flow<List<DeviceInfo>> = dao.getAllDevices().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getOwnInfoDirect(): OwnDeviceInfo? {
        return dao.getOwnInfoDirect()
    }

    fun insertOwnDevice(onwDeviceInfo: OwnDeviceInfo) = viewModelScope.launch {
        dao.insertOwnDeviceInfo(onwDeviceInfo)
    }

    fun insert(deviceInfo: DeviceInfo) = viewModelScope.launch {
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

    fun getById(deviceId: String, callback: (DeviceInfo?) -> Unit) {
        viewModelScope.launch {
            callback(dao.getDeviceById(deviceId))
        }
    }

    fun findRecentDevice(name: String, ip: String, currentTime: Long): LiveData<DeviceInfo?> {
        return dao.findRecentDevice(name, ip, currentTime)
    }

    fun isDuplicateDevice(name: String, androidId: String): Boolean {
        val byName = dao.findByName(name)
//        val byAndroidId = dao.findByAndroidId(androidId)

        return byName.any { it.androidId == androidId }
    }


//    suspend fun isDuplicateDevice(name: String, ip: String, timestamp: Long): Boolean {
//        val byName = dao.findByName(name)
//        val byIp = dao.findByIp(ip)
//        val recent = dao.findRecent(timestamp)
//
//        return byName.any { d ->
//            byIp.any { it.deviceId == d.deviceId } &&
//                    recent.any { it.deviceId == d.deviceId }
//        }
//    }

    suspend fun insertOrIgnore(device: DeviceInfo) {
        val exists = dao.exists(device.name, device.wfdIp, device.timestamp, device.androidId) > 0
        if (!exists) {
            dao.insertDevice(device)
        }
    }

    fun updateLcIpByNameAndRole(newLcIp: String, isGroupOwner: Boolean, isRelayDevice: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateLcIpByNameAndRole(newLcIp, isGroupOwner, isRelayDevice)
        }
    }

    suspend fun updateLcIpAndRelayByAndroidId(androidId: String, newLcIp: String, isRelayDevice: Boolean) {
        dao.updateLcIpByAndroidId(androidId, newLcIp, isRelayDevice)
    }

}
