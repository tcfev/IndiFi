//package org.fordem.indifi.ui.screen.connection
//
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.ColumnScope
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.Button
//import androidx.compose.material3.Card
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.dp
//import androidx.navigation.NavHostController
//import androidx.navigation.compose.rememberNavController
//import org.fordem.indifi.WifiDirectViewModel
//
//@Composable
//fun NearbyDiscoveryScreen(
//    navController: NavHostController,
//    viewModel: WifiDirectViewModel = hiltViewModel(),
//    onDeviceClick: (String) -> Unit
//) {
//    val activity = LocalContext.current as ComponentActivity
//    val devices by remember { derivedStateOf { viewModel.discoveredDevices } }
//
//    DisposableEffect(Unit) {
//        viewModel.registerReceiver(activity)
//        onDispose { viewModel.unregisterReceiver(activity) }
//    }
//
//    ScreenLayout(title = "Nearby Devices") {
//        Button(
//            onClick = { viewModel.startDiscovery() },
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            Text("Start Discovery")
//        }
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        LazyColumn(modifier = Modifier.fillMaxSize()) {
//            items(devices) { deviceName ->
//                Card(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(vertical = 4.dp)
//                        .clickable { onDeviceClick(deviceName) }
//                ) {
//                    Text(
//                        text = deviceName,
//                        modifier = Modifier.padding(16.dp),
//                        style = MaterialTheme.typography.bodyLarge
//                    )
//                }
//            }
//        }
//    }
//}
//
//
//@Composable
//fun ScreenLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
//    Surface(modifier = Modifier.fillMaxSize()) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(24.dp),
//            verticalArrangement = Arrangement.Top,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text(
//                text = title,
//                style = MaterialTheme.typography.titleLarge
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//            content()
//        }
//    }
//}
//
//@Preview(showBackground = true)
//@Composable
//fun NearbyDiscoveryScreenPreview() {
//    val navController = rememberNavController()
//    val discoveredDevices = listOf("Device A", "Device B", "Device C")
//
//    NearbyDiscoveryScreen(navController = navController,
//        title = "deviceName",
//        discoveredDevices = discoveredDevices,
//        onStartDiscovery = { /* TODO: trigger discovery logic */ },
//        onDeviceClick = {
//            // You can navigate to chat screen or device detail here
//            navController.navigate("chat")
//        }
//    )
//}
