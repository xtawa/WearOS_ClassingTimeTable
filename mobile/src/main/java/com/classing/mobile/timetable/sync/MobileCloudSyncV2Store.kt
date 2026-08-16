package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.model.MAX_SCHEDULE_WEEK
import com.classing.shared.model.MIN_SCHEDULE_WEEK
import com.classing.shared.sync.CloudSyncDocumentV2
import com.classing.shared.sync.CloudSyncV2
import com.classing.shared.sync.DeviceSyncMetadata
import com.classing.shared.sync.LogicalVersion
import com.classing.shared.sync.SyncChangeLogEntry
import com.classing.shared.sync.VersionedRecord
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.data.DailyBriefingChannel
import com.xtawa.classingtime.data.OfficialSyncFrequency
import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.data.PersistedScheduleException
import com.xtawa.classingtime.data.SyncScope
import com.xtawa.classingtime.reminder.KeepAliveLevel
import com.xtawa.classingtime.reminder.DailyBriefingScheduler
import com.xtawa.classingtime.reminder.ReminderScheduler
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object MobileCloudSyncV2Store {
    private const val PREF_NAME = "mobile_cloud_sync_v2_state"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_COUNTER = "logical_counter"
    private const val KEY_DOCUMENT = "local_document"
    private const val KEY_PROCESSED_COMMANDS = "processed_commands"
    private const val COMMAND_MAX_AGE_MS = 10L * 60L * 1000L

    fun deviceId(context: Context): String {
        val prefs = prefs(context)
        return prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).commit()
        }
    }

    fun loadDocument(context: Context): CloudSyncDocumentV2 {
        val raw = prefs(context).getString(KEY_DOCUMENT, null) ?: return CloudSyncDocumentV2()
        return runCatching { MobileCloudSyncV2Json.fromJson(JSONObject(raw)) }.getOrDefault(CloudSyncDocumentV2())
    }

    fun hasLocalBaseline(context: Context): Boolean = prefs(context).contains(KEY_DOCUMENT)

    fun saveDocument(context: Context, document: CloudSyncDocumentV2) {
        val maxCounter = document.records.values.flatMap { it.values }.maxOfOrNull { it.version.counter } ?: 0L
        val p = prefs(context)
        p.edit()
            .putString(KEY_DOCUMENT, MobileCloudSyncV2Json.toJson(document).toString())
            .putLong(KEY_COUNTER, maxOf(p.getLong(KEY_COUNTER, 0L), maxCounter))
            .commit()
    }

    fun captureLocal(
        context: Context,
        now: Long = System.currentTimeMillis(),
        syncScopes: Set<SyncScope>? = null,
    ): CloudSyncDocumentV2 {
        val cached = loadDocument(context)
        val state = MobilePrefsStore.loadTimetableState(context)
        val settings = MobilePrefsStore.loadSettings(context)
        val effectiveScopes = syncScopes ?: settings.syncScopes
        val domains = cached.records.toMutableMap()
        val changes = cached.changes.toMutableList()
        if (effectiveScopes.contains(SyncScope.TIMETABLE)) {
            domains[CloudSyncV2.DOMAIN_TIMETABLE_LESSONS] = reconcileDomain(
                context, CloudSyncV2.DOMAIN_TIMETABLE_LESSONS,
                state.baseLessons.associate { it.id to lessonToJson(it).toString() },
                cached.records[CloudSyncV2.DOMAIN_TIMETABLE_LESSONS].orEmpty(), now, changes,
            )
            domains[CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS] = reconcileDomain(
                context, CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS,
                state.exceptions.associate { it.id to exceptionToJson(it).toString() },
                cached.records[CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS].orEmpty(), now, changes,
            )
        }
        if (effectiveScopes.contains(SyncScope.MOBILE_SETTINGS)) {
            domains[CloudSyncV2.DOMAIN_MOBILE_SETTINGS] = reconcileDomain(
                context, CloudSyncV2.DOMAIN_MOBILE_SETTINGS, mobileSettingValues(settings),
                cached.records[CloudSyncV2.DOMAIN_MOBILE_SETTINGS].orEmpty(), now, changes,
            )
            domains[CloudSyncV2.DOMAIN_CLOUD_CONFIG] = reconcileDomain(
                context, CloudSyncV2.DOMAIN_CLOUD_CONFIG, cloudConfigValues(settings),
                cached.records[CloudSyncV2.DOMAIN_CLOUD_CONFIG].orEmpty(), now, changes,
            )
        }
        val wearValues = MobilePrefsStore.loadWearSettingsSnapshot(context)?.first?.let { raw ->
            runCatching { jsonObjectValues(JSONObject(raw)) }.getOrDefault(emptyMap())
        }.orEmpty()
        if (effectiveScopes.contains(SyncScope.WEAR_SETTINGS)) {
            domains[CloudSyncV2.DOMAIN_WEAR_SETTINGS] = reconcileDomain(
                context, CloudSyncV2.DOMAIN_WEAR_SETTINGS, wearValues,
                cached.records[CloudSyncV2.DOMAIN_WEAR_SETTINGS].orEmpty(), now, changes,
            )
        }
        val changed = domains != cached.records || changes != cached.changes
        val device = deviceId(context)
        val devices = if (changed || device !in cached.devices) {
            cached.devices + (device to DeviceSyncMetadata(
                deviceId = device,
                lastCounter = prefs(context).getLong(KEY_COUNTER, 0L),
                lastChangedAt = if (changed) now else cached.updatedAt,
            ))
        } else {
            cached.devices
        }
        return CloudSyncDocumentV2(
            records = domains,
            changes = changes,
            devices = devices,
            updatedAt = if (changed) now else cached.updatedAt,
        ).compact(now)
    }

    fun migrateV1(context: Context, legacy: CloudDocument, now: Long): CloudSyncDocumentV2 {
        fun version(revision: Long, source: String) = LogicalVersion(
            counter = revision.coerceAtLeast(1L),
            deviceId = "legacy-${source.ifBlank { "unknown" }}",
            changedAt = revision.takeIf { it > 0L } ?: now,
        )
        val domains = mutableMapOf<String, Map<String, VersionedRecord>>()
        legacy.timetable?.let { table ->
            val stamp = version(table.revision, table.source.wireValue.lowercase())
            domains[CloudSyncV2.DOMAIN_TIMETABLE_LESSONS] = (table.baseLessons ?: table.lessons).associate { item ->
                item.id to VersionedRecord(item.id, lessonToJson(item).toString(), stamp)
            }
            domains[CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS] = table.exceptions.associate { item ->
                item.id to VersionedRecord(item.id, exceptionToJson(item).toString(), stamp)
            }
        }
        legacy.mobileSettings?.let { snapshot ->
            val stamp = version(snapshot.revision, snapshot.source.wireValue.lowercase())
            domains[CloudSyncV2.DOMAIN_MOBILE_SETTINGS] = jsonObjectValues(snapshot.settings).mapValues { (id, payload) ->
                VersionedRecord(id, payload, stamp)
            }
        }
        legacy.wearSettings?.let { snapshot ->
            val stamp = version(snapshot.revision, snapshot.source.wireValue.lowercase())
            domains[CloudSyncV2.DOMAIN_WEAR_SETTINGS] = jsonObjectValues(snapshot.settings).mapValues { (id, payload) ->
                VersionedRecord(id, payload, stamp)
            }
        }
        return CloudSyncDocumentV2(records = domains, updatedAt = now)
    }

    fun applyMerged(
        context: Context,
        document: CloudSyncDocumentV2,
        syncScopes: Set<SyncScope>? = null,
    ) {
        // Save first so persistence callbacks cannot reinterpret remote data as a new local edit.
        saveDocument(context, document)
        consumeAppCommands(context, document)
        val currentState = MobilePrefsStore.loadTimetableState(context)
        var settings = MobilePrefsStore.loadSettings(context)
        val effectiveScopes = syncScopes ?: settings.syncScopes
        if (effectiveScopes.contains(SyncScope.TIMETABLE)) {
            val lessons = livePayloads(document, CloudSyncV2.DOMAIN_TIMETABLE_LESSONS)
                .mapNotNull { runCatching { lessonFromJson(JSONObject(it)) }.getOrNull() }
            val exceptions = livePayloads(document, CloudSyncV2.DOMAIN_TIMETABLE_EXCEPTIONS)
                .mapNotNull { runCatching { exceptionFromJson(JSONObject(it)) }.getOrNull() }
            MobilePrefsStore.saveTimetableState(context, lessons, exceptions, currentState.snapshots)
        }
        if (effectiveScopes.contains(SyncScope.MOBILE_SETTINGS)) {
            val mobile = liveSettingValues(document, CloudSyncV2.DOMAIN_MOBILE_SETTINGS)
            val cloud = liveSettingValues(document, CloudSyncV2.DOMAIN_CLOUD_CONFIG)
            settings = settings.copy(
                showWeekend = mobile.boolean("showWeekend", settings.showWeekend),
                reminderEnabled = mobile.boolean("reminderEnabled", settings.reminderEnabled),
                reminderMinutes = mobile.int("reminderMinutes", settings.reminderMinutes),
                keepAliveLevel = mobile.string("keepAliveLevel", settings.keepAliveLevel),
                rawIcs = mobile.string("rawIcs", settings.rawIcs),
                wearSyncMode = mobile.string("wearSyncMode", settings.wearSyncMode),
                weekNumberMode = mobile.string("weekNumberMode", settings.weekNumberMode),
                semesterWeekStartDate = mobile.string("semesterWeekStartDate", settings.semesterWeekStartDate),
                weekStartDay = mobile.string("weekStartDay", settings.weekStartDay),
                dailyBriefingEnabled = mobile.boolean("dailyBriefingEnabled", settings.dailyBriefingEnabled),
                dailyBriefingChannel = DailyBriefingChannel.fromRaw(
                    mobile.string("dailyBriefingChannel", settings.dailyBriefingChannel.name),
                ),
                dailyBriefingTime = mobile.string("dailyBriefingTime", settings.dailyBriefingTime),
                cloudProvider = cloud.string("cloudProvider", settings.cloudProvider),
                cloudSyncEnabled = cloud.boolean("cloudSyncEnabled", settings.cloudSyncEnabled),
                cloudServerUrl = cloud.string("cloudServerUrl", settings.cloudServerUrl),
                cloudRemotePath = cloud.string("cloudRemotePath", settings.cloudRemotePath),
                cloudUsername = cloud.string("cloudUsername", settings.cloudUsername),
                cloudDriveFileName = cloud.string("cloudDriveFileName", settings.cloudDriveFileName),
                officialSyncFrequency = OfficialSyncFrequency.fromRaw(
                    cloud.string("officialSyncFrequency", settings.officialSyncFrequency.name),
                ),
                syncScopes = cloud.syncScopes("syncScopes", settings.syncScopes),
            )
        }
        MobilePrefsStore.saveSettings(context, settings)
        ReminderScheduler.sync(
            context = context,
            enabled = settings.reminderEnabled,
            keepAliveLevel = KeepAliveLevel.fromRaw(settings.keepAliveLevel),
            reminderMinutes = settings.reminderMinutes,
        )
        DailyBriefingScheduler.sync(context, settings)
        if (effectiveScopes.contains(SyncScope.WEAR_SETTINGS)) {
            val wear = liveSettingValues(document, CloudSyncV2.DOMAIN_WEAR_SETTINGS)
            if (wear.isNotEmpty()) {
                val snapshot = JSONObject()
                wear.forEach { (key, value) -> snapshot.put(key, value) }
                val revision = document.records[CloudSyncV2.DOMAIN_WEAR_SETTINGS].orEmpty().values
                    .maxOfOrNull { it.version.counter } ?: 0L
                MobilePrefsStore.saveWearSettingsSnapshot(context, snapshot.toString(), revision)
            }
        }
    }

    fun restore(context: Context, domain: String, recordId: String, now: Long = System.currentTimeMillis()): Boolean {
        val document = loadDocument(context)
        val existing = document.records[domain]?.get(recordId) ?: return false
        if (!existing.isDeleted || existing.payload == null || (existing.recoverableUntil ?: 0L) < now) return false
        val restored = existing.copy(version = nextVersion(context, now), deletedAt = null, recoverableUntil = null)
        val updatedDomain = document.records[domain].orEmpty() + (recordId to restored)
        val change = change(domain, recordId, "restored", restored.version, now)
        saveDocument(context, document.copy(
            records = document.records + (domain to updatedDomain),
            changes = listOf(change) + document.changes,
            devices = document.devices + (restored.version.deviceId to DeviceSyncMetadata(
                restored.version.deviceId,
                restored.version.counter,
                now,
            )),
            updatedAt = now,
        ))
        applyMerged(context, loadDocument(context))
        return true
    }

    fun canRestore(context: Context, domain: String, recordId: String, now: Long = System.currentTimeMillis()): Boolean {
        val record = loadDocument(context).records[domain]?.get(recordId) ?: return false
        return record.isDeleted && record.payload != null && (record.recoverableUntil ?: 0L) >= now
    }

    private fun consumeAppCommands(context: Context, document: CloudSyncDocumentV2, now: Long = System.currentTimeMillis()) {
        val prefs = prefs(context)
        val processed = prefs.getString(KEY_PROCESSED_COMMANDS, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .toMutableSet()
        var changed = false
        document.records[CloudSyncV2.DOMAIN_APP_COMMANDS].orEmpty().values
            .filterNot { it.isDeleted }
            .forEach { record ->
                if (record.id in processed) return@forEach
                val payload = runCatching { JSONObject(record.payload ?: "") }.getOrNull() ?: return@forEach
                val createdAt = payload.optLong("createdAt", record.version.changedAt)
                if (createdAt <= 0L || now - createdAt > COMMAND_MAX_AGE_MS) return@forEach
                if (payload.optString("type") == "DAILY_BRIEFING_TEST") {
                    DailyBriefingScheduler.postTestNotification(context)
                    processed += record.id
                    changed = true
                }
        }
        if (changed) {
            prefs.edit().putString(KEY_PROCESSED_COMMANDS, processed.toList().takeLast(50).joinToString(",")).apply()
        }
    }

    private fun reconcileDomain(
        context: Context,
        domain: String,
        current: Map<String, String>,
        cached: Map<String, VersionedRecord>,
        now: Long,
        changes: MutableList<SyncChangeLogEntry>,
    ): Map<String, VersionedRecord> {
        val result = cached.toMutableMap()
        current.forEach { (id, payload) ->
            val old = cached[id]
            if (old == null || old.isDeleted || old.payload != payload) {
                val version = nextVersion(context, now)
                result[id] = VersionedRecord(id, payload, version)
                changes += change(domain, id, if (old == null || old.isDeleted) "upserted" else "updated", version, now)
            }
        }
        cached.values.filter { !it.isDeleted && it.id !in current }.forEach { old ->
            val version = nextVersion(context, now)
            result[old.id] = old.copy(
                version = version,
                deletedAt = now,
                recoverableUntil = now + CloudSyncV2.TOMBSTONE_RETENTION_MS,
            )
            changes += change(domain, old.id, "deleted", version, now)
        }
        return result
    }

    private fun nextVersion(context: Context, now: Long): LogicalVersion {
        val p = prefs(context)
        // Seed once from wall time for v1 ordering, then remain strictly monotonic locally.
        val next = maxOf(p.getLong(KEY_COUNTER, 0L), now) + 1L
        p.edit().putLong(KEY_COUNTER, next).commit()
        return LogicalVersion(next, deviceId(context), now)
    }

    private fun change(domain: String, id: String, action: String, version: LogicalVersion, now: Long) = SyncChangeLogEntry(
        id = "${version.deviceId}:${version.counter}:$domain:$id:$action",
        domain = domain,
        recordId = id,
        action = action,
        version = version,
        occurredAt = now,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}

object MobileCloudSyncV2Json {
    fun toJson(document: CloudSyncDocumentV2): JSONObject {
        val records = JSONObject()
        document.records.forEach { (domain, values) ->
            val array = JSONArray()
            values.toSortedMap().values.forEach { record ->
                array.put(JSONObject()
                    .put("id", record.id)
                    .put("payload", record.payload ?: JSONObject.NULL)
                    .put("version", versionToJson(record.version))
                    .put("deletedAt", record.deletedAt ?: JSONObject.NULL)
                    .put("recoverableUntil", record.recoverableUntil ?: JSONObject.NULL))
            }
            records.put(domain, array)
        }
        val changes = JSONArray()
        document.changes.forEach { item ->
            changes.put(JSONObject()
                .put("id", item.id).put("domain", item.domain).put("recordId", item.recordId)
                .put("action", item.action).put("version", versionToJson(item.version))
                .put("occurredAt", item.occurredAt).put("detail", item.detail))
        }
        val devices = JSONArray()
        document.devices.toSortedMap().values.forEach { device ->
            devices.put(JSONObject()
                .put("deviceId", device.deviceId)
                .put("lastCounter", device.lastCounter)
                .put("lastChangedAt", device.lastChangedAt))
        }
        return JSONObject()
            .put("format", CloudSyncV2.DOCUMENT_FORMAT)
            .put("updatedAt", document.updatedAt)
            .put("records", records)
            .put("changes", changes)
            .put("devices", devices)
    }

    fun fromJson(root: JSONObject): CloudSyncDocumentV2 {
        require(root.optString("format") == CloudSyncV2.DOCUMENT_FORMAT)
        val recordsObject = root.optJSONObject("records") ?: JSONObject()
        val domains = mutableMapOf<String, Map<String, VersionedRecord>>()
        val names = recordsObject.keys()
        while (names.hasNext()) {
            val domain = names.next()
            val array = recordsObject.optJSONArray(domain) ?: continue
            val values = mutableMapOf<String, VersionedRecord>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                values[id] = VersionedRecord(
                    id = id,
                    payload = item.optStringOrNull("payload"),
                    version = versionFromJson(item.getJSONObject("version")),
                    deletedAt = item.optLongOrNull("deletedAt"),
                    recoverableUntil = item.optLongOrNull("recoverableUntil"),
                )
            }
            domains[domain] = values
        }
        val changes = buildList {
            val array = root.optJSONArray("changes") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(SyncChangeLogEntry(
                    id = item.optString("id"), domain = item.optString("domain"), recordId = item.optString("recordId"),
                    action = item.optString("action"), version = versionFromJson(item.getJSONObject("version")),
                    occurredAt = item.optLong("occurredAt"), detail = item.optString("detail"),
                ))
            }
        }
        val devices = buildMap {
            val array = root.optJSONArray("devices") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("deviceId")
                if (id.isBlank()) continue
                put(id, DeviceSyncMetadata(id, item.optLong("lastCounter"), item.optLong("lastChangedAt")))
            }
        }
        return CloudSyncDocumentV2(
            records = domains,
            changes = changes,
            devices = devices,
            updatedAt = root.optLong("updatedAt"),
        )
    }

    private fun versionToJson(version: LogicalVersion) = JSONObject()
        .put("counter", version.counter).put("deviceId", version.deviceId).put("changedAt", version.changedAt)

    private fun versionFromJson(json: JSONObject) = LogicalVersion(
        json.optLong("counter"), json.optString("deviceId"), json.optLong("changedAt"),
    )
}

