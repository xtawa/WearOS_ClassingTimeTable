package com.classing.shared.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudSyncV2MergerTest {
    private fun record(id: String, counter: Long, device: String, payload: String = id) = VersionedRecord(
        id = id,
        payload = payload,
        version = LogicalVersion(counter, device, counter),
    )

    @Test
    fun `merge is commutative and idempotent`() {
        val a = CloudSyncDocumentV2(records = mapOf("lessons" to mapOf("a" to record("a", 1, "phone-a"))))
        val b = CloudSyncDocumentV2(records = mapOf("lessons" to mapOf("a" to record("a", 1, "phone-b", "new"))))

        val ab = CloudSyncV2Merger.merge(a, b, 10).document
        val ba = CloudSyncV2Merger.merge(b, a, 10).document
        assertEquals(ab, ba)
        assertFalse(CloudSyncV2Merger.merge(ab, ab, 10).changed)
    }

    @Test
    fun `higher counter wins and device id breaks concurrent tie`() {
        val old = record("a", 1, "z", "old")
        val newer = record("a", 2, "a", "new")
        val concurrentWinner = record("a", 2, "z", "tie winner")
        val merged = CloudSyncV2Merger.merge(
            CloudSyncDocumentV2(records = mapOf("d" to mapOf("a" to old))),
            CloudSyncDocumentV2(records = mapOf("d" to mapOf("a" to newer))),
            10,
        ).document
        assertEquals("new", merged.records.getValue("d").getValue("a").payload)

        val tied = CloudSyncV2Merger.merge(
            merged,
            CloudSyncDocumentV2(records = mapOf("d" to mapOf("a" to concurrentWinner))),
            10,
        ).document
        assertEquals("tie winner", tied.records.getValue("d").getValue("a").payload)
    }

    @Test
    fun `expired tombstone keeps deletion guard but drops recovery payload`() {
        val deleted = VersionedRecord(
            id = "a",
            payload = "recover me",
            version = LogicalVersion(4, "phone", 100),
            deletedAt = 100,
            recoverableUntil = 200,
        )
        val compacted = CloudSyncDocumentV2(records = mapOf("d" to mapOf("a" to deleted))).compact(201)
            .records.getValue("d").getValue("a")
        assertTrue(compacted.isDeleted)
        assertNull(compacted.payload)
        assertNull(compacted.recoverableUntil)
    }

    @Test
    fun `compact keeps the most recently active devices within the limit`() {
        val devices = (1..70).associate { index ->
            "device-$index" to DeviceSyncMetadata("device-$index", lastCounter = index.toLong(), lastChangedAt = index.toLong())
        }
        val document = CloudSyncDocumentV2(devices = devices)

        val compacted = document.compact(now = 1_000L)

        assertEquals(CloudSyncV2.MAX_DEVICES, compacted.devices.size)
        assertTrue(compacted.devices.containsKey("device-70"))
        assertFalse(compacted.devices.containsKey("device-1"))
        assertFalse(compacted.devices.containsKey("device-6"))
        assertTrue(compacted.devices.containsKey("device-7"))
    }

    @Test
    fun `compact leaves device metadata untouched below the limit`() {
        val devices = mapOf(
            "a" to DeviceSyncMetadata("a", 1, 1),
            "b" to DeviceSyncMetadata("b", 2, 2),
        )
        val compacted = CloudSyncDocumentV2(devices = devices).compact(now = 10L)
        assertEquals(devices, compacted.devices)
    }

    @Test
    fun `merge bounds device metadata growth`() {
        val left = CloudSyncDocumentV2(
            devices = (1..40).associate { "l$it" to DeviceSyncMetadata("l$it", it.toLong(), it.toLong()) },
        )
        val right = CloudSyncDocumentV2(
            devices = (1..40).associate { "r$it" to DeviceSyncMetadata("r$it", 100L + it, 100L + it) },
        )
        val merged = CloudSyncV2Merger.merge(left, right, 1_000L).document
        assertEquals(CloudSyncV2.MAX_DEVICES, merged.devices.size)
        assertTrue(merged.devices.keys.all { it.startsWith("r") || it in (17..40).map { n -> "l$n" } })
    }
}
