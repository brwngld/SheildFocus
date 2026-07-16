package com.shieldfocus.android.model

data class BlockingRule(
    val id: String,
    val domain: String,
    val categoryId: String = "",
    val scheduleId: String = "",
    val enabled: Boolean = true
)
