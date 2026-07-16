package com.shieldfocus.android.model

data class Decision(
    val domain: String,
    val allow: Boolean,
    val reason: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
