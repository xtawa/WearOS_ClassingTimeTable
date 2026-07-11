package com.classing.shared.sync

/** A Lamport-style version. Ordering never depends on device clock accuracy. */
data class LogicalVersion(
    val counter: Long,
    val deviceId: String,
    val changedAt: Long,
) : Comparable<LogicalVersion> {
    init {
        require(counter >= 0L)
        require(deviceId.isNotBlank())
    }

    override fun compareTo(other: LogicalVersion): Int {
        return compareValuesBy(this, other, LogicalVersion::counter, LogicalVersion::deviceId)
    }
}

data class VersionedRecord(
    val id: String,
    val payload: String?,
    val version: LogicalVersion,
    val deletedAt: Long? = null,
    val recoverableUntil: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(payload != null || deletedAt != null)
    }

    val isDeleted: Boolean get() = deletedAt != null

    fun compact(now: Long): VersionedRecord {
        return if (isDeleted && recoverableUntil != null && recoverableUntil <= now) {
            copy(payload = null, recoverableUntil = null)
        } else {
            this
        }
    }
}

data class SyncChangeLogEntry(
    val id: String,
    val domain: String,
    val recordId: String,
    val action: String,
    val version: LogicalVersion,
    val occurredAt: Long,
    val detail: String = "",
)

data class DeviceSyncMetadata(
    val deviceId: String,
    val lastCounter: Long,
    val lastChangedAt: Long,
)

data class CloudSyncDocumentV2(
    val records: Map<String, Map<String, VersionedRecord>> = emptyMap(),
    val changes: List<SyncChangeLogEntry> = emptyList(),
    val devices: Map<String, DeviceSyncMetadata> = emptyMap(),
    val updatedAt: Long = 0L,
) {
    fun compact(now: Long, maxChanges: Int = 100): CloudSyncDocumentV2 {
        val compacted = records.mapValues { (_, domainRecords) ->
            domainRecords.mapValues { (_, record) -> record.compact(now) }
        }
        return copy(
            records = compacted,
            changes = changes.sortedByDescending { it.occurredAt }.take(maxChanges),
        )
    }
}

data class CloudMergeResult(
    val document: CloudSyncDocumentV2,
    val conflicts: Int,
    val changed: Boolean,
)

object CloudSyncV2Merger {
    fun merge(
        left: CloudSyncDocumentV2,
        right: CloudSyncDocumentV2,
        now: Long,
    ): CloudMergeResult {
        var conflicts = 0
        val domains = left.records.keys + right.records.keys
        val mergedRecords = domains.associateWith { domain ->
            val leftRecords = left.records[domain].orEmpty()
            val rightRecords = right.records[domain].orEmpty()
            (leftRecords.keys + rightRecords.keys).associateWith { id ->
                val a = leftRecords[id]
                val b = rightRecords[id]
                when {
                    a == null -> b!!
                    b == null -> a
                    a == b -> a
                    else -> {
                        conflicts += 1
                        selectWinner(a, b)
                    }
                }
            }
        }
        val mergedChanges = (left.changes + right.changes)
            .associateBy { it.id }
            .values
            .sortedWith(compareByDescending<SyncChangeLogEntry> { it.occurredAt }.thenByDescending { it.id })
        val mergedDevices = (left.devices.keys + right.devices.keys).associateWith { id ->
            val a = left.devices[id]
            val b = right.devices[id]
            when {
                a == null -> b!!
                b == null -> a
                a.lastCounter != b.lastCounter -> if (a.lastCounter > b.lastCounter) a else b
                else -> if (a.lastChangedAt >= b.lastChangedAt) a else b
            }
        }
        val merged = CloudSyncDocumentV2(
            records = mergedRecords,
            changes = mergedChanges,
            devices = mergedDevices,
            updatedAt = maxOf(left.updatedAt, right.updatedAt),
        ).compact(now)
        return CloudMergeResult(
            document = merged,
            conflicts = conflicts,
            changed = merged != left,
        )
    }

    private fun selectWinner(a: VersionedRecord, b: VersionedRecord): VersionedRecord {
        val versionComparison = a.version.compareTo(b.version)
        if (versionComparison != 0) return if (versionComparison > 0) a else b

        // Defensive deterministic fallback for malformed documents containing the same stamp twice.
        if (a.isDeleted != b.isDeleted) return if (a.isDeleted) a else b
        val payloadComparison = a.payload.orEmpty().compareTo(b.payload.orEmpty())
        return if (payloadComparison >= 0) a else b
    }
}

object CloudSyncV2 {
    const val DOCUMENT_FORMAT = "classing_cloud_sync_v2"
    const val TOMBSTONE_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

    const val DOMAIN_TIMETABLE_LESSONS = "timetable.lessons"
    const val DOMAIN_TIMETABLE_EXCEPTIONS = "timetable.exceptions"
    const val DOMAIN_MOBILE_SETTINGS = "mobile.settings"
    const val DOMAIN_WEAR_SETTINGS = "wear.settings"
    const val DOMAIN_CLOUD_CONFIG = "cloud.config"
    const val DOMAIN_APP_COMMANDS = "app.commands"
}
