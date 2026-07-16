package com.shieldfocus.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldFocusApp() {
    var protectionEnabled by remember { mutableStateOf(true) }
    var strictMode by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ShieldFocus", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Device-wide local filtering",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(
                enabled = protectionEnabled,
                onToggle = { protectionEnabled = it }
            )

            MetricsCard(
                title = "Coverage",
                primary = "Browsers",
                secondary = "Chrome, Edge, Opera, Firefox",
                caption = "Blocks domains across the device with a local VPN filter."
            )

            MetricsCard(
                title = "Rules",
                primary = "Shared policy",
                secondary = "Blocklist, allowlist, categories, schedules",
                caption = "The Android app will share the same rule concepts as the desktop extension."
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Protection settings", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict mode", fontWeight = FontWeight.Medium)
                            Text(
                                "Block uncertain domains instead of warning.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = strictMode, onCheckedChange = { strictMode = it })
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { /* Placeholder for VPN permission flow */ },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text("Request VPN setup")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Protection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "On" else "Off",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Text(
                "ShieldFocus Android will filter domain traffic on-device through a local VPN service.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MetricsCard(
    title: String,
    primary: String,
    secondary: String,
    caption: String
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(primary, fontWeight = FontWeight.SemiBold)
            Text(secondary, style = MaterialTheme.typography.bodyMedium)
            Text(caption, style = MaterialTheme.typography.bodySmall)
        }
    }
}
