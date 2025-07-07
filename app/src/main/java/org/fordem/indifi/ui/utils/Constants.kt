package org.fordem.indifi.ui.utils

import android.net.wifi.p2p.WifiP2pDevice
import org.fordem.indifi.ui.db.DeviceInfo
import org.fordem.indifi.ui.model.PeerDevice

object Constants {

    lateinit var DummyLCMessage: String
    var ipLcGo: String? = null
    val connectedGMIPs = mutableSetOf<String>()
    val displayedPeersList = mutableListOf<PeerDevice>()
    val connectedDevicesList = mutableListOf<PeerDevice>()
    lateinit var lastDeviceInfo: String
    lateinit var myMembersList: MutableCollection<WifiP2pDevice>


    lateinit var deviceConnectionCallback: ((String) -> Unit)
    lateinit var dummyLegacyClientCallback: ((String) -> Unit)
    lateinit var peerPositionCallback: ((PeerDevice, Int) -> Unit)
    lateinit var openChatCallback: ((DeviceInfo) -> Unit)

    const val PORT = 8888
    const val SILENTPORT = 8889
    const val PREF_SYNC_PORT = 8890

    var isGOViaWFD = false
    var isGoViaLegacy = false
}