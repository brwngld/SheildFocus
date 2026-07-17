package com.shieldfocus.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DoNotDisturbAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import com.shieldfocus.android.model.BlockingCategory
import com.shieldfocus.android.model.BlockingSchedule
import com.shieldfocus.android.model.Decision
import com.shieldfocus.android.vpn.VpnConnectionState
import java.text.DateFormat
import java.util.Date
import java.util.Calendar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldFocusApp(
    protectionEnabled: Boolean,
    vpnConnectionState: VpnConnectionState,
    vpnConnectedAtMillis: Long?,
    vpnErrorMessage: String?,
    strictMode: Boolean,
    redirectDelaySeconds: Int,
    autoStartOnBoot: Boolean,
    loggingEnabled: Boolean,
    blockedDomains: List<String>,
    allowedDomains: List<String>,
    categories: List<BlockingCategory>,
    schedules: List<BlockingSchedule>,
    decisionLogs: List<Decision>,
    onProtectionToggle: (Boolean) -> Unit,
    onStrictModeToggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onLoggingToggle: (Boolean) -> Unit,
    onRequestVpnSetup: () -> Unit,
    onAddBlockedDomain: (String) -> Unit,
    onRemoveBlockedDomain: (String) -> Unit,
    onAddAllowedDomain: (String) -> Unit,
    onRemoveAllowedDomain: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onAddDomainToCategory: (String, String) -> Unit,
    onRemoveDomainFromCategory: (String, String) -> Unit,
    onAssignScheduleToCategory: (String, String) -> Unit,
    onAddSchedule: (String) -> Unit,
    onRemoveSchedule: (String) -> Unit,
    onUpdateSchedule: (String, String, Set<Int>, Int, Int, Boolean) -> Unit,
    onExportBackup: () -> String,
    onImportBackup: (String) -> Boolean,
    onClearDecisionLogs: () -> Unit
) {
    var blockedInput by remember { mutableStateOf("") }
    var allowedInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("") }
    var scheduleInput by remember { mutableStateOf("") }
    var backupInput by remember { mutableStateOf(TextFieldValue("")) }
    var backupMessage by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf(AppTab.Home) }
    var secondaryPage by remember { mutableStateOf<SecondaryPage?>(null) }
    var schedulesReturnPage by remember { mutableStateOf<SecondaryPage?>(null) }
    val homeScrollState = rememberScrollState()
    val rulesScrollState = rememberScrollState()
    val blockListScrollState = rememberScrollState()
    val activityScrollState = rememberScrollState()
    val contentScrollState = when (currentTab) {
        AppTab.Home -> homeScrollState
        AppTab.Rules -> rulesScrollState
        AppTab.BlockList -> blockListScrollState
        AppTab.Activity -> activityScrollState
    }
    var bottomNavigationVisible by remember { mutableStateOf(true) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(secondaryPage) {
        if (secondaryPage != null) contentScrollState.scrollTo(0)
    }

    LaunchedEffect(contentScrollState) {
        var previousScroll = contentScrollState.value
        var accumulatedDelta = 0
        snapshotFlow { contentScrollState.value }.collect { currentScroll ->
            val delta = currentScroll - previousScroll
            accumulatedDelta = when {
                delta > 0 -> if (accumulatedDelta < 0) delta else accumulatedDelta + delta
                delta < 0 -> if (accumulatedDelta > 0) delta else accumulatedDelta + delta
                else -> accumulatedDelta
            }
            when {
                currentScroll == 0 -> {
                    bottomNavigationVisible = true
                    accumulatedDelta = 0
                }
                accumulatedDelta >= 20 -> {
                    bottomNavigationVisible = false
                    accumulatedDelta = 0
                }
                accumulatedDelta <= -20 -> {
                    bottomNavigationVisible = true
                    accumulatedDelta = 0
                }
            }
            previousScroll = currentScroll
        }
    }

    val bottomNavigationProgress by animateFloatAsState(
        targetValue = if (bottomNavigationVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "bottomNavigationProgress"
    )

    Scaffold(
        containerColor = Color(0xFFF3F4F6),
        bottomBar = {
            BottomNavigationBar(
                modifier = Modifier.graphicsLayer {
                    alpha = bottomNavigationProgress
                    translationY = (1f - bottomNavigationProgress) * size.height
                },
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    if (bottomNavigationVisible) {
                        secondaryPage = null
                        currentTab = tab
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(contentScrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (secondaryPage == SecondaryPage.Settings) {
                SettingsPageContent(
                    protectionEnabled = protectionEnabled,
                    strictMode = strictMode,
                    redirectDelaySeconds = redirectDelaySeconds,
                    autoStartOnBoot = autoStartOnBoot,
                    loggingEnabled = loggingEnabled,
                    onBack = { secondaryPage = null },
                    onOpenSchedules = {
                        schedulesReturnPage = SecondaryPage.Settings
                        secondaryPage = SecondaryPage.Schedules
                    },
                    onStrictModeToggle = onStrictModeToggle,
                    onAutoStartToggle = onAutoStartToggle,
                    onLoggingToggle = onLoggingToggle,
                    onRequestVpnSetup = onRequestVpnSetup,
                    backupInput = backupInput,
                    backupMessage = backupMessage,
                    onBackupInputChange = { backupInput = it },
                    onExport = {
                        backupInput = TextFieldValue(onExportBackup())
                        backupMessage = "Backup ready"
                    },
                    onImport = { backupMessage = if (onImportBackup(backupInput.text)) "Backup imported" else "Import failed" }
                )
            } else if (secondaryPage == SecondaryPage.Schedules) {
                SchedulesPageContent(
                    schedules = schedules,
                    inputValue = scheduleInput,
                    onInputChange = { scheduleInput = it },
                    onBack = { secondaryPage = schedulesReturnPage },
                    onSubmit = {
                        val value = scheduleInput.trim()
                        if (value.isNotEmpty()) {
                            onAddSchedule(value)
                            scheduleInput = ""
                        }
                    },
                    onRemoveSchedule = onRemoveSchedule,
                    onUpdateSchedule = onUpdateSchedule
                )
            } else Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 180),
                label = "tabContent"
            ) { tab ->
            when (tab) {
                AppTab.Home -> {
                    HomeTabContent(
                        protectionEnabled = protectionEnabled,
                        vpnConnectionState = vpnConnectionState,
                        vpnConnectedAtMillis = vpnConnectedAtMillis,
                        vpnErrorMessage = vpnErrorMessage,
                        blockedDomains = blockedDomains,
                        allowedDomains = allowedDomains,
                        categories = categories,
                        schedules = schedules,
                        decisionLogs = decisionLogs,
                        onProtectionToggle = onProtectionToggle,
                        onNavigateTab = { currentTab = it },
                        onOpenSettings = { secondaryPage = SecondaryPage.Settings },
                        onOpenSchedules = {
                            schedulesReturnPage = null
                            secondaryPage = SecondaryPage.Schedules
                        }
                    )
                }

                AppTab.Rules -> {
                    RulesPageContent(
                        blockedDomains = blockedDomains,
                        allowedDomains = allowedDomains,
                        categories = categories,
                        onRemoveBlockedDomain = onRemoveBlockedDomain,
                        onRemoveAllowedDomain = onRemoveAllowedDomain,
                        onRemoveCategory = onRemoveCategory,
                        onAddBlockedDomain = onAddBlockedDomain,
                        onAddAllowedDomain = onAddAllowedDomain
                    )
                }

                AppTab.BlockList -> {
                    BlockListTabContent(
                        categories = categories,
                        schedules = schedules,
                        categoryInput = categoryInput,
                        onCategoryInputChange = { categoryInput = it },
                        onCreateCategory = {
                            val value = categoryInput.trim()
                            if (value.isNotEmpty()) {
                                onAddCategory(value)
                                categoryInput = ""
                            }
                        },
                        onRemoveCategory = onRemoveCategory,
                        onAddDomainToCategory = onAddDomainToCategory,
                        onRemoveDomainFromCategory = onRemoveDomainFromCategory,
                        onAssignScheduleToCategory = onAssignScheduleToCategory,
                        scheduleInput = scheduleInput,
                        onScheduleInputChange = { scheduleInput = it },
                        onCreateSchedule = {
                            val value = scheduleInput.trim()
                            if (value.isNotEmpty()) {
                                onAddSchedule(value)
                                scheduleInput = ""
                            }
                        },
                        onRemoveSchedule = onRemoveSchedule,
                        onUpdateSchedule = onUpdateSchedule,
                        blockedDomains = blockedDomains,
                        blockedInput = blockedInput,
                        onBlockedInputChange = { blockedInput = it },
                        onCreateBlockedDomain = {
                            val value = blockedInput.trim()
                            if (value.isNotEmpty()) {
                                onAddBlockedDomain(value)
                                blockedInput = ""
                            }
                        },
                        onRemoveBlockedDomain = onRemoveBlockedDomain,
                        allowedDomains = allowedDomains,
                        allowedInput = allowedInput,
                        onAllowedInputChange = { allowedInput = it },
                        onCreateAllowedDomain = {
                            val value = allowedInput.trim()
                            if (value.isNotEmpty()) {
                                onAddAllowedDomain(value)
                                allowedInput = ""
                            }
                        },
                        onRemoveAllowedDomain = onRemoveAllowedDomain,
                        onAddBlockedDomain = onAddBlockedDomain,
                        onAddAllowedDomain = onAddAllowedDomain
                    )
                }

                AppTab.Activity -> {
                    DecisionLogSection(
                        logs = decisionLogs,
                        onClear = onClearDecisionLogs
                    )
                }

            }
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    protectionEnabled: Boolean,
    vpnConnectionState: VpnConnectionState,
    vpnConnectedAtMillis: Long?,
    vpnErrorMessage: String?,
    blockedDomains: List<String>,
    allowedDomains: List<String>,
    categories: List<BlockingCategory>,
    schedules: List<BlockingSchedule>,
    decisionLogs: List<Decision>,
    onProtectionToggle: (Boolean) -> Unit,
    onNavigateTab: (AppTab) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSchedules: () -> Unit
) {
    val now = System.currentTimeMillis()
    val blockedToday = decisionLogs.count { !it.allow && isSameDay(it.timestampMillis, now) }
    val requestsToday = decisionLogs.count { isSameDay(it.timestampMillis, now) }
    val activeRules = blockedDomains.size + allowedDomains.size + categories.sumOf { it.domains.size } + schedules.size
    val activeSchedule = schedules.firstOrNull { it.isActiveAt(now) } ?: schedules.firstOrNull { it.enabled }
    val greeting = greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    val isTransitioning = vpnConnectionState == VpnConnectionState.Connecting ||
        vpnConnectionState == VpnConnectionState.Disconnecting
    val uptimeSeconds by produceState(
        initialValue = 0L,
        key1 = vpnConnectionState,
        key2 = vpnConnectedAtMillis
    ) {
        while (vpnConnectionState == VpnConnectionState.Connected && vpnConnectedAtMillis != null) {
            value = ((System.currentTimeMillis() - vpnConnectedAtMillis) / 1000L).coerceAtLeast(0L)
            delay(1_000L)
        }
    }
    val statusLabel = when (vpnConnectionState) {
        VpnConnectionState.Connecting -> "Connecting"
        VpnConnectionState.Disconnecting -> "Disconnecting"
        VpnConnectionState.Error -> "Connection Error"
        VpnConnectionState.Connected -> "Protected"
        VpnConnectionState.Disconnected -> "Unprotected"
    }
    val statusTitle = when (vpnConnectionState) {
        VpnConnectionState.Connecting -> "VPN Connecting"
        VpnConnectionState.Disconnecting -> "VPN Disconnecting"
        VpnConnectionState.Error -> "VPN Error"
        VpnConnectionState.Connected -> "VPN Active"
        VpnConnectionState.Disconnected -> "VPN Inactive"
    }
    val statusIsGreen = protectionEnabled || vpnConnectionState == VpnConnectionState.Connecting

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("ShieldFocus", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeHeaderIcon(Icons.Outlined.Notifications, "Notifications", onClick = {})
                HomeHeaderIcon(Icons.Outlined.Settings, "Settings", onClick = onOpenSettings)
                }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (statusIsGreen) Color(0xFFA9D6C2) else Color(0xFFE1E5E7)
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(if (protectionEnabled) 16.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (protectionEnabled) 10.dp else 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (statusIsGreen) Color(0xFF16835A) else Color(0xFFEF3438),
                                        RoundedCornerShape(999.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                statusLabel,
                                color = if (statusIsGreen) Color(0xFF16835A) else Color(0xFFEF3438),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            statusTitle,
                            fontSize = 24.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (vpnConnectionState) {
                                VpnConnectionState.Connected -> "Uptime: ${formatUptime(uptimeSeconds)}"
                                VpnConnectionState.Connecting -> "Starting secure DNS protection…"
                                VpnConnectionState.Disconnecting -> "Stopping secure DNS protection…"
                                VpnConnectionState.Error -> vpnErrorMessage ?: "Unable to change protection state"
                                VpnConnectionState.Disconnected -> "Device traffic unfiltered"
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF8A929F)
                        )
                    }
                    Card(
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (statusIsGreen) Color(0xFFDDF6E9) else Color(0xFFFFE5E6)
                        )
                    ) {
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = if (statusIsGreen) Color(0xFF16835A) else Color(0xFFEF3438),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = { onProtectionToggle(!protectionEnabled) },
                    enabled = !isTransitioning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(43.dp),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (protectionEnabled || vpnConnectionState == VpnConnectionState.Disconnecting) Color(0xFFE53935) else Color(0xFF19784F),
                        disabledContainerColor = if (vpnConnectionState == VpnConnectionState.Disconnecting) Color(0xFFE53935) else Color(0xFF19784F),
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        when (vpnConnectionState) {
                            VpnConnectionState.Connecting -> "Connecting…"
                            VpnConnectionState.Disconnecting -> "Disconnecting…"
                            VpnConnectionState.Connected -> "Turn Off Protection"
                            else -> "Turn On Protection"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (protectionEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            icon = Icons.Outlined.ViewList,
                            label = "DNS Filter",
                            value = "On"
                        )
                        StatusPill(
                            icon = Icons.Outlined.Schedule,
                            label = "Schedule",
                            value = activeSchedule?.name ?: "Work Mode"
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.DoNotDisturbAlt,
                iconTint = Color(0xFF6B7280),
                value = blockedToday.toString(),
                label = "Blocked Today"
            )
            HomeStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.BarChart,
                iconTint = Color(0xFF6B7280),
                value = requestsToday.toString(),
                label = "Requests Today"
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Rule,
                iconTint = Color(0xFF6B7280),
                value = activeRules.toString(),
                label = "Active Rules"
            )
            HomeScheduleCard(
                modifier = Modifier.weight(1f),
                scheduleName = activeSchedule?.name ?: "Work Mode",
                active = activeSchedule != null
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quick Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickActionButton(Modifier.weight(1f), Icons.Outlined.Add, "Add Rule") { onNavigateTab(AppTab.Rules) }
                QuickActionButton(Modifier.weight(1f), Icons.Outlined.BarChart, "View Log") { onNavigateTab(AppTab.Activity) }
                QuickActionButton(Modifier.weight(1f), Icons.Outlined.Schedule, "Schedules", onOpenSchedules)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Activity", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onNavigateTab(AppTab.Activity) }, contentPadding = PaddingValues(0.dp)) {
                Text("View all", fontSize = 12.sp, color = Color(0xFF19784F), fontWeight = FontWeight.SemiBold)
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E5E7)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            if (decisionLogs.isEmpty()) {
                Text(
                    "No activity yet",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column {
                    decisionLogs.take(3).forEach { decision -> RecentActivityItem(decision) }
                }
            }
        }
    }
}

@Composable
private fun HomeStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    label: String
) {
    Card(
        modifier = modifier.height(103.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E5E7)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                Text(label, fontSize = 11.sp, color = Color(0xFF9299A5))
            }
        }
    }
}

@Composable
private fun HomeScheduleCard(
    modifier: Modifier = Modifier,
    scheduleName: String,
    active: Boolean
) {
    Card(
        modifier = modifier.height(103.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E5E7)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(imageVector = Icons.Outlined.Schedule, contentDescription = null, tint = Color(0xFF7D8795), modifier = Modifier.size(17.dp))
                Card(
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) Color(0xFFD1FAE5) else Color(0xFFE5E7EB)
                    )
                ) {
                    Text(
                        text = if (active) "Active" else "Idle",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = if (active) Color(0xFF0F9D58) else Color(0xFF4B5563),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(scheduleName, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                Text("Schedule", fontSize = 11.sp, color = Color(0xFF9299A5))
            }
        }
    }
}

