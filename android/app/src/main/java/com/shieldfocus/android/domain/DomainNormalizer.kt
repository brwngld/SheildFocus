package com.shieldfocus.android.domain

object DomainNormalizer {
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""

        var value = input.trim().lowercase()
        value = value
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
            .trim('.')

        if (value.startsWith("www.")) {
            value = value.removePrefix("www.")
        }

        return when {
            value.isBlank() -> ""
            value == "localhost" -> value
            "." !in value -> ""
            else -> value
        }
    }
}
