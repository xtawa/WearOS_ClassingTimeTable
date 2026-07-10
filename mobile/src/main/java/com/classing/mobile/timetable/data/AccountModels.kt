package com.xtawa.classingtime.data

enum class SyncScope {
    TIMETABLE,
    MOBILE_SETTINGS,
    WEAR_SETTINGS,
    ;

    companion object {
        fun fromRaw(raw: String?): SyncScope? {
            return entries.firstOrNull { it.name == raw?.trim()?.uppercase() }
        }
    }
}

enum class OfficialSyncFrequency(val intervalMinutes: Long?) {
    MANUAL_ONLY(null),
    EVERY_15_MIN(15L),
    EVERY_30_MIN(30L),
    EVERY_1_HOUR(60L),
    EVERY_3_HOURS(180L),
    ;

    companion object {
        fun fromRaw(raw: String?): OfficialSyncFrequency {
            return entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: MANUAL_ONLY
        }
    }
}

enum class DailyBriefingChannel {
    APP_NOTIFICATION,
    EMAIL,
    BOTH,
    ;

    companion object {
        fun fromRaw(raw: String?): DailyBriefingChannel {
            return entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: APP_NOTIFICATION
        }
    }
}

data class AccountSummary(
    val userId: String = "",
    val identifier: String = "",
    val username: String = "",
    val email: String = "",
)

data class MembershipSummary(
    val isMember: Boolean = false,
    val tier: String = "FREE",
    val expiresAt: Long = 0L,
    val lastCheckedAt: Long = 0L,
)
