package org.fordem.indifi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.fordem.indifi.ui.screen.chat.ChatScreen
import org.fordem.indifi.ui.screen.connection.ConnectionMethodScreen
import org.fordem.indifi.ui.screen.connection.HotspotHostSetupScreen
import org.fordem.indifi.ui.screen.connection.HotspotJoinSetupScreen
import org.fordem.indifi.ui.screen.connection.NearbyDiscoveryScreen

@Preview(showBackground = true)
@Composable
fun AppNavigation() {
    val  navController = rememberNavController()
    NavHost(navController = navController, startDestination = "connection_method") {
        composable("connection_method") { ConnectionMethodScreen(navController) }

        composable(
            route = "nearby_device_discovery/{deviceName}",
            arguments = listOf(navArgument("deviceName") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceName = backStackEntry.arguments?.getString("deviceName") ?: "Unknown"

            // Use sample list or dynamic list from ViewModel
            val discoveredDevices = listOf("Device A", "Device B", "Device C")

            NearbyDiscoveryScreen(
                navController = navController,
                title = deviceName,
                discoveredDevices = discoveredDevices,
                onStartDiscovery = { /* TODO: trigger discovery logic */ },
                onDeviceClick = { selectedDevice ->
                    // You can navigate to chat screen or device detail here
                    navController.navigate("chat")
                }
            )
        }

//        composable("nearby_device_discovery") { NearbyDiscoveryScreen(navController) }
//        composable("nearby_join_setup") { NearbyJoinSetupScreen(navController) }

        composable("hotspot_host_setup") { HotspotHostSetupScreen(navController) }
        composable("hotspot_join_setup") { HotspotJoinSetupScreen(navController) }

        composable("chat") { ChatScreen() }
    }
}