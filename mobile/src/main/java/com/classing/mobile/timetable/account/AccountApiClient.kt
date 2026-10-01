package com.xtawa.classingtime.account

import android.content.Context
import com.xtawa.classingtime.BuildConfig
import com.xtawa.classingtime.security.ClientIntegrity
import com.xtawa.classingtime.data.AccountSummary
import com.xtawa.classingtime.data.DailyBriefingChannel
import com.xtawa.classingtime.data.MembershipSummary
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
)

data class RegistrationVerificationChallenge(
    val challengeId: String,
    val expiresAt: Long,
    val resendAfterSeconds: Int,
)

data class RegistrationSecurityConfig(
    val turnstileRequired: Boolean,
    val turnstileSiteKey: String,
    val legalAgreementUrls: LegalAgreementUrls = LegalAgreementUrls(),
)

data class WearLoginDebugInfo(
    val stage: String,
    val terminal: Boolean,
    val loginSucceeded: Boolean?,
    val approvalSucceeded: Boolean?,
    val code: String,
    val reason: String,
    val authorizationId: String,
    val serverTimestampMs: Long,
)

data class WearDeviceLoginApproval(
    val status: String,
    val authorizationId: String,
    val expiresAt: Long,
    val debug: WearLoginDebugInfo?,
)

data class LegalAgreementUrls(
    val privacyPolicy: String = "",
    val termsOfService: String = "",
    val crossBorderTransfer: String = "",
)

class AccountApiException(
    val statusCode: Int,
    val errorCode: String,
    val retryAfterSeconds: Int = 0,
    val debugInfo: WearLoginDebugInfo? = null,
    message: String,
) : IllegalStateException(message)

internal object AccountRequestCooldown {
	private val untilByPath = mutableMapOf<String, Long>()

	@Synchronized
	fun remainingSeconds(path: String, now: Long = System.currentTimeMillis()): Int {
		val until = untilByPath[path] ?: return 0
		if (until <= now) {
			untilByPath.remove(path)
			return 0
		}
		return ((until - now + 999L) / 1_000L).toInt().coerceAtLeast(1)
	}

	@Synchronized
	fun record(path: String, retryAfterSeconds: Int, now: Long = System.currentTimeMillis()) {
		untilByPath[path] = maxOf(untilByPath[path] ?: 0L, now + retryAfterSeconds.coerceAtLeast(1) * 1_000L)
	}
}

data class PendingEmailChange(
    val newEmail: String,
    val expiresAt: Long,
)

data class EmailChangeRequest(
    val requestId: String,
    val expiresAt: Long,
    val resendAfterSeconds: Int,
)

/**
 * How a device zone id maps onto the briefing API's `timezone` field.
 *
 * - [id] non-null: a tz-database identifier the API accepts (region id, `UTC`, or `Etc/GMT±H`
 *   for whole-hour fixed offsets).
 * - [id] null and [unsupportedOffset] true: the device uses a fixed offset that the tz database
 *   cannot name (minute-level offsets such as +05:30, or outside UTC-12..UTC+14). The request
 *   omits the field and the service applies its default zone; callers should tell the user.
 * - [id] null and [unsupportedOffset] false: unrecognised id; the field is omitted.
 */
data class BriefingTimezoneResolution(
    val id: String?,
    val unsupportedOffset: Boolean,
) {
    companion object {
        fun supported(id: String) = BriefingTimezoneResolution(id, unsupportedOffset = false)
        fun unsupportedOffset() = BriefingTimezoneResolution(null, unsupportedOffset = true)
        fun unknown() = BriefingTimezoneResolution(null, unsupportedOffset = false)
    }
}

data class AccountProfile(
    val account: AccountSummary,
    val membership: MembershipSummary,
    val pendingEmailChange: PendingEmailChange? = null,
)

