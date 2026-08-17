package com.classing.wear.timetable.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncPayloadApplierIdTest {
    @Test
    fun `existing local id wins when Room reports an update sentinel`() {
        val localId = stableLocalId(
            existingLocalId = 42,
            upsertResult = -1,
            entityName = "course",
        )

        assertEquals(42, localId)
    }

    @Test
    fun `inserted row id is used for a new entity`() {
        val localId = stableLocalId(
            existingLocalId = null,
            upsertResult = 17,
            entityName = "course",
        )

        assertEquals(17, localId)
    }

    @Test
    fun `invalid id from a new entity fails before a foreign key write`() {
        assertThrows(IllegalArgumentException::class.java) {
            stableLocalId(
                existingLocalId = null,
                upsertResult = -1,
                entityName = "course",
            )
        }
    }
}
