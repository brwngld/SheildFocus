package com.shieldfocus.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.shieldfocus.android.data.ProtectionStore
import com.shieldfocus.android.model.ProtectionSettings
import com.shieldfocus.android.ui.ShieldFocusApp
import com.shieldfocus.android.ui.theme.ShieldFocusTheme
import com.shieldfocus.android.vpn.ShieldFocusVpnService

class MainActivity : ComponentActivity() {
    private lateinit var protectionStore: ProtectionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        protectionStore = ProtectionStore(applicationContext)

        setContent {
            var settings by remember {
                mutableStateOf(protectionStore.loadSettings())
            }
            var blockedDomains by remember {
                mutableStateOf(protectionStore.loadBlockedDomains().sorted())
            }
            var allowedDomains by remember {
                mutableStateOf(protectionStore.loadAllowedDomains().sorted())
            }

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    startVpnFlow()
                } else {
                    settings = settings.copy(enabled = false)
                    protectionStore.saveSettings(settings)
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    startVpnFlow()
                }
            }

            fun startVpnFlow() {
                val prepareIntent = VpnService.prepare(this@MainActivity)
                if (prepareIntent != null) {
                    vpnPermissionLauncher.launch(prepareIntent)
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return
                    }
                }

                settings = settings.copy(enabled = true)
                protectionStore.saveSettings(settings)
                startVpnService()
            }

            fun stopProtection() {
                settings = settings.copy(enabled = false)
                protectionStore.saveSettings(settings)
                stopVpnService()
            }

            fun updateSettings(nextSettings: ProtectionSettings) {
                settings = nextSettings
                protectionStore.saveSettings(nextSettings)
            }

            fun updateBlockedDomains(nextDomains: Set<String>) {
                blockedDomains = nextDomains.sorted()
                protectionStore.saveBlockedDomains(nextDomains)
            }

            fun updateAllowedDomains(nextDomains: Set<String>) {
                allowedDomains = nextDomains.sorted()
                protectionStore.saveAllowedDomains(nextDomains)
            }

            ShieldFocusTheme {
                ShieldFocusApp(
                    protectionEnabled = settings.enabled,
                    strictMode = settings.strictMode,
                    redirectDelaySeconds = settings.redirectDelaySeconds,
                    blockedDomains = blockedDomains,
                    allowedDomains = allowedDomains,
                    onProtectionToggle = { enabled ->
                        if (enabled) {
                            startVpnFlow()
                        } else {
                            stopProtection()
                        }
                    },
                    onStrictModeToggle = { enabled ->
                        updateSettings(settings.copy(strictMode = enabled))
                    },
                    onRequestVpnSetup = {
                        startVpnFlow()
                    },
                    onAddBlockedDomain = { domain ->
                        updateBlockedDomains(protectionStore.addBlockedDomain(domain))
                    },
                    onRemoveBlockedDomain = { domain ->
                        updateBlockedDomains(protectionStore.removeBlockedDomain(domain))
                    },
                    onAddAllowedDomain = { domain ->
                        updateAllowedDomains(protectionStore.addAllowedDomain(domain))
                    },
                    onRemoveAllowedDomain = { domain ->
                        updateAllowedDomains(protectionStore.removeAllowedDomain(domain))
                    }
                )
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this@MainActivity, ShieldFocusVpnService::class.java).apply {
            action = ShieldFocusVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this@MainActivity, intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this@MainActivity, ShieldFocusVpnService::class.java).apply {
            action = ShieldFocusVpnService.ACTION_STOP
        }
        startService(intent)
    }
}
