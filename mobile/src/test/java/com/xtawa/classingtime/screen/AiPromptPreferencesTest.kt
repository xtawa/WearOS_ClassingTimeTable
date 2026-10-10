package com.xtawa.classingtime.screen

import com.xtawa.classingtime.account.AiPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiPromptPreferencesTest {
    @Test fun oldAccountsKeepSuggestionsVisibleAndHiddenStateSurvivesDeviceCache() {
        val old = AiPreferences.fromJson(JSONObject("""{"defaultModel":"flash","favoriteModels":["flash"],"version":4}"""))
        assertTrue(old.showPromptSuggestions)
        val restored = AiPreferences.fromJson(old.copy(showPromptSuggestions = false).toJson())
        assertFalse(restored.showPromptSuggestions)
        assertEquals("flash", restored.defaultModel)
        assertEquals(setOf("flash"), restored.favoriteModels)
        assertEquals(4L, restored.version)
    }
}
