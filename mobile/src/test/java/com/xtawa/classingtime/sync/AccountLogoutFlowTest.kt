package com.xtawa.classingtime.sync

import com.xtawa.classingtime.account.AccountApiException
import com.xtawa.classingtime.account.AuthSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLogoutFlowTest {
    @Test
    fun logout_refreshesExpiredAccessTokenAndUsesRotatedRefreshToken() = runBlocking {
        val store = RecordingSessionStore(accessToken = "expired", refreshToken = "refresh-old", accessUsable = false)
        val gate = AccountSessionRefreshGate()
        var logoutCall: Pair<String, String>? = null

        val revoked = AccountLogoutFlow.logoutAndClear(
            store = store,
            ensureAccessToken = {
                gate.ensureAccessToken(store) { presented ->
                    assertEquals("refresh-old", presented)
                    Result.success(AuthSession("access-new", "refresh-new", 1L, 2L))
                }
            },
            logout = { access, refresh ->
                logoutCall = access to refresh
                Result.success(Unit)
            },
        )

        assertTrue(revoked)
        assertEquals("access-new" to "refresh-new", logoutCall)
        assertTrue(store.cleared)
    }

    @Test
    fun logout_usesCurrentAccessTokenWhenStillValid() = runBlocking {
        val store = RecordingSessionStore(accessToken = "access-ok", refreshToken = "refresh-ok", accessUsable = true)
        var logoutCall: Pair<String, String>? = null

        val revoked = AccountLogoutFlow.logoutAndClear(
            store = store,
            ensureAccessToken = { AccountSessionRefreshGate().ensureAccessToken(store) { error("refresh must not be called") } },
            logout = { access, refresh ->
                logoutCall = access to refresh
                Result.success(Unit)
            },
        )

        assertTrue(revoked)
        assertEquals("access-ok" to "refresh-ok", logoutCall)
        assertTrue(store.cleared)
    }

    @Test
    fun logout_clearsLocallyWhenSessionAlreadyRevoked() = runBlocking {
        val store = RecordingSessionStore(accessToken = "expired", refreshToken = "refresh-old", accessUsable = false)
        var logoutCalled = false

        val revoked = AccountLogoutFlow.logoutAndClear(
            store = store,
            ensureAccessToken = {
                AccountSessionRefreshGate().ensureAccessToken(store) {
                    Result.failure(AccountApiException(401, "AUTH_REFRESH_REVOKED", message = "revoked"))
                }
            },
            logout = { _, _ ->
                logoutCalled = true
                Result.success(Unit)
            },
        )

        assertFalse(revoked)
        assertFalse(logoutCalled)
        assertTrue(store.cleared)
    }

    @Test
    fun logout_clearsLocallyEvenWhenServerCallFails() = runBlocking {
        val store = RecordingSessionStore(accessToken = "access-ok", refreshToken = "refresh-ok", accessUsable = true)

        val revoked = AccountLogoutFlow.logoutAndClear(
            store = store,
            ensureAccessToken = { store.loadAccessToken() },
            logout = { _, _ -> Result.failure(java.io.IOException("offline")) },
        )

        assertFalse(revoked)
        assertTrue(store.cleared)
    }
}

private class RecordingSessionStore(
    private var accessToken: String,
    private var refreshToken: String,
    private var accessUsable: Boolean,
) : AccountSessionStore {
    var cleared = false
        private set

    override fun loadAccessToken(): String = accessToken
    override fun loadRefreshToken(): String = refreshToken
    override fun isAccessTokenUsable(): Boolean = accessUsable
    override fun isRefreshTokenUsable(): Boolean = refreshToken.isNotBlank()

    override fun saveSession(session: AuthSession) {
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        accessUsable = true
    }

    override fun clear() {
        accessToken = ""
        refreshToken = ""
        accessUsable = false
        cleared = true
    }
}