class AccountApiClient(
    private val baseUrl: String = BASE_URL,
    private val deviceId: String = "",
    appContext: Context? = null,
) {
    private val appContext = appContext?.applicationContext

    suspend fun requestRegistrationVerification(
        username: String,
        email: String,
        password: String,
        turnstileToken: String = "",
    ): Result<RegistrationVerificationChallenge> {
        return request(
            method = "POST",
            path = "/api/v1/auth/register/email/request",
            body = JSONObject()
                .put("username", username.trim())
                .put("email", email.trim())
                .put("password", password)
                .put("turnstileToken", turnstileToken)
                .put("consent", legalConsent()),
        ).map { json ->
            val challenge = json.optJSONObject("challenge") ?: json
            RegistrationVerificationChallenge(
                challengeId = challenge.getString("challengeId"),
                expiresAt = challenge.optLong("expiresAt", 0L),
                resendAfterSeconds = challenge.optInt("resendAfterSeconds", 60),
            )
        }
    }

    suspend fun registrationSecurityConfig(): Result<RegistrationSecurityConfig> {
        return request(method = "GET", path = "/api/v1/auth/registration/config").map { json ->
            RegistrationSecurityConfig(
                turnstileRequired = json.optBoolean("turnstileRequired", false),
                turnstileSiteKey = json.optString("turnstileSiteKey"),
                legalAgreementUrls = json.optJSONObject("legalAgreementUrls")?.let {
                    LegalAgreementUrls(
                        privacyPolicy = it.optString("privacyPolicy"),
                        termsOfService = it.optString("termsOfService"),
                        crossBorderTransfer = it.optString("crossBorderTransfer"),
                    )
                } ?: LegalAgreementUrls(),
            )
        }
    }

    suspend fun confirmRegistration(
        challengeId: String,
        verificationCode: String,
    ): Result<AuthSession> {
        return postAuthSession(
            path = "/api/v1/auth/register/email/confirm",
            body = JSONObject()
                .put("challengeId", challengeId)
                .put("verificationCode", verificationCode.trim())
                .put("consent", legalConsent()),
        )
    }

    suspend fun login(identifier: String, password: String): Result<AuthSession> {
        return postAuthSession(
            path = "/api/v1/auth/login",
            body = JSONObject()
                .put("identifier", identifier.trim())
                .put("password", password)
                .put("consent", legalConsent()),
        )
    }

    suspend fun refresh(refreshToken: String): Result<AuthSession> {
        return postAuthSession(
            path = "/api/v1/auth/refresh",
            body = JSONObject().put("refreshToken", refreshToken),
        )
    }

    suspend fun logout(accessToken: String, refreshToken: String): Result<Unit> {
        return request(
            method = "POST",
            path = "/api/v1/auth/logout",
            accessToken = accessToken,
            body = JSONObject().put("refreshToken", refreshToken),
        ).map { Unit }
    }

    suspend fun approveWearDeviceLogin(
        accessToken: String,
        authorizationId: String,
        debug: Boolean = false,
    ): Result<WearDeviceLoginApproval> {
        return request(
            method = "POST",
            path = "/api/v1/auth/device/qr/approve",
            accessToken = accessToken,
            body = JSONObject()
                .put("authorizationId", authorizationId.trim())
                .put("debug", debug),
            debugRequest = debug,
        ).map { response ->
            WearDeviceLoginApproval(
                status = response.optString("status"),
                authorizationId = response.optString("authorizationId", authorizationId.trim()),
                expiresAt = response.optLong("expiresAt", 0L),
                debug = response.optJSONObject("debug")?.toWearLoginDebugInfo(),
            )
        }
    }

    suspend fun awaitWearDeviceLoginDebug(
        accessToken: String,
        authorizationId: String,
        attempts: Int = 10,
    ): Result<WearLoginDebugInfo> {
        repeat(attempts.coerceIn(1, 30)) { attempt ->
            val response = request(
                method = "POST",
                path = "/api/v1/auth/device/qr/status",
                accessToken = accessToken,
                body = JSONObject()
                    .put("authorizationId", authorizationId.trim())
                    .put("debug", true),
                debugRequest = true,
            )
            if (response.isFailure) {
                return Result.failure(response.exceptionOrNull() ?: IllegalStateException("Wear login status failed"))
            }
            val debug = response.getOrThrow().optJSONObject("debug")?.toWearLoginDebugInfo()
                ?: return Result.failure(IllegalStateException("Wear login status response missing debug information"))
            if (debug.terminal) return Result.success(debug)
            if (attempt + 1 < attempts) delay(1_000)
        }
        val debug = WearLoginDebugInfo(
            stage = "MOBILE_STATUS",
            terminal = true,
            loginSucceeded = false,
            approvalSucceeded = true,
            code = "DEVICE_LOGIN_DEBUG_TIMEOUT",
            reason = "Wear did not complete login within the debug observation window",
            authorizationId = authorizationId.trim(),
            serverTimestampMs = System.currentTimeMillis(),
        )
        return Result.failure(
            AccountApiException(
                statusCode = 408,
                errorCode = debug.code,
                debugInfo = debug,
                message = debug.reason,
            ),
        )
    }

    suspend fun deleteAccount(accessToken: String, currentPassword: String, confirm: String): Result<Unit> {
        return request(
            method = "POST",
            path = "/api/v1/account/delete",
            accessToken = accessToken,
            body = JSONObject()
                .put("currentPassword", currentPassword)
                .put("confirm", confirm.trim()),
        ).map { Unit }
    }

    suspend fun requestPasswordReset(email: String): Result<Unit> {
        return request(
            method = "POST",
            path = "/api/v1/auth/password/reset/request",
            body = JSONObject().put("email", email.trim()),
        ).map { Unit }
    }

    suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit> {
        return request(
            method = "POST",
            path = "/api/v1/auth/password/reset/confirm",
            body = JSONObject()
                .put("token", token.trim())
                .put("newPassword", newPassword),
        ).map { Unit }
    }

    suspend fun fetchMembershipStatus(accessToken: String): Result<MembershipSummary> {
        return request(
            method = "GET",
            path = "/api/v1/membership/status",
            accessToken = accessToken,
        ).map { parseMembershipSummary(it) }
    }

    suspend fun fetchProfile(accessToken: String): Result<AccountProfile> {
        return runCatching {
            val accountJson = request(
                method = "GET",
                path = "/api/v1/account/me",
                accessToken = accessToken,
            ).getOrThrow()
            val membership = fetchMembershipStatus(accessToken).getOrThrow()
            AccountProfile(
                account = parseAccountSummary(accountJson),
                membership = membership,
                pendingEmailChange = accountJson.optJSONObject("pendingEmailChange")?.let {
                    PendingEmailChange(
                        newEmail = it.optString("newEmail"),
                        expiresAt = it.optLong("expiresAt", 0L),
                    )
                },
            )
        }
    }

    suspend fun requestEmailChange(
        accessToken: String,
        username: String,
        email: String,
        currentPassword: String,
    ): Result<EmailChangeRequest> {
        return request(
            method = "PATCH",
            path = "/api/v1/account/me",
            accessToken = accessToken,
            body = JSONObject()
                .put("username", username.trim())
                .put("email", email.trim())
                .put("currentPassword", currentPassword),
        ).mapCatching { json -> parseEmailChangeResponse(json) }
    }

    suspend fun confirmEmailChange(
        accessToken: String,
        requestId: String,
        verificationCode: String,
    ): Result<Boolean> {
        return request(
            method = "POST",
            path = "/api/v1/account/email/confirm",
            accessToken = accessToken,
            body = JSONObject()
                .put("requestId", requestId)
                .put("verificationCode", verificationCode.trim()),
        ).map { it.optBoolean("sessionsRevoked", true) }
    }

    suspend fun redeemCode(accessToken: String, code: String): Result<MembershipSummary> {
        return request(
            method = "POST",
            path = "/api/v1/membership/redeem",
            accessToken = accessToken,
            body = JSONObject().put("code", code.trim()),
        ).map { parseMembershipSummary(it) }
    }

    suspend fun saveDailyBriefingSubscription(
        accessToken: String,
        enabled: Boolean,
        channel: DailyBriefingChannel,
        time: String,
        timezone: String = TimeZone.getDefault().id,
    ): Result<Unit> {
        val body = JSONObject()
            .put("enabled", enabled)
            .put("channel", channel.name)
            .put("time", time)
        // The delivery time is a local wall-clock value; without the zone the service applies
        // its default region. UTC aliases and whole-hour offsets are normalised to tz-database
        // names; offsets the database cannot name are omitted (see BriefingTimezoneResolution).
        resolveBriefingTimezone(timezone).id?.let { body.put("timezone", it) }
        return request(
            method = "PUT",
            path = "/api/v1/briefings/daily",
            accessToken = accessToken,
            body = body,
        ).map { Unit }
    }

    private suspend fun postAuthSession(path: String, body: JSONObject): Result<AuthSession> {
        return request(method = "POST", path = path, body = body).map { json ->
            val session = json.optJSONObject("session") ?: json
            val accessToken = session.optString("accessToken")
            val refreshToken = session.optString("refreshToken")
            require(accessToken.isNotBlank() && refreshToken.isNotBlank()) {
                "Authentication response missing tokens"
            }
            AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessExpiresAt = session.optLong("accessExpiresAt", 0L),
                refreshExpiresAt = session.optLong("refreshExpiresAt", 0L),
            )
        }
    }

    private fun shouldVerifyClientSignature(path: String): Boolean {
        return path != "/api/v1/auth/registration/config"
    }

    private fun legalConsent(): JSONObject = JSONObject()
        .put("privacyPolicy", true)
        .put("termsOfService", true)
        .put("crossBorderTransfer", true)
        .put("acceptedAt", System.currentTimeMillis())
        .put("client", "android-mobile")

    private suspend fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        body: JSONObject? = null,
        debugRequest: Boolean = false,
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            if (shouldVerifyClientSignature(path)) {
                appContext?.let { ClientIntegrity.ensureTrusted(it, baseUrl).getOrThrow() }
            }
			AccountRequestCooldown.remainingSeconds(path).takeIf { it > 0 }?.let { remaining ->
				throw AccountApiException(429, "CLIENT_COOLDOWN", remaining, message = "retry after $remaining seconds")
			}
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                appContext?.let { ClientIntegrity.applyHeaders(this, it) }
                if (deviceId.isNotBlank()) {
                    setRequestProperty("X-Classing-Device-ID", deviceId.take(128))
                }
                if (!accessToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                if (debugRequest) {
                    setRequestProperty("X-Classing-Debug", "true")
                }
                doInput = true
                if (body != null) {
                    doOutput = true
                }
            }
            try {
                if (body != null) {
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                }
                val code = connection.responseCode
                val payload = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use(BufferedReader::readText)
                    .orEmpty()
                    .trim()
                if (code !in 200..299) {
                    val errorBody = runCatching { JSONObject(payload) }.getOrNull()
                    val message = errorBody?.optString("message").orEmpty().ifBlank { payload }
					val retryAfter = connection.getHeaderField("Retry-After")?.toIntOrNull() ?: 0
					if (code == 429) AccountRequestCooldown.record(path, retryAfter)
                    throw AccountApiException(
                        statusCode = code,
                        errorCode = errorBody?.optString("code").orEmpty(),
						retryAfterSeconds = retryAfter,
                        debugInfo = errorBody?.optJSONObject("debug")?.toWearLoginDebugInfo(),
                        message = message.ifBlank { "request failed" },
                    )
                }
                if (payload.isBlank()) JSONObject() else JSONObject(payload)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseAccountSummary(json: JSONObject): AccountSummary {
        val account = json.optJSONObject("account") ?: json
        return AccountSummary(
            userId = account.optString("userId").ifBlank { account.optString("id") },
            identifier = account.optString("identifier")
                .ifBlank { account.optString("email").ifBlank { account.optString("username") } },
            username = account.optString("username"),
            email = account.optString("email"),
        )
    }

    private fun parseMembershipSummary(json: JSONObject): MembershipSummary {
        val membership = json.optJSONObject("membership") ?: json
        return MembershipSummary(
            isMember = membership.optBoolean("isMember", false),
            tier = membership.optString("tier", "FREE").ifBlank { "FREE" },
            expiresAt = membership.optLong("expiresAt", 0L),
            lastCheckedAt = membership.optLong("lastCheckedAt", System.currentTimeMillis()),
        )
    }

    companion object {
        val BASE_URL: String
            get() = BuildConfig.API_BASE_URL

        /** Client-side error code: the requested email equals the account's current address. */
        const val ERROR_EMAIL_UNCHANGED = "ACCOUNT_EMAIL_UNCHANGED"

        /**
         * When the submitted address equals the current one the API applies the profile update
         * and responds without a verification challenge. Surface that as a dedicated error
         * instead of a JSON parsing failure so the UI can explain it.
         */
        internal fun parseEmailChangeResponse(json: JSONObject): EmailChangeRequest {
            val change = json.optJSONObject("emailChange")
                ?: throw AccountApiException(
                    statusCode = 200,
                    errorCode = ERROR_EMAIL_UNCHANGED,
                    message = "new email matches the current address",
                )
            return EmailChangeRequest(
                requestId = change.getString("requestId"),
                expiresAt = change.optLong("expiresAt", 0L),
                resendAfterSeconds = change.optInt("resendAfterSeconds", 60),
            )
        }

        /**
         * Resolves a device zone id into the form accepted by the briefing API, or reports why it
         * cannot be expressed. See [BriefingTimezoneResolution].
         */
        fun resolveBriefingTimezone(timezone: String?): BriefingTimezoneResolution {
            val value = timezone.orEmpty().trim()
            if (value.isEmpty() || value.length > 64) return BriefingTimezoneResolution.unknown()
            if (value.uppercase() in UTC_ALIASES) return BriefingTimezoneResolution.supported("UTC")
            val zone = runCatching { ZoneId.of(value) }.getOrNull()
                ?: return if (looksLikeRegionId(value)) {
                    BriefingTimezoneResolution.supported(value)
                } else {
                    BriefingTimezoneResolution.unknown()
                }
            val normalized = zone.normalized()
            if (normalized is ZoneOffset) {
                val totalSeconds = normalized.totalSeconds
                if (totalSeconds == 0) return BriefingTimezoneResolution.supported("UTC")
                if (totalSeconds % 3600 != 0) return BriefingTimezoneResolution.unsupportedOffset()
                val hours = totalSeconds / 3600
                if (hours !in ETC_GMT_MIN_HOURS..ETC_GMT_MAX_HOURS) return BriefingTimezoneResolution.unsupportedOffset()
                // tz database "Etc/GMT" zones use POSIX sign convention: Etc/GMT-8 is UTC+08:00.
                val sign = if (hours > 0) "-" else "+"
                return BriefingTimezoneResolution.supported("Etc/GMT$sign${kotlin.math.abs(hours)}")
            }
            val regionId = zone.id
            return if (looksLikeRegionId(regionId)) {
                BriefingTimezoneResolution.supported(regionId)
            } else {
                BriefingTimezoneResolution.unknown()
            }
        }

        /** Convenience for callers that only need the request value. */
        fun briefingTimezoneOrNull(timezone: String?): String? = resolveBriefingTimezone(timezone).id

        private fun looksLikeRegionId(value: String): Boolean =
            value.length <= 64 &&
                value.contains('/') &&
                value.all { it.isLetterOrDigit() || it == '/' || it == '_' || it == '-' || it == '+' }

        private val UTC_ALIASES = setOf(
            "UTC", "GMT", "UT", "Z", "UCT", "ZULU", "UNIVERSAL", "GREENWICH", "GMT0",
            "ETC/UTC", "ETC/GMT", "ETC/UCT", "ETC/UT", "ETC/GMT0", "ETC/GMT+0", "ETC/GMT-0",
            "ETC/UNIVERSAL", "ETC/ZULU", "ETC/GREENWICH",
        )

        /** Fixed-offset zones exist in the tz database as Etc/GMT+12 (UTC-12) through Etc/GMT-14 (UTC+14). */
        private const val ETC_GMT_MIN_HOURS = -12
        private const val ETC_GMT_MAX_HOURS = 14
    }
}

private fun JSONObject.toWearLoginDebugInfo(): WearLoginDebugInfo = WearLoginDebugInfo(
    stage = optString("stage"),
    terminal = optBoolean("terminal", false),
    loginSucceeded = nullableBoolean("loginSucceeded"),
    approvalSucceeded = nullableBoolean("approvalSucceeded"),
    code = optString("code"),
    reason = optString("reason"),
    authorizationId = optString("authorizationId"),
    serverTimestampMs = optLong("serverTimestampMs", 0L),
)

private fun JSONObject.nullableBoolean(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null