@Composable
private fun StatusPill(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F8))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF0F9D58),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(70.dp)
            .noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E5E7)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF19784F), modifier = Modifier.size(21.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HomeHeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.noRippleClickable(onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E5E7)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = Color(0xFF4B5563), modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun RecentActivityItem(decision: Decision) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (decision.allow) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                )
            ) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (decision.allow) Icons.Outlined.Code else Icons.Outlined.DoNotDisturbAlt,
                        contentDescription = null,
                        tint = if (decision.allow) Color(0xFF0F9D58) else Color(0xFFE53935)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(decision.domain, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
                Text(decision.reason, fontSize = 11.sp, color = Color(0xFF9299A5))
            }

            Column(horizontalAlignment = Alignment.End) {
                Card(
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (decision.allow) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                    )
                ) {
                    Text(
                        text = if (decision.allow) "Allowed" else "Blocked",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = if (decision.allow) Color(0xFF0F9D58) else Color(0xFFE53935),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    text = formatRelativeTime(decision.timestampMillis),
                    fontSize = 10.sp,
                    color = Color(0xFF9299A5)
                )
            }
        }
}

private fun formatRelativeTime(timestampMillis: Long): String {
    val diffMinutes = ((System.currentTimeMillis() - timestampMillis) / 60000L).coerceAtLeast(0)
    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffMinutes < 1440 -> "${diffMinutes / 60} h ago"
        else -> "${diffMinutes / 1440} d ago"
    }
}