private fun lessonToJson(item: PersistedLesson) = JSONObject()
    .put("id", item.id).put("title", item.title).put("teacher", item.teacher ?: JSONObject.NULL)
    .put("location", item.location ?: JSONObject.NULL).put("note", item.note ?: JSONObject.NULL)
    .put("dayOfWeek", item.dayOfWeek).put("startMinute", item.startMinute).put("endMinute", item.endMinute)
    .put("startWeek", item.startWeek).put("endWeek", item.endWeek).put("weekParity", item.weekParity)

private fun lessonFromJson(item: JSONObject): PersistedLesson? {
    val id = item.optString("id"); val title = item.optString("title")
    if (id.isBlank() || title.isBlank()) return null
    val startWeek = item.optInt("startWeek", MIN_SCHEDULE_WEEK)
        .coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK)
    return PersistedLesson(id, title, item.optStringOrNull("teacher"), item.optStringOrNull("location"),
        item.optStringOrNull("note"), item.optInt("dayOfWeek", 1).coerceIn(1, 7), item.optInt("startMinute"),
        item.optInt("endMinute"),
        startWeek,
        item.optInt("endWeek", MAX_SCHEDULE_WEEK).coerceIn(startWeek, MAX_SCHEDULE_WEEK),
        item.optString("weekParity", "ALL"),
    )
}

