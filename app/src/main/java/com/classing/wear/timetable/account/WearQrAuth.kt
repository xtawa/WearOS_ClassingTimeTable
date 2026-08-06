package com.classing.wear.timetable.account

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.classing.wear.timetable.security.ClientIntegrity
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class WearDeviceAuthorization(
    val authorizationId: String,
    val pollSecret: String,
    val qrPayload: String,
    val expiresAt: Long,
    val intervalSeconds: Int,
)

data class WearDirectAccountSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
    val userId: String,
    val username: String,
    val isMember: Boolean,
    val membershipTier: String,
)

sealed interface WearDeviceAuthorizationPoll {
    data class Pending(val intervalSeconds: Int) : WearDeviceAuthorizationPoll
    data class Approved(val session: WearDirectAccountSession) : WearDeviceAuthorizationPoll
}

class WearQrAuthException(
    val statusCode: Int,
    val errorCode: String,
    message: String,
) : IllegalStateException(message)

class WearQrAuthApiClient(
    private val baseUrl: String = BASE_URL,
    context: Context? = null,
) {
    private val appContext = context?.applicationContext

    suspend fun start(deviceName: String): Result<WearDeviceAuthorization> = runCatching {
        val response = request(
            path = "/api/v1/auth/device/qr/start",
            body = JSONObject().put("deviceName", deviceName.trim()),
        )
        require(response.statusCode == HttpURLConnection.HTTP_CREATED) { "unexpected response" }
        WearDeviceAuthorization(
            authorizationId = response.json.getString("authorizationId"),
            pollSecret = response.json.getString("pollSecret"),
            qrPayload = response.json.getString("qrPayload"),
            expiresAt = response.json.getLong("expiresAt"),
            intervalSeconds = response.json.optInt("intervalSeconds", 5).coerceIn(3, 30),
        )
    }

    suspend fun poll(authorization: WearDeviceAuthorization): Result<WearDeviceAuthorizationPoll> = runCatching {
        val response = request(
            path = "/api/v1/auth/device/qr/poll",
            body = JSONObject()
                .put("authorizationId", authorization.authorizationId)
                .put("pollSecret", authorization.pollSecret),
            acceptStatuses = setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED),
        )
        if (response.statusCode == HttpURLConnection.HTTP_ACCEPTED) {
            WearDeviceAuthorizationPoll.Pending(
                response.json.optInt("intervalSeconds", authorization.intervalSeconds).coerceIn(3, 30),
            )
        } else {
            WearDeviceAuthorizationPoll.Approved(parseSession(response.json))
        }
    }

    suspend fun login(identifier: String, password: String): Result<WearDirectAccountSession> = runCatching {
        val response = request(
            path = "/api/v1/auth/login",
            body = JSONObject()
                .put("identifier", identifier.trim())
                .put("password", password)
                .put("consent", loginConsent()),
            acceptStatuses = setOf(HttpURLConnection.HTTP_OK),
        )
        parseSession(response.json)
    }

    suspend fun logout(session: WearDirectAccountSession): Result<Unit> = runCatching {
        request(
            path = "/api/v1/auth/logout",
            body = JSONObject().put("refreshToken", session.refreshToken),
            accessToken = session.accessToken,
            acceptStatuses = setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_UNAUTHORIZED),
        )
        Unit
    }

    suspend fun refresh(session: WearDirectAccountSession): Result<WearDirectAccountSession> = runCatching {
        val response = request(
            path = "/api/v1/auth/refresh",
            body = JSONObject().put("refreshToken", session.refreshToken),
            acceptStatuses = setOf(HttpURLConnection.HTTP_OK),
        )
        val refreshed = response.json.optJSONObject("session") ?: response.json
        val accessToken = refreshed.optString("accessToken")
        val refreshToken = refreshed.optString("refreshToken")
        require(accessToken.isNotBlank() && refreshToken.isNotBlank()) {
            "Authentication response missing tokens"
        }
        session.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessExpiresAt = refreshed.optLong("accessExpiresAt", 0L),
            refreshExpiresAt = refreshed.optLong("refreshExpiresAt", 0L),
        )
    }

    private fun parseSession(json: JSONObject): WearDirectAccountSession {
        val session = json.getJSONObject("session")
        val account = json.getJSONObject("account")
        val membership = json.optJSONObject("membership") ?: JSONObject()
        return WearDirectAccountSession(
            accessToken = session.getString("accessToken"),
            refreshToken = session.getString("refreshToken"),
            accessExpiresAt = session.optLong("accessExpiresAt", 0L),
            refreshExpiresAt = session.optLong("refreshExpiresAt", 0L),
            userId = account.getString("userId"),
            username = account.optString("username"),
            isMember = membership.optBoolean("isMember", false),
            membershipTier = membership.optString("tier", "FREE").ifBlank { "FREE" },
        )
    }

    private fun loginConsent(): JSONObject = JSONObject()
        .put("privacyPolicy", true)
        .put("termsOfService", true)
        .put("crossBorderTransfer", true)
        .put("acceptedAt", System.currentTimeMillis())
        .put("client", "android-wear")

    private data class HttpResponse(val statusCode: Int, val json: JSONObject)

    private suspend fun request(
        path: String,
        body: JSONObject,
        accessToken: String = "",
        acceptStatuses: Set<Int> = setOf(HttpURLConnection.HTTP_CREATED),
    ): HttpResponse = withContext(Dispatchers.IO) {
        appContext?.let { ClientIntegrity.ensureTrusted(it, baseUrl).getOrThrow() }
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "Classing-WearOS")
            if (accessToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            appContext?.let { ClientIntegrity.applyHeaders(this, it) }
            doInput = true
            doOutput = true
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use(BufferedReader::readText)
                .orEmpty()
                .trim()
            val json = if (payload.isBlank()) JSONObject() else JSONObject(payload)
            if (status !in acceptStatuses) {
                throw WearQrAuthException(
                    statusCode = status,
                    errorCode = json.optString("code"),
                    message = json.optString("message").ifBlank { "request failed" },
                )
            }
            HttpResponse(status, json)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://api-classing.underflo.ink"
    }
}