private fun greetingForHour(hour: Int): String {
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

private fun formatUptime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun isSameDay(timestampMillis: Long, referenceMillis: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = timestampMillis }
    val b = Calendar.getInstance().apply { timeInMillis = referenceMillis }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesPageContent(
    blockedDomains: List<String>,
    allowedDomains: List<String>,
    categories: List<BlockingCategory>,
    onRemoveBlockedDomain: (String) -> Unit,
    onRemoveAllowedDomain: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onAddBlockedDomain: (String) -> Unit,
    onAddAllowedDomain: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(RuleFilter.All) }
    var searchQuery by remember { mutableStateOf("") }
    var disabledKeys by remember { mutableStateOf(emptySet<String>()) }
    var showAddSheet by remember { mutableStateOf(false) }
    var sheetMode by remember { mutableStateOf(RuleSheetMode.Block) }
    var sheetDomain by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val rules = buildList {
        blockedDomains.forEach { domain ->
            add(RulesDisplayItem("blocked:$domain", domain, ruleCategoryFor(domain), true, RuleSource.Blocked))
        }
        allowedDomains.forEach { domain ->
            add(RulesDisplayItem("allowed:$domain", domain, ruleCategoryFor(domain), false, RuleSource.Allowed))
        }
        categories.forEach { category ->
            add(RulesDisplayItem("category:${category.id}", category.name, "Category", true, RuleSource.Category, category.id))
        }
    }
    val visibleRules = rules.filter { rule ->
        val matchesFilter = when (selectedFilter) {
            RuleFilter.All -> true
            RuleFilter.Blocked -> rule.blocked && rule.source != RuleSource.Category
            RuleFilter.Allowed -> !rule.blocked
            RuleFilter.Categories -> rule.source == RuleSource.Category
        }
        val matchesSearch = searchQuery.isBlank() ||
            rule.title.contains(searchQuery, ignoreCase = true) ||
            rule.subtitle.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
    }
    val activeCount = rules.count { it.key !in disabledKeys }
    val blockingCount = rules.count { it.blocked && it.key !in disabledKeys }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Filter Rules", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                    Text("$activeCount rules active", fontSize = 12.sp, color = Color(0xFF9299A5))
                }
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Shield, null, tint = Color(0xFF147A51), modifier = Modifier.size(14.dp))
                        Text("$blockingCount blocking", color = Color(0xFF147A51), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFFA0A8B5), modifier = Modifier.size(19.dp))
                },
                placeholder = { Text("Search domains or categories...", fontSize = 13.sp, color = Color(0xFFA0A8B5)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFFDDE2E7),
                    unfocusedBorderColor = Color(0xFFDDE2E7)
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleFilter.entries.forEach { filter ->
                    RulesFilterChip(
                        label = when (filter) {
                            RuleFilter.All -> "All"
                            RuleFilter.Blocked -> "Blocked"
                            RuleFilter.Allowed -> "Allowed"
                            RuleFilter.Categories -> "Categories"
                        },
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleRules.forEach { rule ->
                    SwipeDeleteRuleCard(
                        key = rule.key,
                        rule = rule,
                        enabled = rule.key !in disabledKeys,
                        onEnabledChange = { enabled ->
                            disabledKeys = if (enabled) disabledKeys - rule.key else disabledKeys + rule.key
                        },
                        onDelete = {
                            when (rule.source) {
                                RuleSource.Blocked -> onRemoveBlockedDomain(rule.title)
                                RuleSource.Allowed -> onRemoveAllowedDomain(rule.title)
                                RuleSource.Category -> onRemoveCategory(rule.sourceId)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(68.dp))
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .noRippleClickable {
                    sheetMode = RuleSheetMode.Block
                    showAddSheet = true
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF147A51))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("Add Rule", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, sheetState = sheetState) {
            AddRuleSheet(
                domain = sheetDomain,
                onDomainChange = { sheetDomain = it },
                selectedMode = sheetMode,
                onSelectMode = { sheetMode = it },
                onClose = {
                    showAddSheet = false
                    sheetDomain = ""
                },
                onSubmit = {
                    val value = sheetDomain.trim()
                    if (value.isNotEmpty()) {
                        if (sheetMode == RuleSheetMode.Block) onAddBlockedDomain(value) else onAddAllowedDomain(value)
                    }
                    showAddSheet = false
                    sheetDomain = ""
                }
            )
        }
    }
}

@Composable
private fun RulesFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE0E4E8)),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF147A51) else Color.White)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            color = if (selected) Color.White else Color(0xFF6F7888),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteRuleCard(
    key: String,
    rule: RulesDisplayItem,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFDEDF), RoundedCornerShape(12.dp))
                    .padding(end = 23.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Outlined.Delete, "Delete $key", tint = Color(0xFFEA2F36), modifier = Modifier.size(22.dp))
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E5E8)),
            colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF8F9FA))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconStyle = rulesIconStyle(rule)
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = iconStyle.first)
                ) {
                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Icon(iconStyle.third, null, tint = iconStyle.second, modifier = Modifier.size(20.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(rule.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color(0xFF374151) else Color(0xFF9AA3B2))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.subtitle, fontSize = 11.sp, color = Color(0xFF9AA3B2))
                        Card(
                            shape = RoundedCornerShape(5.dp),
                            colors = CardDefaults.cardColors(containerColor = if (rule.blocked) Color(0xFFFFDFE1) else Color(0xFFCFF5DF))
                        ) {
                            Text(
                                if (rule.blocked) "Blocked" else "Allowed",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                color = if (rule.blocked) Color(0xFFEF3438) else Color(0xFF16835A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF147A51),
                        checkedTrackColor = Color(0xFFCFF8E3),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE4E7ED),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }
}

private data class RulesDisplayItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val blocked: Boolean,
    val source: RuleSource,
    val sourceId: String = title
)

private enum class RuleSource { Blocked, Allowed, Category }

private fun ruleCategoryFor(domain: String): String = when {
    domain.contains("ad", ignoreCase = true) -> "Advertising"
    domain.contains("track", ignoreCase = true) || domain.contains("metric", ignoreCase = true) -> "Tracking"
    domain.contains("social", ignoreCase = true) || domain.contains("facebook", ignoreCase = true) -> "Social Media"
    domain.contains("malware", ignoreCase = true) || domain.contains("phish", ignoreCase = true) -> "Malware"
    domain.contains("twitch", ignoreCase = true) || domain.contains("video", ignoreCase = true) -> "Streaming"
    else -> "Development"
}

