package org.fordem.indifi.ui.screen.connection

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import org.fordem.indifi.ui.components.CommonScreenLayout

@Composable
fun NearbyJoinSetupScreen(navController: NavHostController) {
    CommonScreenLayout(title = "Nearby Join Setup") {
        Button(onClick = { navController.navigate("chat") }) {
            Text("Scan & Connect")
        }
    }
}