object WearDirectAccountStore {
    private const val PREF_NAME = "wear_direct_account"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
    private const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_IS_MEMBER = "is_member"
    private const val KEY_MEMBERSHIP_TIER = "membership_tier"

    private fun prefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(context: Context, session: WearDirectAccountSession) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, session.accessExpiresAt)
            .putLong(KEY_REFRESH_EXPIRES_AT, session.refreshExpiresAt)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putBoolean(KEY_IS_MEMBER, session.isMember)
            .putString(KEY_MEMBERSHIP_TIER, session.membershipTier)
            .apply()
    }

    fun load(context: Context, now: Long = System.currentTimeMillis()): WearDirectAccountSession? {
        val prefs = prefs(context)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty()
        val refreshExpiresAt = prefs.getLong(KEY_REFRESH_EXPIRES_AT, 0L)
        val userId = prefs.getString(KEY_USER_ID, "").orEmpty()
        if (refreshToken.isBlank() || userId.isBlank() || refreshExpiresAt <= now) return null
        return WearDirectAccountSession(
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
            refreshToken = refreshToken,
            accessExpiresAt = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            refreshExpiresAt = refreshExpiresAt,
            userId = userId,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            isMember = prefs.getBoolean(KEY_IS_MEMBER, false),
            membershipTier = prefs.getString(KEY_MEMBERSHIP_TIER, "FREE").orEmpty().ifBlank { "FREE" },
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

/** Serializes refresh-token rotation for every direct Wear cloud caller. */
object WearDirectAccountSessionManager {
    private val refreshMutex = Mutex()

    suspend fun ensureSession(
        context: Context,
        apiClient: WearQrAuthApiClient = WearQrAuthApiClient(),
        now: Long = System.currentTimeMillis(),
    ): WearDirectAccountSession? = refreshMutex.withLock {
        val current = WearDirectAccountStore.load(context, now) ?: return@withLock null
        if (current.accessToken.isNotBlank() && current.accessExpiresAt > now + ACCESS_EXPIRY_SKEW_MS) {
            return@withLock current
        }
        refreshLocked(context, current, apiClient, now)
    }

    suspend fun refreshAfterUnauthorized(
        context: Context,
        rejectedAccessToken: String,
        apiClient: WearQrAuthApiClient = WearQrAuthApiClient(),
        now: Long = System.currentTimeMillis(),
    ): WearDirectAccountSession? = refreshMutex.withLock {
        val current = WearDirectAccountStore.load(context, now) ?: return@withLock null
        if (current.accessToken.isNotBlank() &&
            current.accessToken != rejectedAccessToken &&
            current.accessExpiresAt > now + ACCESS_EXPIRY_SKEW_MS
        ) {
            return@withLock current
        }
        refreshLocked(context, current, apiClient, now)
    }

    private suspend fun refreshLocked(
        context: Context,
        current: WearDirectAccountSession,
        apiClient: WearQrAuthApiClient,
        now: Long,
    ): WearDirectAccountSession? {
        if (current.refreshToken.isBlank() || current.refreshExpiresAt <= now) {
            WearDirectAccountStore.clear(context)
            return null
        }
        val attemptedRefreshToken = current.refreshToken
        val result = apiClient.refresh(current)
        if (result.isSuccess) {
            return result.getOrThrow().also { WearDirectAccountStore.save(context, it) }
        }

        val latest = WearDirectAccountStore.load(context, now)
        if (latest != null && latest.refreshToken != attemptedRefreshToken &&
            latest.accessToken.isNotBlank() && latest.accessExpiresAt > now
        ) {
            return latest
        }
        val error = result.exceptionOrNull()
        val definitelyRevoked = error is WearQrAuthException &&
            error.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED &&
            error.errorCode in setOf("AUTH_REFRESH_REVOKED", "AUTH_SESSION_REVOKED", "AUTH_REQUIRED")
        if (definitelyRevoked && latest?.refreshToken == attemptedRefreshToken) {
            WearDirectAccountStore.clear(context)
        }
        return null
    }

    private const val ACCESS_EXPIRY_SKEW_MS = 60_000L
}
