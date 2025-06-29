//package org.fordem.indifi.ui.screen.connection
//
//import android.Manifest
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.location.LocationManager
//import android.os.Build
//import android.provider.Settings
//import android.widget.Toast
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.dp
//import androidx.core.content.ContextCompat
//import androidx.navigation.NavHostController
//import androidx.navigation.compose.rememberNavController
//
//@Composable
//fun ConnectionMethodScreen(navController: NavHostController) {
//    Surface(modifier = Modifier.fillMaxSize()) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(24.dp),
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text("Choose Connection Method", style = MaterialTheme.typography.titleLarge)
//
//            Spacer(modifier = Modifier.height(24.dp))
//            Text("Nearby Mode")
//            Spacer(modifier = Modifier.height(8.dp))
//            DiscoveryButton(navController)
//
//            Spacer(modifier = Modifier.height(32.dp))
//            Text("Hotspot Mode")
//            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
//                Button(onClick = { navController.navigate("hotspot_host_setup") }) {
//                    Text("Host")
//                }
//                Button(onClick = { navController.navigate("hotspot_join_setup") }) {
//                    Text("Join")
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun DiscoveryButton(navController: NavHostController) {
//    val context = LocalContext.current
//
//    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//        arrayOf(
//            Manifest.permission.ACCESS_COARSE_LOCATION,
//            Manifest.permission.ACCESS_FINE_LOCATION,
//            Manifest.permission.NEARBY_WIFI_DEVICES
//        )
//    } else {
//        arrayOf(
//            Manifest.permission.ACCESS_COARSE_LOCATION,
//            Manifest.permission.ACCESS_FINE_LOCATION,
//        )
//    }
//
//    val launcher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.RequestMultiplePermissions()
//    ) { permissions ->
//        val allGranted = permissions.values.all { it }
//        if (allGranted) {
//            proceedIfLocationEnabled(context, navController)
//        } else {
//            Toast.makeText(context, "Please grant all permissions to proceed", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
//        Button(onClick = {
//            if (requiredPermissions.any {
//                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
//                }
//            ) {
//                launcher.launch(requiredPermissions)
//            } else {
//                proceedIfLocationEnabled(context, navController)
//            }
//        }) {
//            Text("Discover Nearby Devices")
//        }
//    }
//}
//
//private fun proceedIfLocationEnabled(context: Context, navController: NavHostController) {
//    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
//    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
//        Toast.makeText(context, "Please enable Location Services", Toast.LENGTH_LONG).show()
//        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
//    } else {
//        navController.navigate("nearby_device_discovery/MyDeviceName")
//    }
//}
//
//@Preview(showBackground = true)
//@Composable
//fun DiscoveryButtonPreview() {
//    val navController = rememberNavController()
//    DiscoveryButton(navController)
//}
