package org.fordem.indifi

object Constants {
    val connectedGMIPs = mutableSetOf<String>()
    val  displayedPeersList = mutableListOf<PeerDevice>()
    val connectedDevicesList = mutableListOf<PeerDevice>()

    lateinit var deviceConnectionCallback: ((String) -> Unit)
    lateinit var peerPositionCallback: ((PeerDevice, Int) -> Unit)

}