private fun rulesIconStyle(rule: RulesDisplayItem): Triple<Color, Color, androidx.compose.ui.graphics.vector.ImageVector> = when {
    rule.source == RuleSource.Category -> Triple(Color(0xFFE5E7EB), Color(0xFF8D96A6), Icons.Outlined.People)
    rule.subtitle == "Advertising" -> Triple(Color(0xFFFFE3E4), Color(0xFFEF3438), Icons.Outlined.DoNotDisturbAlt)
    rule.subtitle == "Tracking" -> Triple(Color(0xFFFFF0C9), Color(0xFFE59A14), Icons.Outlined.FlashOn)
    rule.subtitle == "Malware" -> Triple(Color(0xFFFFE0E2), Color(0xFFE52C35), Icons.Outlined.Shield)
    rule.subtitle == "Streaming" -> Triple(Color(0xFFEAE4FF), Color(0xFF8255F6), Icons.Outlined.PlayCircleOutline)
    else -> Triple(Color(0xFFD5F8E5), Color(0xFF16835A), Icons.Outlined.Edit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockListTabContent(
    categories: List<BlockingCategory>,
    schedules: List<BlockingSchedule>,
    categoryInput: String,
    onCategoryInputChange: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onRemoveCategory: (String) -> Unit,
    onAddDomainToCategory: (String, String) -> Unit,
    onRemoveDomainFromCategory: (String, String) -> Unit,
    onAssignScheduleToCategory: (String, String) -> Unit,
    scheduleInput: String,
    onScheduleInputChange: (String) -> Unit,
    onCreateSchedule: () -> Unit,
    onRemoveSchedule: (String) -> Unit,
    onUpdateSchedule: (String, String, Set<Int>, Int, Int, Boolean) -> Unit,
    blockedDomains: List<String>,
    blockedInput: String,
    onBlockedInputChange: (String) -> Unit,
    onCreateBlockedDomain: () -> Unit,
    onRemoveBlockedDomain: (String) -> Unit,
    allowedDomains: List<String>,
    allowedInput: String,
    onAllowedInputChange: (String) -> Unit,
    onCreateAllowedDomain: () -> Unit,
    onRemoveAllowedDomain: (String) -> Unit,
    onAddBlockedDomain: (String) -> Unit,
    onAddAllowedDomain: (String) -> Unit
) {
    var selectedSection by remember { mutableStateOf(BlocklistSection.DefaultLists) }
    val defaultPresets = defaultBlocklistPresetsClean()
    val categoryPresets = defaultCategoryPresetsClean()
    var importedPresetIds by remember { mutableStateOf(setOf(defaultPresets.first().id)) }
    var enabledPresetIds by remember { mutableStateOf(setOf(defaultPresets.first().id)) }
    var activeCategoryIds by remember { mutableStateOf(setOf("ad-networks", "trackers", "malware-phishing")) }
    val activeBlockedCount = blockedDomains.size + activeCategoryIds.size
    val activeCategoryCount = activeCategoryIds.size

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ViewList,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column {
                            Text("Blocklists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Manage pre-built lists and category presets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Card(
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                ) {
                    Text(
                        text = "${compactCount(activeBlockedCount)} blocked",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFF0F9D58),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                BlocklistSectionTab(
                    modifier = Modifier.weight(1f),
                    label = "Default Lists",
                    selected = selectedSection == BlocklistSection.DefaultLists,
                    onClick = { selectedSection = BlocklistSection.DefaultLists }
                )
                BlocklistSectionTab(
                    modifier = Modifier.weight(1f),
                    label = "Categories",
                    selected = selectedSection == BlocklistSection.Categories,
                    onClick = { selectedSection = BlocklistSection.Categories }
                )
            }

            when (selectedSection) {
                BlocklistSection.DefaultLists -> {
                    BlocklistHeroCard(
                        title = "Pre-built Blocklists",
                        subtitle = "Tap a list to import and activate it instantly",
                        icon = Icons.Outlined.ViewList
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        defaultPresets.forEach { preset ->
                            BlocklistPresetCard(
                                preset = preset,
                                imported = preset.id in importedPresetIds,
                                enabled = preset.id in enabledPresetIds,
                                onImport = {
                                    importedPresetIds = importedPresetIds + preset.id
                                    enabledPresetIds = enabledPresetIds + preset.id
                                },
                                onEnabledChange = { enabled ->
                                    enabledPresetIds = if (enabled) {
                                        enabledPresetIds + preset.id
                                    } else {
                                        enabledPresetIds - preset.id
                                    }
                                }
                            )
                        }
                    }
                }

                BlocklistSection.Categories -> {
                    BlocklistHeroCard(
                        title = "Category Toggles",
                        subtitle = "${compactCount(activeCategoryCount)} of ${categoryPresets.size} categories active",
                        icon = Icons.Outlined.Shield
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        categoryPresets.forEach { preset ->
                            val checked = activeCategoryIds.contains(preset.id)
                            CategoryPresetCard(
                                preset = preset,
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    activeCategoryIds = if (enabled) {
                                        activeCategoryIds + preset.id
                                    } else {
                                        activeCategoryIds - preset.id
                                    }
                                }
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5EE))
                    ) {
                        Text(
                            text = "Category toggles apply across all imported blocklists.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            color = Color(0xFF0F9D58),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun BlocklistSectionTab(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF1D4ED8) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.titleSmall
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    color = if (selected) Color(0xFF1D4ED8) else Color.Transparent,
                    shape = RoundedCornerShape(999.dp)
                )
        )
    }
}

@Composable
private fun BlocklistHeroCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1D4ED8),
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BlocklistPresetCard(
    preset: BlocklistPreset,
    imported: Boolean,
    enabled: Boolean,
    onImport: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (enabled) preset.accentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = preset.accentColor.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = preset.icon,
                        contentDescription = null,
                        tint = preset.accentColor,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            preset.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (preset.recommended) {
                            Card(
                                shape = RoundedCornerShape(999.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5EE))
                            ) {
                                Text(
                                    "Recommended",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = Color(0xFF0F9D58),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Text(
                        preset.meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (imported) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF147A51),
                            checkedTrackColor = Color(0xFFCFF8E3),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE3E7ED),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                } else {
                    Button(
                        onClick = onImport,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = preset.accentColor)
                    ) {
                        Text("Import")
                    }
                }
            }

            Text(
                preset.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                preset.tags.forEach { tag ->
                    val tagColors = blocklistTagColors(tag)
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, tagColors.second.copy(alpha = 0.22f)),
                        colors = CardDefaults.cardColors(containerColor = tagColors.first)
                    ) {
                        Text(
                            text = tag,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = tagColors.second,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (imported) {
                Card(
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFFD1FAE5) else Color(0xFFE5E7EB))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (enabled) Icons.Outlined.CheckCircleOutline else Icons.Outlined.PauseCircleOutline,
                            contentDescription = null,
                            tint = if (enabled) Color(0xFF16835A) else Color(0xFF7C8491),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            if (enabled) "Active" else "Paused",
                            color = if (enabled) Color(0xFF16835A) else Color(0xFF7C8491),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPresetCard(
    preset: CategoryPreset,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (checked) preset.accentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = preset.accentColor.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = preset.icon,
                    contentDescription = null,
                    tint = preset.accentColor,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (checked) {
                        Card(
                            shape = RoundedCornerShape(999.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5EE))
                        ) {
                            Text(
                                "Active",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = Color(0xFF0F9D58),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    preset.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private data class BlocklistPreset(
    val id: String,
    val name: String,
    val meta: String,
    val description: String,
    val tags: List<String>,
    val accentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val recommended: Boolean = false
)

private data class CategoryPreset(
    val id: String,
    val name: String,
    val meta: String,
    val description: String,
    val accentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun defaultBlocklistPresets(): List<BlocklistPreset> {
    return listOf(
        BlocklistPreset(
            id = "shieldfocus-default",
            name = "ShieldFocus Default",
            meta = "10k domains · Updated Jul 15, 2026",
            description = "Curated list covering ads, trackers, and known malware domains. Best starting point for most users.",
            tags = listOf("Ads", "Trackers", "Malware"),
            accentColor = Color(0xFF1D7A4A),
            icon = Icons.Outlined.Shield,
            recommended = true
        ),
        BlocklistPreset(
            id = "hagezi-pro",
            name = "Hagezi Pro",
            meta = "49k domains · Updated Jul 14, 2026",
            description = "High-quality multi-purpose blocklist maintained by the community. Balanced coverage for ads, trackers, and telemetry.",
            tags = listOf("Ads", "Trackers", "Telemetry"),
            accentColor = Color(0xFF2563EB),
            icon = Icons.Outlined.FlashOn
        ),
        BlocklistPreset(
            id = "oisd-big",
            name = "OISD Big",
            meta = "185k domains · Updated Jul 13, 2026",
            description = "One of the most comprehensive blocklists. Aggregates multiple sources for broad coverage of ads and trackers.",
            tags = listOf("Ads", "Trackers", "Social", "Telemetry"),
            accentColor = Color(0xFF7C3AED),
            icon = Icons.Outlined.ViewList
        ),
        BlocklistPreset(
            id = "malware-list",
            name = "Malware Domain List",
            meta = "12k domains · Updated Jul 16, 2026",
            description = "Focused on active phishing, malware, and ransomware distribution domains. Updated daily from threat intelligence.",
            tags = listOf("Security", "Phishing", "Malware"),
            accentColor = Color(0xFFDC2626),
            icon = Icons.Outlined.Block
        )
    )
}

private fun defaultCategoryPresets(): List<CategoryPreset> {
    return listOf(
        CategoryPreset(
            id = "ad-networks",
            name = "Ad Networks",
            meta = "5k domains · Banner ads, pop-ups, and ad delivery",
            description = "Blocks advertising infrastructure across websites and apps.",
            accentColor = Color(0xFF1D7A4A),
            icon = Icons.Outlined.Block
        ),
        CategoryPreset(
            id = "trackers",
            name = "Trackers",
            meta = "3k domains · Analytics, pixel trackers, and beacons",
            description = "Stops analytics networks and tracking scripts from following browsing activity.",
            accentColor = Color(0xFFEA580C),
            icon = Icons.Outlined.Schedule
        ),
        CategoryPreset(
            id = "malware-phishing",
            name = "Malware & Phishing",
            meta = "2k domains · Known malicious and impersonation domains",
            description = "Adds protection against scam pages, credential theft, and malware delivery.",
            accentColor = Color(0xFF7C3AED),
            icon = Icons.Outlined.Shield
        ),
        CategoryPreset(
            id = "social-media",
            name = "Social Media",
            meta = "890 domains · Social network trackers and embeds",
            description = "Useful for focus sessions and reducing distraction-heavy social embeds.",
            accentColor = Color(0xFF6B7280),
            icon = Icons.Outlined.Home
        ),
        CategoryPreset(
            id = "telemetry",
            name = "Telemetry",
            meta = "640 domains · OS and app telemetry endpoints",
            description = "Blocks platform telemetry and app reporting domains.",
            accentColor = Color(0xFF64748B),
            icon = Icons.Outlined.BarChart
        ),
        CategoryPreset(
            id = "gambling",
            name = "Gambling",
            meta = "1k domains · Online gambling and betting",
            description = "Targets gambling platforms and associated ad networks.",
            accentColor = Color(0xFFB45309),
            icon = Icons.Outlined.FlashOn
        ),
        CategoryPreset(
            id = "adult-content",
            name = "Adult Content",
            meta = "3k domains · Adult websites and explicit media",
            description = "Restricts adult content categories and associated content delivery hosts.",
            accentColor = Color(0xFFDB2777),
            icon = Icons.Outlined.Block
        ),
        CategoryPreset(
            id = "cryptomining",
            name = "Cryptomining",
            meta = "310 domains · Browser-based crypto miners",
            description = "Blocks in-browser mining scripts and pool endpoints.",
            accentColor = Color(0xFF0EA5E9),
            icon = Icons.Outlined.Schedule
        )
    )
}

private fun compactCount(value: Int): String {
    return when {
        value >= 1_000_000 -> "${value / 1_000_000}m"
        value >= 1_000 -> "${value / 1_000}k"
        else -> value.toString()
    }
}

private fun blocklistTagColors(tag: String): Pair<Color, Color> = when (tag.lowercase()) {
    "ads" -> Color(0xFFFFE8E8) to Color(0xFFE64A4F)
    "trackers" -> Color(0xFFFFF0DC) to Color(0xFFD97706)
    "malware" -> Color(0xFFF0E8FF) to Color(0xFF7C3AED)
    "phishing", "security" -> Color(0xFFFFE7E7) to Color(0xFFDC2626)
    "social" -> Color(0xFFE7F0FF) to Color(0xFF2563EB)
    "telemetry" -> Color(0xFFE0F6F0) to Color(0xFF159A76)
    "adult" -> Color(0xFFFFE8F1) to Color(0xFFDB2777)
    "gambling" -> Color(0xFFFFE6F0) to Color(0xFFE23D7D)
    else -> Color(0xFFE8EEF5) to Color(0xFF64748B)
}

private fun defaultBlocklistPresetsClean(): List<BlocklistPreset> {
    return listOf(
        BlocklistPreset(
            id = "shieldfocus-default",
            name = "ShieldFocus Default",
            meta = "10k domains - Updated Jul 15, 2026",
            description = "Curated list covering ads, trackers, and known malware domains. Best starting point for most users.",
            tags = listOf("Ads", "Trackers", "Malware"),
            accentColor = Color(0xFF1D7A4A),
            icon = Icons.Outlined.Shield,
            recommended = true
        ),
        BlocklistPreset(
            id = "hagezi-pro",
            name = "Hagezi Pro",
            meta = "49k domains - Updated Jul 14, 2026",
            description = "High-quality multi-purpose blocklist maintained by the community. Balanced coverage for ads, trackers, and telemetry.",
            tags = listOf("Ads", "Trackers", "Telemetry"),
            accentColor = Color(0xFF2563EB),
            icon = Icons.Outlined.FlashOn
        ),
        BlocklistPreset(
            id = "oisd-big",
            name = "OISD Big",
            meta = "185k domains - Updated Jul 13, 2026",
            description = "One of the most comprehensive blocklists. Aggregates multiple sources for broad coverage of ads and trackers.",
            tags = listOf("Ads", "Trackers", "Social", "Telemetry"),
            accentColor = Color(0xFF7C3AED),
            icon = Icons.Outlined.ViewList
        ),
        BlocklistPreset(
            id = "malware-list",
            name = "Malware Domain List",
            meta = "12k domains - Updated Jul 16, 2026",
            description = "Focused on active phishing, malware, and ransomware distribution domains. Updated daily from threat intelligence.",
            tags = listOf("Security", "Phishing", "Malware"),
            accentColor = Color(0xFFDC2626),
            icon = Icons.Outlined.Block
        ),
        BlocklistPreset(
            id = "energized-ultimate",
            name = "Energized Ultimate",
            meta = "320k domains - Updated Jul 12, 2026",
            description = "Ultimate protection with adult content, gambling, and social media blocking on top of standard ad and tracker coverage.",
            tags = listOf("Ads", "Trackers", "Malware", "Adult", "Gambling"),
            accentColor = Color(0xFFF59E0B),
            icon = Icons.Outlined.FlashOn
        )
    )
}

private fun defaultCategoryPresetsClean(): List<CategoryPreset> {
    return listOf(
        CategoryPreset(
            id = "ad-networks",
            name = "Ad Networks",
            meta = "5k domains - Banner ads, pop-ups, and ad delivery",
            description = "Blocks advertising infrastructure across websites and apps.",
            accentColor = Color(0xFF1D7A4A),
            icon = Icons.Outlined.Block
        ),
        CategoryPreset(
            id = "trackers",
            name = "Trackers",
            meta = "3k domains - Analytics, pixel trackers, and beacons",
            description = "Stops analytics networks and tracking scripts from following browsing activity.",
            accentColor = Color(0xFFEA580C),
            icon = Icons.Outlined.Schedule
        ),
        CategoryPreset(
            id = "malware-phishing",
            name = "Malware & Phishing",
            meta = "2k domains - Known malicious and impersonation domains",
            description = "Adds protection against scam pages, credential theft, and malware delivery.",
            accentColor = Color(0xFF7C3AED),
            icon = Icons.Outlined.Shield
        ),
        CategoryPreset(
            id = "social-media",
            name = "Social Media",
            meta = "890 domains - Social network trackers and embeds",
            description = "Useful for focus sessions and reducing distraction-heavy social embeds.",
            accentColor = Color(0xFF6B7280),
            icon = Icons.Outlined.Home
        ),
        CategoryPreset(
            id = "telemetry",
            name = "Telemetry",
            meta = "640 domains - OS and app telemetry endpoints",
            description = "Blocks platform telemetry and app reporting domains.",
            accentColor = Color(0xFF64748B),
            icon = Icons.Outlined.BarChart
        ),
        CategoryPreset(
            id = "gambling",
            name = "Gambling",
            meta = "1k domains - Online gambling and betting",
            description = "Targets gambling platforms and associated ad networks.",
            accentColor = Color(0xFFB45309),
            icon = Icons.Outlined.FlashOn
        ),
        CategoryPreset(
            id = "adult-content",
            name = "Adult Content",
            meta = "3k domains - Adult websites and explicit media",
            description = "Restricts adult content categories and associated content delivery hosts.",
            accentColor = Color(0xFFDB2777),
            icon = Icons.Outlined.Block
        ),
        CategoryPreset(
            id = "cryptomining",
            name = "Cryptomining",
            meta = "310 domains - Browser-based crypto miners",
            description = "Blocks in-browser mining scripts and pool endpoints.",
            accentColor = Color(0xFF0EA5E9),
            icon = Icons.Outlined.Schedule
        )
    )
}

@Composable
private fun FilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFF1D7A4A) else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE8F5EE) else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (selected) Color(0xFF1D7A4A) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun RuleItemCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    badgeTextColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    switchChecked: Boolean,
    onSwitchToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = badgeColor)
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(containerColor = badgeColor)
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = badgeTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Switch(checked = switchChecked, onCheckedChange = onSwitchToggle)
        }
    }
}

@Composable
private fun AddRuleSheet(
    domain: String,
    onDomainChange: (String) -> Unit,
    selectedMode: RuleSheetMode,
    onSelectMode: (RuleSheetMode) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add Domain Rule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Enter a domain to block or allow across all browsers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = domain,
            onValueChange = onDomainChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Home, contentDescription = null)
            },
            placeholder = { Text("Domain") }
        )

        Text("Rule Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            RuleModeButton(
                modifier = Modifier.weight(1f),
                label = "Block",
                selected = selectedMode == RuleSheetMode.Block,
                selectedColor = Color(0xFFFEE2E2),
                selectedTextColor = Color(0xFFE53935),
                onClick = { onSelectMode(RuleSheetMode.Block) }
            )
            RuleModeButton(
                modifier = Modifier.weight(1f),
                label = "Allow",
                selected = selectedMode == RuleSheetMode.Allow,
                selectedColor = Color(0xFFD1FAE5),
                selectedTextColor = Color(0xFF0F9D58),
                onClick = { onSelectMode(RuleSheetMode.Allow) }
            )
        }

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedMode == RuleSheetMode.Block) Color(0xFF1D7A4A).copy(alpha = 0.95f) else Color(0xFF1D7A4A)
            )
        ) {
            Text(if (selectedMode == RuleSheetMode.Block) "Block Domain" else "Allow Domain")
        }

        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}

