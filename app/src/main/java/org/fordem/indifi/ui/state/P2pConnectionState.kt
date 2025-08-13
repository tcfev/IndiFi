package org.fordem.indifi.ui.state

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.P2PConnectionStatus

object P2pConnectionState {
    // Connection status LiveData
    private val _connectionStatus = MutableLiveData<P2PConnectionStatus>(P2PConnectionStatus.UNKNOWN)
    val connectionStatus: LiveData<P2PConnectionStatus> = _connectionStatus

    // Current peer list LiveData
    private val _peers = MutableLiveData<List<WifiP2pDevice>>(emptyList())
    val peers: LiveData<List<WifiP2pDevice>> = _peers

    // Current group info LiveData
    private val _group = MutableLiveData<WifiP2pGroup?>(null)
    val group: LiveData<WifiP2pGroup?> = _group

    // Update methods — thread-safe (postValue)
    fun postConnectionStatus(status: P2PConnectionStatus) {
        _connectionStatus.postValue(status)
    }

    fun postPeers(list: List<WifiP2pDevice>) {
        _peers.postValue(list)
    }

    fun postGroup(group: WifiP2pGroup?) {
        _group.postValue(group)
    }

    // Optional: synchronous update on main thread
    fun setConnectionStatus(status: Constants.P2PConnectionStatus) {
//        _connectionStatus.value = status
    }

    fun setPeers(list: List<WifiP2pDevice>) {
        _peers.value = list
    }

    fun setGroup(group: WifiP2pGroup?) {
        _group.value = group
    }
}