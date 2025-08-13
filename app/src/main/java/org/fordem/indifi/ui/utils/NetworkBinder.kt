package org.fordem.indifi.ui.utils

import android.content.Context
import android.net.*
import android.util.Log
import org.fordem.indifi.ui.utils.Constants.connectivityManager
import org.fordem.indifi.ui.utils.Constants.currentBoundInterface

object NetworkBinder {

//    private var connectivityManager: ConnectivityManager? = null
    private var activeCallback: ConnectivityManager.NetworkCallback? = null
//    private var currentBoundInterface: String? = null

    fun bindToInterface(context: Context, targetInterfacePrefix: String, onSuccess: (() -> Unit)? = null) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Unbind existing first (optional safety)
        unbind()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        activeCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                val linkProps = connectivityManager?.getLinkProperties(network)
                val ifaceName = linkProps?.interfaceName

                if (ifaceName?.startsWith(targetInterfacePrefix) == true) {
                    val result = connectivityManager?.bindProcessToNetwork(network)
                    currentBoundInterface = ifaceName
                    Log.d("NetworkBinder", "✅ Bound to $ifaceName: $result")
                    onSuccess?.invoke()
                } else {
                    Log.d("NetworkBinder", "⛔ Skipped network, not matching target: $ifaceName")
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (connectivityManager?.getLinkProperties(network)?.interfaceName == currentBoundInterface) {
                    unbind()
                    Log.d("NetworkBinder", "⚠️ Network $currentBoundInterface lost, unbound.")
                }
            }
        }

        connectivityManager?.registerNetworkCallback(request, activeCallback!!)
        Log.d("NetworkBinder", "🔍 Waiting for network with prefix: $targetInterfacePrefix")
    }

    fun unbind() {
        connectivityManager?.bindProcessToNetwork(null)
        if (activeCallback != null) {
            try {
                connectivityManager?.unregisterNetworkCallback(activeCallback!!)
            } catch (_: Exception) { }
        }
        Log.d("NetworkBinder", "🔓 Unbound from $currentBoundInterface")
        activeCallback = null
        currentBoundInterface = null
    }
}