@Composable
private fun RuleModeButton(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    selectedTextColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) selectedTextColor else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (label == "Block") Icons.Outlined.Block else Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                tint = if (selected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                color = if (selected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

private enum class RuleFilter {
    All,
    Blocked,
    Allowed,
    Categories
}

private enum class RuleSheetMode {
    Block,
    Allow
}

private enum class BlocklistSection {
    DefaultLists,
    Categories
}

private enum class SecondaryPage {
    Settings,
    Schedules
}

@Composable
private fun SettingsPageContent(
    protectionEnabled: Boolean,
    strictMode: Boolean,
    redirectDelaySeconds: Int,
    autoStartOnBoot: Boolean,
    loggingEnabled: Boolean,
    onBack: () -> Unit,
    onOpenSchedules: () -> Unit,
    onStrictModeToggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onLoggingToggle: (Boolean) -> Unit,
    onRequestVpnSetup: () -> Unit,
    backupInput: TextFieldValue,
    backupMessage: String,
    onBackupInputChange: (TextFieldValue) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    PageHeader(
        title = "Settings",
        subtitle = "Manage ShieldFocus preferences",
        onBack = onBack
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Strict mode", fontWeight = FontWeight.Medium)
                    Text(
                        "Block uncertain domains instead of warning.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = strictMode, onCheckedChange = onStrictModeToggle)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Redirect delay", fontWeight = FontWeight.Medium)
                    Text(
                        "$redirectDelaySeconds seconds on the desktop flow.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRequestVpnSetup,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(if (protectionEnabled) "VPN active" else "Request VPN setup")
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Start on boot", fontWeight = FontWeight.Medium)
                    Text(
                        "Enable protection automatically after restart.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = autoStartOnBoot, onCheckedChange = onAutoStartToggle)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Decision logging", fontWeight = FontWeight.Medium)
                    Text(
                        "Keep a local history of allow and block decisions.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = loggingEnabled, onCheckedChange = onLoggingToggle)
            }
        }
    }

    Text("Menu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SettingsMenuRow(
        icon = Icons.Outlined.Schedule,
        title = "Schedules",
        subtitle = "Choose when protection rules are active",
        onClick = onOpenSchedules
    )

    BackupSection(
        backupInput = backupInput,
        backupMessage = backupMessage,
        onBackupInputChange = onBackupInputChange,
        onExport = onExport,
        onImport = onImport
    )
}

@Composable
private fun SettingsMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().noRippleClickable(onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDDF6E9))) {
                Icon(icon, null, tint = Color(0xFF16835A), modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFF8A929F))
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.noRippleClickable(onBack),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Icon(Icons.Outlined.ArrowBack, "Back", modifier = Modifier.padding(10.dp).size(20.dp), tint = Color(0xFF374151))
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    val selectedColor = Color(0xFF1F7A4D)
    val unselectedColor = Color(0xFF8E96A8)

    Card(
        modifier = modifier.navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .noRippleClickable { onTabSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) selectedColor else unselectedColor
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            color = selectedColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(selectedColor, RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }
    }
}

