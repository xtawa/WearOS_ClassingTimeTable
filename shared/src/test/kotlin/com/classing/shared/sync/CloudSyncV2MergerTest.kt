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
}
