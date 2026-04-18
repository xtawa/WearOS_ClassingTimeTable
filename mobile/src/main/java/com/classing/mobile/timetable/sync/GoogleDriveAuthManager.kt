package com.xtawa.classingtime.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

data class DriveAccessToken(
    val token: String,
    val expireAt: Long,
)

object GoogleDriveAuthManager {
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val DEFAULT_ACCESS_TOKEN_TTL_MS = 50 * 60 * 1000L

    fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
    }

    suspend fun authorize(context: Context): Result<AuthorizationResult> {
        return runCatching {
            Identity.getAuthorizationClient(context)
                .authorize(buildAuthorizationRequest())
                .await()
        }
    }

    fun getAuthorizationResultFromIntent(context: Context, intent: Intent): Result<AuthorizationResult> {
        return runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        }
    }

    fun parseAccessToken(result: AuthorizationResult, now: Long = System.currentTimeMillis()): Result<DriveAccessToken> {
        return runCatching {
            val token = result.accessToken.orEmpty().trim()
            if (token.isBlank()) {
                error("Drive access token is empty")
            }
            DriveAccessToken(
                token = token,
                expireAt = now + DEFAULT_ACCESS_TOKEN_TTL_MS,
            )
        }
    }

    suspend fun tryRefreshAccessTokenSilently(context: Context): Result<DriveAccessToken> {
        val authorization = authorize(context).getOrElse { return Result.failure(it) }
        if (authorization.hasResolution()) {
            return Result.failure(IllegalStateException("Drive authorization requires user interaction"))
        }
        return parseAccessToken(authorization)
    }

    suspend fun clearToken(context: Context, token: String): Result<Unit> {
        if (token.isBlank()) return Result.success(Unit)
        // The current auth dependency set does not expose a token revocation API here.
        // We still clear local state at call sites, which is sufficient for sign-out UX.
        return Result.success(Unit)
    }
}
