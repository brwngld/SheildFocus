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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.shieldfocus.android.data.ProtectionStore
import com.shieldfocus.android.model.BlockingCategory
import com.shieldfocus.android.model.BlockingSchedule
import com.shieldfocus.android.model.ProtectionSettings
import com.shieldfocus.android.ui.ShieldFocusApp
import com.shieldfocus.android.ui.theme.ShieldFocusTheme
import com.shieldfocus.android.vpn.ShieldFocusVpnService
import com.shieldfocus.android.vpn.VpnConnectionState
import kotlinx.coroutines.flow.collect

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
            var categories by remember {
                mutableStateOf(protectionStore.loadCategories())
            }
            var schedules by remember {
                mutableStateOf(protectionStore.loadSchedules())
            }
            var importedPresetIds by remember {
                mutableStateOf(protectionStore.loadImportedPresetIds())
            }
            var enabledPresetIds by remember {
                mutableStateOf(protectionStore.loadEnabledPresetIds())
            }
            var activePresetCategoryIds by remember {
                mutableStateOf(protectionStore.loadActivePresetCategoryIds())
            }
            var activeAdultSubcategoryIds by remember {
                mutableStateOf(protectionStore.loadActiveAdultSubcategoryIds())
            }
            val bundledCategoryDomainCounts = remember {
                protectionStore.loadBundledCategoryDomainCounts()
            }
            val decisionLogs by produceState(
                initialValue = protectionStore.loadDecisionHistory()
            ) {
                protectionStore.decisionHistoryFlow().collect { value = it }
            }
            val vpnStatus by ShieldFocusVpnService.connectionStatus.collectAsState()
            val dnsHealth by ShieldFocusVpnService.dnsHealth.collectAsState()
            var startVpnFlow: (() -> Unit)? = null
            var restartAfterNetworkSettingsChange by remember { mutableStateOf(false) }

            LaunchedEffect(vpnStatus.state) {
                when (vpnStatus.state) {
                    VpnConnectionState.Connected -> {
                        settings = settings.copy(enabled = true)
                        protectionStore.saveSettings(settings)
                    }
                    VpnConnectionState.Disconnected, VpnConnectionState.Error -> {
                        if (
                            vpnStatus.state == VpnConnectionState.Disconnected &&
                            restartAfterNetworkSettingsChange
                        ) {
                            restartAfterNetworkSettingsChange = false
                            startVpnFlow?.invoke()
                        } else {
                            restartAfterNetworkSettingsChange = false
                            settings = settings.copy(enabled = false)
                            protectionStore.saveSettings(settings)
                        }
                    }
                    else -> Unit
                }
            }

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result -> 
                if (result.resultCode == Activity.RESULT_OK) {
                    startVpnFlow?.invoke()
                } else {
                    settings = settings.copy(enabled = false)
                    protectionStore.saveSettings(settings)
                    ShieldFocusVpnService.reportError("VPN permission was not granted")
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    startVpnFlow?.invoke()
                } else {
                    ShieldFocusVpnService.reportError("Notification permission is required to run protection")
                }
            }

            startVpnFlow = {
                ShieldFocusVpnService.reportConnecting()
                val prepareIntent = VpnService.prepare(this@MainActivity)
                if (prepareIntent != null) {
                    vpnPermissionLauncher.launch(prepareIntent)
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    try {
                        startVpnService()
                    } catch (_: Exception) {
                        ShieldFocusVpnService.reportError("Unable to start protection")
                    }
                }
            }

            fun stopProtection() {
                ShieldFocusVpnService.reportDisconnecting()
                try {
                    stopVpnService()
                } catch (_: Exception) {
                    ShieldFocusVpnService.reportError("Unable to turn off protection")
                }
            }

            fun updateSettings(nextSettings: ProtectionSettings) {
                settings = nextSettings
                protectionStore.saveSettings(nextSettings)
            }

            fun updateNetworkSettings(nextSettings: ProtectionSettings) {
                updateSettings(nextSettings)
                if (vpnStatus.state == VpnConnectionState.Connected) {
                    restartAfterNetworkSettingsChange = true
                    stopProtection()
                }
            }

            fun updateBlockedDomains(nextDomains: Set<String>) {
                blockedDomains = nextDomains.sorted()
                protectionStore.saveBlockedDomains(nextDomains)
            }

            fun updateAllowedDomains(nextDomains: Set<String>) {
                allowedDomains = nextDomains.sorted()
                protectionStore.saveAllowedDomains(nextDomains)
            }

            fun updateCategories(nextCategories: List<BlockingCategory>) {
                categories = nextCategories
                protectionStore.saveCategories(nextCategories)
            }

            fun updateSchedules(nextSchedules: List<BlockingSchedule>) {
                schedules = nextSchedules
                protectionStore.saveSchedules(nextSchedules)
            }

            fun refreshStateFromStore() {
                settings = protectionStore.loadSettings()
                blockedDomains = protectionStore.loadBlockedDomains().sorted()
                allowedDomains = protectionStore.loadAllowedDomains().sorted()
                categories = protectionStore.loadCategories()
                schedules = protectionStore.loadSchedules()
                importedPresetIds = protectionStore.loadImportedPresetIds()
                enabledPresetIds = protectionStore.loadEnabledPresetIds()
                activePresetCategoryIds = protectionStore.loadActivePresetCategoryIds()
                activeAdultSubcategoryIds = protectionStore.loadActiveAdultSubcategoryIds()
            }

            ShieldFocusTheme {
                ShieldFocusApp(
                    protectionEnabled = vpnStatus.state == VpnConnectionState.Connected,
                    vpnConnectionState = vpnStatus.state,
                    vpnConnectedAtMillis = vpnStatus.connectedAtMillis,
                    vpnErrorMessage = vpnStatus.errorMessage,
                    strictMode = settings.strictMode,
                    ipv4DnsEnabled = settings.ipv4DnsEnabled,
                    ipv6DnsEnabled = settings.ipv6DnsEnabled,
                    dnsTimeoutMillis = settings.dnsTimeoutMillis,
                    safeSearchEnabled = settings.safeSearchEnabled,
                    ipv4DnsHealth = dnsHealth.ipv4,
                    ipv6DnsHealth = dnsHealth.ipv6,
                    redirectDelaySeconds = settings.redirectDelaySeconds,
                    autoStartOnBoot = settings.autoStartOnBoot,
                    restartAfterInterruption = settings.restartAfterInterruption,
                    loggingEnabled = settings.loggingEnabled,
                    activityRetentionDays = settings.activityRetentionDays,
                    hideSensitiveDomains = settings.hideSensitiveDomains,
                    blockedDomains = blockedDomains,
                    allowedDomains = allowedDomains,
                    categories = categories,
                    schedules = schedules,
                    decisionLogs = decisionLogs,
                    importedPresetIds = importedPresetIds,
                    enabledPresetIds = enabledPresetIds,
                    activePresetCategoryIds = activePresetCategoryIds,
                    activeAdultSubcategoryIds = activeAdultSubcategoryIds,
                    bundledCategoryDomainCounts = bundledCategoryDomainCounts,
                    onProtectionToggle = { enabled ->
                        if (enabled) {
                            startVpnFlow?.invoke()
                        } else {
                            stopProtection()
                        }
                    },
                    onStrictModeToggle = { enabled ->
                        updateSettings(settings.copy(strictMode = enabled))
                    },
                    onIpv4DnsToggle = { enabled ->
                        if (enabled || settings.ipv6DnsEnabled) {
                            updateNetworkSettings(settings.copy(ipv4DnsEnabled = enabled))
                        }
                    },
                    onIpv6DnsToggle = { enabled ->
                        if (enabled || settings.ipv4DnsEnabled) {
                            updateNetworkSettings(settings.copy(ipv6DnsEnabled = enabled))
                        }
                    },
                    onDnsTimeoutMillisChange = { timeoutMillis ->
                        updateSettings(settings.copy(dnsTimeoutMillis = timeoutMillis.coerceIn(1_000, 10_000)))
                    },
                    onSafeSearchToggle = { enabled ->
                        updateSettings(settings.copy(safeSearchEnabled = enabled))
                    },
                    onAutoStartToggle = { enabled ->
                        updateSettings(settings.copy(autoStartOnBoot = enabled))
                    },
                    onRestartAfterInterruptionToggle = { enabled ->
                        updateSettings(settings.copy(restartAfterInterruption = enabled))
                    },
                    onLoggingToggle = { enabled ->
                        updateSettings(settings.copy(loggingEnabled = enabled))
                    },
                    onActivityRetentionDaysChange = { days ->
                        updateSettings(settings.copy(activityRetentionDays = days.coerceIn(1, 90)))
                    },
                    onHideSensitiveDomainsToggle = { enabled ->
                        updateSettings(settings.copy(hideSensitiveDomains = enabled))
                    },
                    onOpenVpnSettings = {
                        startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    },
                    onRequestVpnSetup = {
                        startVpnFlow?.invoke()
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
                    },
                    onAddCategory = { name ->
                        updateCategories(protectionStore.addCategory(name))
                    },
                    onRemoveCategory = { categoryId ->
                        updateCategories(protectionStore.removeCategory(categoryId))
                    },
                    onAddDomainToCategory = { categoryId, domain ->
                        updateCategories(protectionStore.addDomainToCategory(categoryId, domain))
                    },
                    onRemoveDomainFromCategory = { categoryId, domain ->
                        updateCategories(protectionStore.removeDomainFromCategory(categoryId, domain))
                    },
                    onAssignScheduleToCategory = { categoryId, scheduleName ->
                        updateCategories(protectionStore.assignScheduleToCategory(categoryId, scheduleName))
                    },
                    onAddSchedule = { name, activeDays, startMinute, endMinute ->
                        updateSchedules(protectionStore.addSchedule(name, activeDays, startMinute, endMinute))
                    },
                    onImportedPresetIdsChange = { ids ->
                        importedPresetIds = ids
                        protectionStore.saveImportedPresetIds(ids)
                    },
                    onEnabledPresetIdsChange = { ids ->
                        enabledPresetIds = ids
                        protectionStore.saveEnabledPresetIds(ids)
                    },
                    onActivePresetCategoryIdsChange = { ids ->
                        activePresetCategoryIds = ids
                        protectionStore.saveActivePresetCategoryIds(ids)
                    },
                    onActiveAdultSubcategoryIdsChange = { ids ->
                        activeAdultSubcategoryIds = ids
                        protectionStore.saveActiveAdultSubcategoryIds(ids)
                    },
                    onRemoveSchedule = { scheduleId ->
                        updateSchedules(protectionStore.removeSchedule(scheduleId))
                    },
                    onUpdateSchedule = { scheduleId, name, activeDays, startMinuteOfDay, endMinuteOfDay, enabled ->
                        updateSchedules(
                            protectionStore.updateSchedule(
                                scheduleId = scheduleId,
                                name = name,
                                activeDays = activeDays,
                                startMinuteOfDay = startMinuteOfDay,
                                endMinuteOfDay = endMinuteOfDay,
                                enabled = enabled
                            )
                        )
                    },
                    onExportBackup = {
                        protectionStore.exportBackup()
                    },
                    onImportBackup = { payload ->
                        val imported = protectionStore.importBackup(payload)
                        if (imported) {
                            refreshStateFromStore()
                        }
                        imported
                    },
                    onClearDecisionLogs = {
                        protectionStore.clearDecisionHistory()
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
