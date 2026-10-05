package net.velalab.veladrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.velalab.veladrive.core.destination.Destination

@Composable
fun HomeScreen(
    sharedDestination: Destination?,
    isResolvingShare: Boolean,
    shareError: String?,
    onGoogleSearch: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Vela Drive", style = MaterialTheme.typography.headlineLarge)
                Text("Search like Google. Drive with clearer guidance.")
                Spacer(Modifier.height(24.dp))

                Button(onClick = onGoogleSearch, modifier = Modifier.fillMaxWidth()) {
                    Text("ค้นหาด้วย Google Maps")
                }

                if (isResolvingShare) {
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("กำลังอ่านปลายทางจาก Google Maps…")
                }

                sharedDestination?.let {
                    Spacer(Modifier.height(24.dp))
                    Text("รับปลายทางแล้ว", style = MaterialTheme.typography.titleMedium)
                    Text("${it.latitude}, ${it.longitude}")
                }

                shareError?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it)
                }
            }
        }
    }
}