private fun exceptionToJson(item: PersistedScheduleException) = JSONObject()
    .put("id", item.id).put("lessonId", item.lessonId ?: JSONObject.NULL).put("type", item.type).put("date", item.date)
    .put("title", item.title ?: JSONObject.NULL).put("teacher", item.teacher ?: JSONObject.NULL)
    .put("location", item.location ?: JSONObject.NULL).put("note", item.note ?: JSONObject.NULL)
    .put("dayOfWeek", item.dayOfWeek ?: JSONObject.NULL).put("startMinute", item.startMinute ?: JSONObject.NULL)
    .put("endMinute", item.endMinute ?: JSONObject.NULL)

private fun exceptionFromJson(item: JSONObject): PersistedScheduleException? {
    val id = item.optString("id"); val type = item.optString("type"); val date = item.optString("date")
    if (id.isBlank() || type.isBlank() || date.isBlank()) return null
    return PersistedScheduleException(id, item.optStringOrNull("lessonId"), type, date, item.optStringOrNull("title"),
        item.optStringOrNull("teacher"), item.optStringOrNull("location"), item.optStringOrNull("note"),
        item.optIntOrNull("dayOfWeek"), item.optIntOrNull("startMinute"), item.optIntOrNull("endMinute"))
}

private fun mobileSettingValues(settings: MobileSettings) = mapOf(
    "showWeekend" to valuePayload(settings.showWeekend), "reminderEnabled" to valuePayload(settings.reminderEnabled),
    "reminderMinutes" to valuePayload(settings.reminderMinutes), "keepAliveLevel" to valuePayload(settings.keepAliveLevel),
    "rawIcs" to valuePayload(settings.rawIcs), "wearSyncMode" to valuePayload(settings.wearSyncMode),
    "weekNumberMode" to valuePayload(settings.weekNumberMode),
    "semesterWeekStartDate" to valuePayload(settings.semesterWeekStartDate), "weekStartDay" to valuePayload(settings.weekStartDay),
    "dailyBriefingEnabled" to valuePayload(settings.dailyBriefingEnabled),
    "dailyBriefingChannel" to valuePayload(settings.dailyBriefingChannel.name),
    "dailyBriefingTime" to valuePayload(settings.dailyBriefingTime),
)

