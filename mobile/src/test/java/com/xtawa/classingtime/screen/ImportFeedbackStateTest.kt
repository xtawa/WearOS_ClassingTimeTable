package com.xtawa.classingtime.screen

import org.junit.Assert.*
import org.junit.Test

class ImportFeedbackStateTest {
    @Test fun jsonFailureDoesNotShowUpInIcsOrEnableIcsConfirmation() {
        val state = ImportFeedbackState()
            .parsed(ImportFocusMethod.ICS, "ICS preview ready", listOf("ICS warning"), true)
            .parsed(ImportFocusMethod.JSON, "Invalid JSON", listOf("JSON warning"), false)
        assertEquals("ICS preview ready", state.forMethod(ImportFocusMethod.ICS).message)
        assertEquals(listOf("ICS warning"), state.forMethod(ImportFocusMethod.ICS).warnings)
        assertEquals("Invalid JSON", state.forMethod(ImportFocusMethod.JSON).message)
        assertFalse(state.canConfirm(ImportFocusMethod.ICS))
        assertFalse(state.canConfirm(ImportFocusMethod.JSON))
    }

    @Test fun jsonPreviewCanOnlyBeConfirmedByJsonAndManualFeedbackStaysSeparate() {
        val state = ImportFeedbackState()
            .parsed(ImportFocusMethod.JSON, "13 courses", emptyList(), true)
            .message(ImportFocusMethod.MANUAL, "Enter a title")
        assertTrue(state.canConfirm(ImportFocusMethod.JSON))
        assertFalse(state.canConfirm(ImportFocusMethod.ICS))
        assertEquals("13 courses", state.forMethod(ImportFocusMethod.JSON).message)
        assertEquals("", state.forMethod(ImportFocusMethod.ICS).message)
        assertEquals("Enter a title", state.forMethod(ImportFocusMethod.MANUAL).message)
    }

    @Test fun editingOrClearingIcsKeepsJsonPreviewAndEditingJsonInvalidatesIt() {
        val state = ImportFeedbackState().parsed(ImportFocusMethod.JSON, "13 courses", emptyList(), true)
        val clearedIcs = state.editing(ImportFocusMethod.ICS).message(ImportFocusMethod.ICS, "Cleared")
        assertTrue(clearedIcs.canConfirm(ImportFocusMethod.JSON))
        assertEquals("13 courses", clearedIcs.forMethod(ImportFocusMethod.JSON).message)
        val changedJson = clearedIcs.editing(ImportFocusMethod.JSON)
        assertFalse(changedJson.canConfirm(ImportFocusMethod.JSON))
        assertEquals("", changedJson.forMethod(ImportFocusMethod.JSON).message)
    }
}
