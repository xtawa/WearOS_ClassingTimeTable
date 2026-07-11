package com.xtawa.classingtime.sync

import android.content.Context
import com.xtawa.classingtime.account.AccountApiClient
import com.xtawa.classingtime.account.AccountApiException
import com.xtawa.classingtime.account.AuthSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns account access-token refreshes for the whole mobile process.
 *
 * Refresh tokens are rotated by the server, so every caller must share the same
 * single-flight gate. Otherwise two callers can submit the same refresh token;
 * the losing request can then erase the session that the winning request saved.
 */
object AccountSessionManager {
    private val refreshGate = AccountSessionRefreshGate()

    suspend fun ensureAccessToken(
        context: Context,
        apiClient: AccountApiClient = AccountApiClient(),
    ): String? {
        return refreshGate.ensureAccessToken(
            store = AndroidAccountSessionStore(context),
            refresh = apiClient::refresh,
        )
    }

    suspend fun refreshAfterUnauthorized(
        context: Context,
        rejectedAccessToken: String,
        apiClient: AccountApiClient = AccountApiClient(),
    ): String? {
        return refreshGate.refreshAfterUnauthorized(
            store = AndroidAccountSessionStore(context),
            rejectedAccessToken = rejectedAccessToken,
            refresh = apiClient::refresh,
        )
    }
}

internal interface AccountSessionStore {
    fun loadAccessToken(): String
    fun loadRefreshToken(): String
    fun isAccessTokenUsable(): Boolean
    fun isRefreshTokenUsable(): Boolean
    fun saveSession(session: AuthSession)
    fun clear()
}

internal class AccountSessionRefreshGate {
    private val mutex = Mutex()

    suspend fun ensureAccessToken(
        store: AccountSessionStore,
        refresh: suspend (String) -> Result<AuthSession>,
    ): String? = mutex.withLock {
        if (store.isAccessTokenUsable()) {
            return@withLock store.loadAccessToken()
        }
        refreshLocked(store, refresh)
    }

    suspend fun refreshAfterUnauthorized(
        store: AccountSessionStore,
        rejectedAccessToken: String,
        refresh: suspend (String) -> Result<AuthSession>,
    ): String? = mutex.withLock {
        val currentAccessToken = store.loadAccessToken()
        if (currentAccessToken.isNotBlank() &&
            currentAccessToken != rejectedAccessToken &&
            store.isAccessTokenUsable()
        ) {
            return@withLock currentAccessToken
        }
        refreshLocked(store, refresh)
    }

    private suspend fun refreshLocked(
        store: AccountSessionStore,
        refresh: suspend (String) -> Result<AuthSession>,
    ): String? {
        if (!store.isRefreshTokenUsable()) {
            store.clear()
            return null
        }

        val attemptedRefreshToken = store.loadRefreshToken()
        val refreshed = refresh(attemptedRefreshToken)
        if (refreshed.isFailure) {
            // A legacy caller may still have completed a refresh outside this gate.
            // Never erase a newer session that appeared while this request was running.
            val storedRefreshToken = store.loadRefreshToken()
            if (storedRefreshToken != attemptedRefreshToken && store.isAccessTokenUsable()) {
                return store.loadAccessToken()
            }

            val error = refreshed.exceptionOrNull()
            val definitelyRevoked = error is AccountApiException &&
                error.statusCode == 401 &&
                error.errorCode == "AUTH_REFRESH_REVOKED"
            if (definitelyRevoked && storedRefreshToken == attemptedRefreshToken) {
                store.clear()
            }
            return null
        }

        val session = refreshed.getOrThrow()
        store.saveSession(session)
        return session.accessToken
    }
}

private class AndroidAccountSessionStore(context: Context) : AccountSessionStore {
    private val appContext = context.applicationContext

    override fun loadAccessToken(): String = AuthCredentialStore.loadAccessToken(appContext)

    override fun loadRefreshToken(): String = AuthCredentialStore.loadRefreshToken(appContext)

    override fun isAccessTokenUsable(): Boolean = AuthCredentialStore.isAccessTokenUsable(appContext)

    override fun isRefreshTokenUsable(): Boolean = AuthCredentialStore.isRefreshTokenUsable(appContext)

    override fun saveSession(session: AuthSession) {
        AuthCredentialStore.saveSession(
            context = appContext,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            accessExpiresAt = session.accessExpiresAt,
            refreshExpiresAt = session.refreshExpiresAt,
        )
    }

    override fun clear() {
        AuthCredentialStore.clear(appContext)
    }
}
