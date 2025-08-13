package org.fordem.indifi.ui.utils

import android.content.Context
import android.location.LocationManager
import org.fordem.indifi.ui.model.DeviceInfo
import org.json.JSONObject

fun buildJsonForDeviceList(deviceList: List<DeviceInfo>): String {
    val jsonArray = org.json.JSONArray()

    deviceList.forEach { device ->
        val json = JSONObject()
        json.put("deviceId", device.deviceId)
        json.put("name", device.name)
        json.put("ip", device.ip)
        json.put("isGroupOwner", device.isGroupOwner)
        json.put("timestamp", device.timestamp)
        jsonArray.put(json)
    }

    return "DEVICE_LIST:$jsonArray"
}