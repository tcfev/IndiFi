package org.fordem.indifi.ui.screen.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun ConnectionMethodScreen(navController: NavHostController) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Choose Connection Method", style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.height(24.dp))
            Text("Nearby Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { navController.navigate("nearby_device_discovery/MyDeviceName") }) {
                    Text("Discover Nearby Devices")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Hotspot Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { navController.navigate("hotspot_host_setup") }) {
                    Text("Host")
                }
                Button(onClick = { navController.navigate("hotspot_join_setup") }) {
                    Text("Join")
                }
            }
        }
    }
}
