package com.shieldfocus.android.model

data class ProtectionSettings(
    val enabled: Boolean = true,
    val strictMode: Boolean = true,
    val redirectDelaySeconds: Int = 5,
    val autoStartOnBoot: Boolean = false,
    val loggingEnabled: Boolean = true
)
