package com.classing.shared.sync

enum class SyncDomain {
    TIMETABLE,
    MOBILE_SETTINGS,
    WEAR_SETTINGS,
}

enum class SyncSource(val wireValue: String) {
    PHONE_DIRECT("PHONE_DIRECT"),
    PHONE_LOCAL("PHONE_LOCAL"),
    WEAR_LOCAL("WEAR_LOCAL"),
    CLOUD_PULL("CLOUD_PULL"),
    UNKNOWN("UNKNOWN"),
    ;

    companion object {
        fun fromWire(raw: String?): SyncSource {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return when (normalized) {
                "PHONE_DIRECT", "WEARABLE_API", "WEAROS_APP" -> PHONE_DIRECT
                "PHONE_LOCAL" -> PHONE_LOCAL
                "WEAR_LOCAL" -> WEAR_LOCAL
                "CLOUD_PULL", "CLOUD_SYNC", "SOURCE_CLOUD_SYNC" -> CLOUD_PULL
                "", "UNKNOWN" -> UNKNOWN
                else -> UNKNOWN
            }
        }
    }
}

data class SyncStamp(
    val revision: Long,
    val source: SyncSource,
    val appliedAt: Long,
)

object SyncArbitrator {
    fun shouldApply(
        domain: SyncDomain,
        incoming: SyncStamp,
        current: SyncStamp?,
    ): Boolean {
        if (current == null) return true

        if (incoming.revision != current.revision) {
            return incoming.revision > current.revision
        }

        val incomingPriority = sourcePriority(domain, incoming.source)
        val currentPriority = sourcePriority(domain, current.source)
        if (incomingPriority != currentPriority) {
            return incomingPriority > currentPriority
        }

        if (incoming.appliedAt != current.appliedAt) {
            return incoming.appliedAt > current.appliedAt
        }

        // Same revision + source + appliedAt is considered a duplicate.
        return false
    }

    private fun sourcePriority(domain: SyncDomain, source: SyncSource): Int {
        return when (domain) {
            SyncDomain.TIMETABLE -> when (source) {
                SyncSource.PHONE_DIRECT -> 3
                SyncSource.CLOUD_PULL -> 2
                SyncSource.WEAR_LOCAL -> 1
                SyncSource.PHONE_LOCAL -> 1
                SyncSource.UNKNOWN -> 0
            }

            SyncDomain.MOBILE_SETTINGS -> when (source) {
                SyncSource.PHONE_LOCAL -> 3
                SyncSource.CLOUD_PULL -> 2
                SyncSource.PHONE_DIRECT -> 1
                SyncSource.WEAR_LOCAL -> 1
                SyncSource.UNKNOWN -> 0
            }

            SyncDomain.WEAR_SETTINGS -> when (source) {
                SyncSource.WEAR_LOCAL -> 3
                SyncSource.CLOUD_PULL -> 2
                SyncSource.PHONE_DIRECT -> 1
                SyncSource.PHONE_LOCAL -> 1
                SyncSource.UNKNOWN -> 0
            }
        }
    }
}