private enum class AppTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Home("Home", Icons.Outlined.Home),
    Rules("Rules", Icons.Outlined.Shield),
    BlockList("Block List", Icons.Outlined.ViewList),
    Activity("Activity", Icons.Outlined.BarChart)
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick
)

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
                Column {
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
                "ShieldFocus Android filters domain traffic on-device through a local VPN service.",
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

@Composable
private fun DomainSection(
    title: String,
    subtitle: String,
    inputValue: String,
    placeholder: String,
    buttonLabel: String,
    domains: List<String>,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemove: (String) -> Unit
) {
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(0.78f),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text(placeholder) }
                )
                Button(
                    onClick = onSubmit,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(buttonLabel)
                }
            }

            if (domains.isEmpty()) {
                Text(
                    text = "No entries yet",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    domains.forEach { domain ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(domain, fontWeight = FontWeight.Medium)
                            TextButton(onClick = { onRemove(domain) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    categories: List<BlockingCategory>,
    schedules: List<BlockingSchedule>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemoveCategory: (String) -> Unit,
    onAddDomain: (String, String) -> Unit,
    onRemoveDomain: (String, String) -> Unit,
    onAssignSchedule: (String, String) -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Categories", style = MaterialTheme.typography.titleMedium)
                    Text("Group blocked domains by topic", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onSubmit,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("New Category")
                }
            }

            OutlinedTextField(
                value = inputValue,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("e.g. Social Media") }
            )

            if (categories.isEmpty()) {
                Text("No categories yet", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    categories.forEach { category ->
                        CategoryCard(
                            category = category,
                            schedules = schedules,
                            onRemoveCategory = onRemoveCategory,
                            onAddDomain = onAddDomain,
                            onRemoveDomain = onRemoveDomain,
                            onAssignSchedule = onAssignSchedule
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: BlockingCategory,
    schedules: List<BlockingSchedule>,
    onRemoveCategory: (String) -> Unit,
    onAddDomain: (String, String) -> Unit,
    onRemoveDomain: (String, String) -> Unit,
    onAssignSchedule: (String, String) -> Unit
) {
    var domainInput by remember(category.id) { mutableStateOf("") }
    var scheduleInput by remember(category.id) { mutableStateOf("") }
    val accent = Color(android.graphics.Color.parseColor(category.colorHex))
    val currentScheduleName = schedules.firstOrNull { it.id == category.scheduleId }?.name ?: "Always on"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(accent, RoundedCornerShape(999.dp))
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        Text(category.name, fontWeight = FontWeight.SemiBold)
                        Text("${category.domains.size} sites", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { onRemoveCategory(category.id) }) {
                    Text("Remove")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    modifier = Modifier.fillMaxWidth(0.74f),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("Add a domain") }
                )
                Button(
                    onClick = {
                        val value = domainInput.trim()
                        if (value.isNotEmpty()) {
                            onAddDomain(category.id, value)
                            domainInput = ""
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Add")
                }
            }

            Text(
                "Schedule: $currentScheduleName",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = scheduleInput,
                    onValueChange = { scheduleInput = it },
                    modifier = Modifier.fillMaxWidth(0.74f),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("Schedule name") }
                )
                Button(
                    onClick = {
                        val value = scheduleInput.trim()
                        if (value.isNotEmpty()) {
                            onAssignSchedule(category.id, value)
                            scheduleInput = ""
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Assign")
                }
            }

            if (category.domains.isEmpty()) {
                Text("No sites yet", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    category.domains.forEach { domain ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(domain, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onRemoveDomain(category.id, domain) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleSectionEditor(
    schedules: List<BlockingSchedule>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemoveSchedule: (String) -> Unit,
    onUpdateSchedule: (String, String, Set<Int>, Int, Int, Boolean) -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Create schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Add a new protection time window", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onSubmit,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("Add")
                }
            }

            OutlinedTextField(
                value = inputValue,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("e.g. Work Hours") }
            )

            if (schedules.isEmpty()) {
                Text("No schedules yet", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    schedules.forEach { schedule ->
                        EditableScheduleCard(
                            schedule = schedule,
                            onRemoveSchedule = onRemoveSchedule,
                            onUpdateSchedule = onUpdateSchedule
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulesPageContent(
    schedules: List<BlockingSchedule>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onRemoveSchedule: (String) -> Unit,
    onUpdateSchedule: (String, String, Set<Int>, Int, Int, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader(
            title = "Schedules",
            subtitle = "Set when protection rules are active",
            onBack = onBack
        )
        ScheduleSectionEditor(
            schedules = schedules,
            inputValue = inputValue,
            onInputChange = onInputChange,
            onSubmit = onSubmit,
            onRemoveSchedule = onRemoveSchedule,
            onUpdateSchedule = onUpdateSchedule
        )
    }
}

@Composable
private fun EditableScheduleCard(
    schedule: BlockingSchedule,
    onRemoveSchedule: (String) -> Unit,
    onUpdateSchedule: (String, String, Set<Int>, Int, Int, Boolean) -> Unit
) {
    var name by remember(schedule.id) { mutableStateOf(schedule.name) }
    var startText by remember(schedule.id) { mutableStateOf(formatMinute(schedule.startMinuteOfDay)) }
    var endText by remember(schedule.id) { mutableStateOf(formatMinute(schedule.endMinuteOfDay)) }
    var daysText by remember(schedule.id) { mutableStateOf(formatDaysInput(schedule.activeDays)) }
    var enabled by remember(schedule.id) { mutableStateOf(schedule.enabled) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Edit schedule", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (schedule.enabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                label = { Text("Name") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("Start") },
                    placeholder = { Text("09:00 AM") }
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("End") },
                    placeholder = { Text("05:00 PM") }
                )
            }

            OutlinedTextField(
                value = daysText,
                onValueChange = { daysText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                label = { Text("Days") },
                placeholder = { Text("Mon,Tue,Wed,Thu,Fri") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onRemoveSchedule(schedule.id) }) {
                    Text("Remove")
                }
                Button(
                    onClick = {
                        val startMinute = parseTimeInput(startText)
                        val endMinute = parseTimeInput(endText)
                        val activeDays = parseDaysInput(daysText)
                        if (startMinute != null && endMinute != null) {
                            onUpdateSchedule(
                                schedule.id,
                                name,
                                activeDays,
                                startMinute,
                                endMinute,
                                enabled
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("Save")
                }
            }

            Text(
                "${formatDays(schedule.activeDays)}  •  ${formatMinute(schedule.startMinuteOfDay)} - ${formatMinute(schedule.endMinuteOfDay)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun parseDaysInput(input: String): Set<Int> {
    val tokens = input.split(',', ' ', ';')
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }

    val dayMap = mapOf(
        "sun" to 1,
        "sunday" to 1,
        "mon" to 2,
        "monday" to 2,
        "tue" to 3,
        "tues" to 3,
        "tuesday" to 3,
        "wed" to 4,
        "wednesday" to 4,
        "thu" to 5,
        "thur" to 5,
        "thurs" to 5,
        "thursday" to 5,
        "fri" to 6,
        "friday" to 6,
        "sat" to 7,
        "saturday" to 7
    )

    return buildSet {
        tokens.forEach { token ->
            token.toIntOrNull()?.takeIf { it in 1..7 }?.let(::add)
                ?: dayMap[token]?.let(::add)
        }
    }
}

private fun formatDaysInput(days: Set<Int>): String {
    val names = mapOf(
        1 to "Sun",
        2 to "Mon",
        3 to "Tue",
        4 to "Wed",
        5 to "Thu",
        6 to "Fri",
        7 to "Sat"
    )
    return days.sorted().joinToString(separator = ",") { names[it] ?: it.toString() }
}

private fun parseTimeInput(input: String): Int? {
    val normalized = input.trim().uppercase()
    val am = normalized.endsWith("AM")
    val pm = normalized.endsWith("PM")
    val raw = normalized.removeSuffix("AM").removeSuffix("PM").trim()
    val parts = raw.split(':')
    if (parts.size != 2) return null

    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (minute !in 0..59) return null

    val normalizedHour = when {
        am || pm -> {
            if (hour !in 1..12) return null
            when {
                hour == 12 && am -> 0
                hour == 12 && pm -> 12
                pm -> hour + 12
                else -> hour
            }
        }
        else -> {
            if (hour !in 0..23) return null
            hour
        }
    }
    return normalizedHour * 60 + minute
}

@Composable
private fun BackupSection(
    backupInput: TextFieldValue,
    backupMessage: String,
    onBackupInputChange: (TextFieldValue) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Backup & import", style = MaterialTheme.typography.titleMedium)
                    Text("Copy or restore a local ShieldFocus snapshot", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onExport,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("Export")
                }
            }

            OutlinedTextField(
                value = backupInput,
                onValueChange = onBackupInputChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                minLines = 5,
                placeholder = { Text("Paste backup JSON here") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onImport) {
                    Text("Import Backup")
                }
                if (backupMessage.isNotBlank()) {
                    Text(backupMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DecisionLogSection(
    logs: List<Decision>,
    onClear: () -> Unit
) {
    var selectedWindow by remember { mutableStateOf(ActivityWindow.Today) }
    var selectedFilter by remember { mutableStateOf(DecisionFilter.All) }
    val now = System.currentTimeMillis()
    val visibleLogs = logs
        .filter { isWithinActivityWindow(it.timestampMillis, selectedWindow, now) }
        .sortedByDescending { it.timestampMillis }
    val total = visibleLogs.size
    val blocked = visibleLogs.count { !it.allow }
    val allowed = visibleLogs.count { it.allow }
    val series = buildActivitySeries(visibleLogs, selectedWindow, now)
    val chartTitle = when (selectedWindow) {
        ActivityWindow.Today -> "Blocked Requests / Hour"
        ActivityWindow.SevenDays -> "Blocked Requests / Day"
        ActivityWindow.ThirtyDays -> "Blocked Requests / Day"
    }
    val listLogs = when (selectedFilter) {
        DecisionFilter.All -> visibleLogs
        DecisionFilter.Blocked -> visibleLogs.filter { !it.allow }
        DecisionFilter.Allowed -> visibleLogs.filter { it.allow }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Activity Log", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (selectedWindow) {
                            ActivityWindow.Today -> "$total DNS decisions today"
                            ActivityWindow.SevenDays -> "$total DNS decisions in 7 days"
                            ActivityWindow.ThirtyDays -> "$total DNS decisions in 30 days"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Card(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FlashOn,
                            contentDescription = null,
                            tint = Color(0xFF0F9D58)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityWindowChip(selected = selectedWindow == ActivityWindow.Today, label = "Today") { selectedWindow = ActivityWindow.Today }
                ActivityWindowChip(selected = selectedWindow == ActivityWindow.SevenDays, label = "7 Days") { selectedWindow = ActivityWindow.SevenDays }
                ActivityWindowChip(selected = selectedWindow == ActivityWindow.ThirtyDays, label = "30 Days") { selectedWindow = ActivityWindow.ThirtyDays }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(chartTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ActivityLineChart(points = series)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Daily ▾", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActivitySummaryCard(modifier = Modifier.weight(1f), value = blocked.toString(), label = "Blocked\nToday", icon = Icons.Outlined.Block, accent = Color(0xFFFEE2E2), accentText = Color(0xFFE53935))
                ActivitySummaryCard(modifier = Modifier.weight(1f), value = allowed.toString(), label = "Allowed\nToday", icon = Icons.Outlined.CheckCircleOutline, accent = Color(0xFFD1FAE5), accentText = Color(0xFF0F9D58))
                ActivitySummaryCard(modifier = Modifier.weight(1f), value = total.toString(), label = "Total DNS\nToday", icon = Icons.Outlined.BarChart, accent = Color(0xFFF3F4F6), accentText = Color(0xFF1F2937))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Decisions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecisionFilterChip(selected = selectedFilter == DecisionFilter.All, label = "All ($total)") { selectedFilter = DecisionFilter.All }
                    DecisionFilterChip(selected = selectedFilter == DecisionFilter.Blocked, label = "Blocked ($blocked)") { selectedFilter = DecisionFilter.Blocked }
                    DecisionFilterChip(selected = selectedFilter == DecisionFilter.Allowed, label = "Allowed ($allowed)") { selectedFilter = DecisionFilter.Allowed }
                }
            }

            if (listLogs.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        "No recent decisions yet",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listLogs.take(8).forEach { decision ->
                        ActivityDecisionRow(decision = decision)
                    }
                }
            }

            if (logs.isNotEmpty()) {
                TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) {
                    Text("Clear")
                }
            }
    }
}

@Composable
private fun ActivityWindowChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) Color(0xFF1D7A4A) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun DecisionFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF1D7A4A) else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ActivitySummaryCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    accentText: Color
) {
    Card(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accentText)
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = accent)) {
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = accentText, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityLineChart(points: List<ActivityPoint>) {
    val lineColor = Color(0xFF1D7A4A)
    val dotColor = Color(0xFF1D7A4A)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        if (points.isEmpty()) return@Canvas
        val maxValue = points.maxOf { it.value }.coerceAtLeast(1)
        val stepX = if (points.size <= 1) size.width else size.width / (points.size - 1)
        val chartTop = 18f
        val chartBottom = size.height - 24f
        val usableHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val mapped = points.mapIndexed { index, point ->
            val x = index * stepX
            val y = chartBottom - (point.value.toFloat() / maxValue.toFloat()) * usableHeight
            x to y
        }

        val gridSteps = listOf(0.0f, 0.33f, 0.66f, 1.0f)
        gridSteps.forEach { fraction ->
            val y = chartBottom - usableHeight * fraction
            drawLine(
                color = Color(0xFFE5E7EB),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        for (i in 0 until mapped.size - 1) {
            val start = mapped[i]
            val end = mapped[i + 1]
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(start.first, start.second),
                end = androidx.compose.ui.geometry.Offset(end.first, end.second),
                strokeWidth = 4f
            )
        }

        mapped.forEach { point ->
            drawCircle(
                color = dotColor,
                radius = 5f,
                center = androidx.compose.ui.geometry.Offset(point.first, point.second)
            )
        }

        val labelPositions = points.mapIndexedNotNull { index, point ->
            if (point.label.isNotBlank()) index to point.label else null
        }
        labelPositions.forEach { (index, label) ->
            val x = index * stepX
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    label,
                    x,
                    size.height - 2f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#6B7280")
                        textSize = 24f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

private data class ActivityPoint(val label: String, val value: Int)

private enum class ActivityWindow {
    Today,
    SevenDays,
    ThirtyDays
}

private enum class DecisionFilter {
    All,
    Blocked,
    Allowed
}

private fun buildActivitySeries(
    logs: List<Decision>,
    window: ActivityWindow,
    nowMillis: Long
): List<ActivityPoint> {
    return when (window) {
        ActivityWindow.Today -> {
            val buckets = (6..18).map { hour ->
                val count = logs.count { decision ->
                    if (!isSameDay(decision.timestampMillis, nowMillis)) return@count false
                    val cal = Calendar.getInstance().apply { timeInMillis = decision.timestampMillis }
                    cal.get(Calendar.HOUR_OF_DAY) == hour && !decision.allow
                }
                ActivityPoint(label = when (hour) {
                    6 -> "6am"
                    9 -> "9am"
                    12 -> "12pm"
                    15 -> "3pm"
                    18 -> "6pm"
                    else -> ""
                }, value = count)
            }
            buckets
        }
        ActivityWindow.SevenDays -> {
            (6 downTo 0).map { daysAgo ->
                val start = nowMillis - daysAgo * 24L * 60L * 60L * 1000L
                val end = start + 24L * 60L * 60L * 1000L
                val count = logs.count { !it.allow && it.timestampMillis in start until end }
                val label = when (daysAgo) {
                    6 -> "Mon"
                    5 -> "Tue"
                    4 -> "Wed"
                    3 -> "Thu"
                    2 -> "Fri"
                    1 -> "Sat"
                    else -> "Sun"
                }
                ActivityPoint(label, count)
            }
        }
        ActivityWindow.ThirtyDays -> {
            val step = 5
            (5 downTo 0).map { bucket ->
                val end = nowMillis - bucket * step * 24L * 60L * 60L * 1000L
                val start = end - step * 24L * 60L * 60L * 1000L
                val count = logs.count { !it.allow && it.timestampMillis in start until end }
                ActivityPoint(label = if (bucket == 0) "Now" else "${bucket * step}d", value = count)
            }
        }
    }
}

private fun isWithinActivityWindow(timestampMillis: Long, window: ActivityWindow, nowMillis: Long): Boolean {
    return when (window) {
        ActivityWindow.Today -> isSameDay(timestampMillis, nowMillis)
        ActivityWindow.SevenDays -> timestampMillis >= nowMillis - 7L * 24L * 60L * 60L * 1000L
        ActivityWindow.ThirtyDays -> timestampMillis >= nowMillis - 30L * 24L * 60L * 60L * 1000L
    }
}

private fun formatDays(days: Set<Int>): String {
    val names = mapOf(
        1 to "Sun",
        2 to "Mon",
        3 to "Tue",
        4 to "Wed",
        5 to "Thu",
        6 to "Fri",
        7 to "Sat"
    )
    return days.sorted().joinToString(separator = ", ") { names[it] ?: it.toString() }
}

private fun formatMinute(minuteOfDay: Int): String {
    val hours = (minuteOfDay / 60).coerceIn(0, 23)
    val minutes = (minuteOfDay % 60).coerceIn(0, 59)
    val suffix = if (hours >= 12) "PM" else "AM"
    val displayHour = when {
        hours == 0 -> 12
        hours > 12 -> hours - 12
        else -> hours
    }
    return "%02d:%02d %s".format(displayHour, minutes, suffix)
}

@Composable
private fun ActivityDecisionRow(decision: Decision) {
    val statusColor = if (decision.allow) Color(0xFF15805B) else Color(0xFFEF3038)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().width(3.dp).background(statusColor))
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    shape = RoundedCornerShape(9.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (decision.allow) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                    )
                ) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (decision.allow) Icons.Outlined.Edit else Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(decision.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        decisionSubtitle(decision),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (decision.allow) Color(0xFFD1FAE5) else Color(0xFFFFDEDF)
                        )
                    ) {
                        Text(
                            if (decision.allow) "Allowed" else "Blocked",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                    Text(
                        formatDecisionTime(decision.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun decisionSubtitle(decision: Decision): String {
    val reason = decision.reason.ifBlank { if (decision.allow) "Allowlist" else "Default blocklist" }
    val category = when {
        decision.domain.contains("doubleclick", true) || decision.domain.contains("google", true) -> "Advertising"
        decision.domain.contains("github", true) || decision.domain.contains("stackoverflow", true) -> "Development"
        decision.domain.contains("snapchat", true) || decision.domain.contains("hotjar", true) -> "Tracking"
        else -> if (decision.allow) "Development" else "Blocked request"
    }
    return "$category  ·  $reason"
}

private fun formatDecisionTime(timestampMillis: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(timestampMillis))
        .lowercase()
