package org.fordem.indifi.ui.utils

import android.net.ConnectivityManager
import android.net.Network

object LegacyNetworkManager {
    var boundNetwork: Network? = null
    var networkCallback: ConnectivityManager.NetworkCallback? = null
}
