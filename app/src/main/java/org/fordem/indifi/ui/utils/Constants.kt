package org.fordem.indifi.ui.utils

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.wifi.p2p.WifiP2pDevice
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.model.PeerDevice
import java.security.PublicKey
import javax.crypto.SecretKey

object Constants {

    lateinit var DummyLCMessage: String
    lateinit var ipLcGo: String
    lateinit var androidId: String
    lateinit var lastDeviceInfo: String
    lateinit var gm_ip: String
    var currentBoundInterface: String? = null
    var goMacAddress: String? = null

    val connectedGMIPs = mutableSetOf<String>()
    val displayedPeersList = mutableListOf<PeerDevice>()
    val connectedDevicesList = mutableListOf<PeerDevice>()
    lateinit var myMembersList: MutableCollection<WifiP2pDevice>

    lateinit var deviceConnectionCallback: ((String) -> Unit)
    lateinit var legacyClientCallback: (() -> Unit)
    lateinit var peerPositionCallback: ((PeerDevice, Int) -> Unit)
    lateinit var openChatCallback: ((DeviceInfo) -> Unit)
    lateinit var chatCallback: ((String, String) -> Unit)
    lateinit var connectivityManager: ConnectivityManager
    var networkCallback: NetworkCallback? = null

    val peerPublicKeys: MutableMap<String, PublicKey> = mutableMapOf()
    val peerAESKeys = mutableMapOf<String, SecretKey>()
//    lateinit var aesKey: SecretKey

    const val UNICAST_PORT = 8888
    const val MULTICAST_PORT = 8889
    const val PREF_SYNC_PORT = 8890

    var isGOViaWFD = false
    var isGoViaLegacy = false
    var isChatMessage = false
    var isRegisterReceiver = false
}