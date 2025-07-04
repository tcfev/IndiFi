package org.fordem.indifi.ui.db

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val dao: DeviceInfoDao
) : ViewModel() {

    val allDevices: LiveData<List<DeviceInfo>> = dao.getAllDevices()

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
}
