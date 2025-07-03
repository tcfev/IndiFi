package org.fordem.indifi.ui.utils

import org.fordem.indifi.ui.model.PeerDevice

object Constants {
    lateinit var DummyLCMessage: String
    lateinit var ipLcGo: String
    val connectedGMIPs = mutableSetOf<String>()
    val  displayedPeersList = mutableListOf<PeerDevice>()
    val connectedDevicesList = mutableListOf<PeerDevice>()
    lateinit var lastDeviceInfo: String

    lateinit var deviceConnectionCallback: ((String) -> Unit)
    lateinit var dummyLegacyClientCallback: ((String) -> Unit)
    lateinit var peerPositionCallback: ((PeerDevice, Int) -> Unit)

    const val PORT = 8888
    const val SILENTPORT = 8889
    const val PREF_SYNC_PORT = 8890

    var isGOviaWFD = false
    var isGoViaLegacy = false
}