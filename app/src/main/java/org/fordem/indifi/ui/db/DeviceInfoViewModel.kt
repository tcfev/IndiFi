package org.fordem.indifi.ui.db

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val dao: DeviceInfoDao
) : ViewModel() {

    val ownDeviceInfo: Flow<OwnDeviceInfo?> = dao.getOwnInfo()

    val allDevices: Flow<List<DeviceInfo>> = dao.getAllDevices().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

//    val allMessages = dao.getAllMessages()
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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

    suspend fun isDuplicateDevice(name: String, ip: String, timestamp: Long): Boolean {
        val byName = dao.findByName(name)
        val byIp = dao.findByIp(ip)
        val recent = dao.findRecent(timestamp)

        return byName.any { d ->
            byIp.any { it.deviceId == d.deviceId } &&
                    recent.any { it.deviceId == d.deviceId }
        }
    }

}
