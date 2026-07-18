package com.shieldfocus.android.model

data class ProtectionSettings(
    val enabled: Boolean = true,
    val strictMode: Boolean = true,
    val ipv4DnsEnabled: Boolean = true,
    val ipv6DnsEnabled: Boolean = true,
    val dnsTimeoutMillis: Int = 2_000,
    val redirectDelaySeconds: Int = 5,
    val autoStartOnBoot: Boolean = false,
    val restartAfterInterruption: Boolean = true,
    val loggingEnabled: Boolean = true,
    val activityRetentionDays: Int = 30,
    val hideSensitiveDomains: Boolean = false
)
