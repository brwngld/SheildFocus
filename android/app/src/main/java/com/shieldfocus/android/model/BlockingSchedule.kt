package com.shieldfocus.android.model

import java.util.Calendar

data class BlockingSchedule(
    val id: String,
    val name: String,
    val activeDays: Set<Int>,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val enabled: Boolean = true
) {
    fun isActiveAt(timestampMillis: Long): Boolean {
        if (!enabled || activeDays.isEmpty()) return false

        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
        }

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val activeToday = dayOfWeek in activeDays

        return if (startMinuteOfDay <= endMinuteOfDay) {
            activeToday && minuteOfDay in startMinuteOfDay..endMinuteOfDay
        } else {
            activeToday && (minuteOfDay >= startMinuteOfDay || minuteOfDay <= endMinuteOfDay)
        }
    }
}
