package org.fordem.indifi.ui.model

import org.fordem.indifi.ui.utils.PeerActionMode

data class PeerDevice(
    val name: String,
    val ip: String,
    val mac: String,
    val mode: PeerActionMode = PeerActionMode.CONNECT
)
