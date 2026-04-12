package com.xtawa.classingtime.reminder

enum class KeepAliveLevel {
    ECO,
    BALANCED,
    AGGRESSIVE,
    ;

    companion object {
        fun fromRaw(raw: String?): KeepAliveLevel {
            return entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: BALANCED
        }
    }
}