private fun cloudConfigValues(settings: MobileSettings) = mapOf(
    "cloudProvider" to valuePayload(settings.cloudProvider), "cloudSyncEnabled" to valuePayload(settings.cloudSyncEnabled),
    "cloudServerUrl" to valuePayload(settings.cloudServerUrl), "cloudRemotePath" to valuePayload(settings.cloudRemotePath),
    "cloudUsername" to valuePayload(settings.cloudUsername), "cloudDriveFileName" to valuePayload(settings.cloudDriveFileName),
    "officialSyncFrequency" to valuePayload(settings.officialSyncFrequency.name),
    "syncScopes" to valuePayload(settings.syncScopes.sortedBy { it.ordinal }.joinToString(",") { it.name }),
)

private fun valuePayload(value: Any?) = JSONObject().put("value", value ?: JSONObject.NULL).toString()

private fun jsonObjectValues(json: JSONObject): Map<String, String> = buildMap {
    val keys = json.keys()
    val metadataKeys = setOf(
        "format", "updatedAt", "revision", "source", "trigger", "deviceId", "logicalCounter",
    )
    while (keys.hasNext()) {
        val key = keys.next()
        if (key !in metadataKeys) put(key, valuePayload(json.opt(key)))
    }
}

private fun livePayloads(document: CloudSyncDocumentV2, domain: String) = document.records[domain].orEmpty().values
    .filterNot { it.isDeleted }.mapNotNull { it.payload }

private fun liveSettingValues(document: CloudSyncDocumentV2, domain: String): Map<String, Any?> =
    document.records[domain].orEmpty().values.filterNot { it.isDeleted }.mapNotNull { record ->
        record.payload?.let { record.id to JSONObject(it).opt("value") }
    }.toMap()

private fun Map<String, Any?>.string(key: String, fallback: String) = this[key]?.toString() ?: fallback
private fun Map<String, Any?>.boolean(key: String, fallback: Boolean) = when (val value = this[key]) {
    is Boolean -> value
    is String -> value.toBooleanStrictOrNull() ?: fallback
    else -> fallback
}
private fun Map<String, Any?>.int(key: String, fallback: Int) = when (val value = this[key]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: fallback
    else -> fallback
}
private fun Map<String, Any?>.syncScopes(key: String, fallback: Set<SyncScope>) = when (val value = this[key]) {
    is String -> value.split(',').mapNotNull { SyncScope.fromRaw(it) }.toSet().ifEmpty { fallback }
    else -> fallback
}

private fun JSONObject.optStringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }
private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
