package com.shieldfocus.android.model

data class BlockingCategory(
    val id: String,
    val name: String,
    val colorHex: String,
    val scheduleId: String = "",
    val domains: List<String> = emptyList(),
    val enabled: Boolean = true
)
