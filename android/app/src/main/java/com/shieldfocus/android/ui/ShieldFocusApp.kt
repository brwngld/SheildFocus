package com.shieldfocus.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import com.shieldfocus.android.model.BlockingCategory
import com.shieldfocus.android.model.BlockingSchedule
import com.shieldfocus.android.model.Decision
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldFocusApp(
    protectionEnabled: Boolean,
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
    val clipboardManager = LocalClipboardManager.current

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
                onToggle = onProtectionToggle
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
                caption = "The Android app shares the same rule concepts as the desktop extension."
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
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Startup & logging", style = MaterialTheme.typography.titleMedium)

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

            CategorySection(
                categories = categories,
                schedules = schedules,
                inputValue = categoryInput,
                onInputChange = { categoryInput = it },
                onSubmit = {
                    val value = categoryInput.trim()
                    if (value.isNotEmpty()) {
                        onAddCategory(value)
                        categoryInput = ""
                    }
                },
                onRemoveCategory = onRemoveCategory,
                onAddDomain = onAddDomainToCategory,
                onRemoveDomain = onRemoveDomainFromCategory,
                onAssignSchedule = onAssignScheduleToCategory
            )

            ScheduleSection(
                schedules = schedules,
                inputValue = scheduleInput,
                onInputChange = { scheduleInput = it },
                onSubmit = {
                    val value = scheduleInput.trim()
                    if (value.isNotEmpty()) {
                        onAddSchedule(value)
                        scheduleInput = ""
                    }
                },
                onRemoveSchedule = onRemoveSchedule
            )

            BackupSection(
                backupInput = backupInput,
                backupMessage = backupMessage,
                onBackupInputChange = { backupInput = it },
                onExport = {
                    clipboardManager.setText(AnnotatedString(onExportBackup()))
                    backupMessage = "Backup copied to clipboard."
                },
                onImport = {
                    val imported = onImportBackup(backupInput.text)
                    backupMessage = if (imported) {
                        backupInput = TextFieldValue("")
                        "Backup imported successfully."
                    } else {
                        "Backup import failed."
                    }
                }
            )

            DomainSection(
                title = "Block list",
                subtitle = "Domains blocked on-device",
                inputValue = blockedInput,
                placeholder = "e.g. reddit.com",
                buttonLabel = "Add",
                domains = blockedDomains,
                onInputChange = { blockedInput = it },
                onSubmit = {
                    val value = blockedInput.trim()
                    if (value.isNotEmpty()) {
                        onAddBlockedDomain(value)
                        blockedInput = ""
                    }
                },
                onRemove = onRemoveBlockedDomain
            )

            DomainSection(
                title = "Allow list",
                subtitle = "Domains that always stay accessible",
                inputValue = allowedInput,
                placeholder = "e.g. educational-site.org",
                buttonLabel = "Allow",
                domains = allowedDomains,
                onInputChange = { allowedInput = it },
                onSubmit = {
                    val value = allowedInput.trim()
                    if (value.isNotEmpty()) {
                        onAddAllowedDomain(value)
                        allowedInput = ""
                    }
                },
                onRemove = onRemoveAllowedDomain
            )

            DecisionLogSection(
                logs = decisionLogs,
                onClear = onClearDecisionLogs
            )
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
private fun ScheduleSection(
    schedules: List<BlockingSchedule>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemoveSchedule: (String) -> Unit
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
                    Text("Schedules", style = MaterialTheme.typography.titleMedium)
                    Text("Time windows that activate category blocks", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onSubmit,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("New Schedule")
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
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(schedule.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${formatDays(schedule.activeDays)}  •  ${formatMinute(schedule.startMinuteOfDay)} - ${formatMinute(schedule.endMinuteOfDay)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    TextButton(onClick = { onRemoveSchedule(schedule.id) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
                    Text("Recent decisions", style = MaterialTheme.typography.titleMedium)
                    Text("Latest local block and allow events", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }

            if (logs.isEmpty()) {
                Text("No recent decisions yet", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    logs.take(5).forEach { decision ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        decision.domain,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (decision.allow) "Allowed" else "Blocked",
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(decision.reason, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(decision.timestampMillis)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
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
