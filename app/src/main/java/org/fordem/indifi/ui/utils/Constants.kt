package org.fordem.indifi.ui.utils

import org.fordem.indifi.ui.model.PeerDevice

object Constants {
    val connectedGMIPs = mutableSetOf<String>()
    val  displayedPeersList = mutableListOf<PeerDevice>()
    val connectedDevicesList = mutableListOf<PeerDevice>()

    lateinit var deviceConnectionCallback: ((String) -> Unit)
    lateinit var peerPositionCallback: ((PeerDevice, Int) -> Unit)

}