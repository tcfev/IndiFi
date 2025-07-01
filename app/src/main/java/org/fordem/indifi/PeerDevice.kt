package org.fordem.indifi

data class PeerDevice(
    val name: String,
    val ip: String,
    val mac: String,
    val mode: PeerActionMode = PeerActionMode.CONNECT
)
