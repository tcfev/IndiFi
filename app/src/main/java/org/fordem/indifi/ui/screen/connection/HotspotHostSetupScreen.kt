package org.fordem.indifi.ui.screen.connection

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import org.fordem.indifi.ui.components.CommonScreenLayout

@Composable
fun HotspotHostSetupScreen(navController: NavHostController) {
    CommonScreenLayout(title = "Hotspot Host Setup") {
        Button(onClick = { navController.navigate("chat") }) {
            Text("Start Sharing")
        }
    }
}