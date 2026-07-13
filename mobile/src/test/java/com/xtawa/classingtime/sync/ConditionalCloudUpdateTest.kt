package com.xtawa.classingtime.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConditionalCloudUpdateTest {
    @Test
    fun retriesConflictsAndReturnsSuccessfulAttempt() = runBlocking {
        var calls = 0
        val result = retryConditionalCloudUpdate(maxAttempts = 4) { attempt ->
            calls += 1
            if (attempt < 2) throw CloudWriteConflictException()
            "written"
        }
        assertEquals("written", result)
        assertEquals(3, calls)
    }

    @Test
    fun propagatesConflictAfterAttemptLimit() {
        assertThrows(CloudWriteConflictException::class.java) {
            runBlocking {
                retryConditionalCloudUpdate<Unit>(maxAttempts = 2) { throw CloudWriteConflictException() }
            }
        }
    }

    @Test
    fun retriesWhenLocalStateChangesDuringMerge() = runBlocking {
        var calls = 0
        val result = retryConditionalCloudUpdate(maxAttempts = 3) {
            calls += 1
            if (calls == 1) throw LocalCloudStateChangedException()
            "merged-latest-local-state"
        }
        assertEquals("merged-latest-local-state", result)
        assertEquals(2, calls)
    }
}
