package com.xtawa.classingtime.account

import com.xtawa.classingtime.data.AccountSummary
import com.xtawa.classingtime.data.DailyBriefingChannel
import com.xtawa.classingtime.data.MembershipSummary
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
)

data class AccountProfile(
    val account: AccountSummary,
    val membership: MembershipSummary,
)

class AccountApiClient(
    private val baseUrl: String = BASE_URL,
) {
    fun register(username: String, email: String, password: String): Result<AuthSession> {
        return postAuthSession(
            path = "/api/v1/auth/register",
            body = JSONObject()
                .put("username", username.trim())
                .put("email", email.trim())
                .put("password", password),
        )
    }

    fun login(identifier: String, password: String): Result<AuthSession> {
        return postAuthSession(
            path = "/api/v1/auth/login",
            body = JSONObject()
                .put("identifier", identifier.trim())
                .put("password", password),
        )
    }

    fun refresh(refreshToken: String): Result<AuthSession> {
        return postAuthSession(
            path = "/api/v1/auth/refresh",
            body = JSONObject().put("refreshToken", refreshToken),
        )
    }

    fun logout(accessToken: String, refreshToken: String): Result<Unit> {
        return request(
            method = "POST",
            path = "/api/v1/auth/logout",
            accessToken = accessToken,
            body = JSONObject().put("refreshToken", refreshToken),
        ).map { Unit }
    }

    fun requestPasswordReset(email: String): Result<Unit> {
        return request(
            method = "POST",
            path = "/api/v1/auth/password/reset/request",
            body = JSONObject().put("email", email.trim()),
        ).map { Unit }
    }

    fun confirmPasswordReset(token: String, newPassword: String): Result<Unit> {
        return request(
            method = "POST",
            path = "/api/v1/auth/password/reset/confirm",
            body = JSONObject()
                .put("token", token.trim())
                .put("newPassword", newPassword),
        ).map { Unit }
    }

    fun fetchProfile(accessToken: String): Result<AccountProfile> {
        return runCatching {
            val accountJson = request(
                method = "GET",
                path = "/api/v1/account/me",
                accessToken = accessToken,
            ).getOrThrow()
            val membershipJson = request(
                method = "GET",
                path = "/api/v1/membership/status",
                accessToken = accessToken,
            ).getOrThrow()
            AccountProfile(
                account = parseAccountSummary(accountJson),
                membership = parseMembershipSummary(membershipJson),
            )
        }
    }

    fun redeemCode(accessToken: String, code: String): Result<MembershipSummary> {
        return request(
            method = "POST",
            path = "/api/v1/membership/redeem",
            accessToken = accessToken,
            body = JSONObject().put("code", code.trim()),
        ).map { parseMembershipSummary(it) }
    }

    fun saveDailyBriefingSubscription(
        accessToken: String,
        enabled: Boolean,
        channel: DailyBriefingChannel,
        time: String,
    ): Result<Unit> {
        return request(
            method = "PUT",
            path = "/api/v1/briefings/daily",
            accessToken = accessToken,
            body = JSONObject()
                .put("enabled", enabled)
                .put("channel", channel.name)
                .put("time", time),
        ).map { Unit }
    }

    private fun postAuthSession(path: String, body: JSONObject): Result<AuthSession> {
        return request(method = "POST", path = path, body = body).map { json ->
            val session = json.optJSONObject("session") ?: json
            val accessToken = session.optString("accessToken")
            val refreshToken = session.optString("refreshToken")
            require(accessToken.isNotBlank() && refreshToken.isNotBlank()) {
                "Authentication response missing tokens"
            }
            AuthSession(accessToken = accessToken, refreshToken = refreshToken)
        }
    }

    private fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        body: JSONObject? = null,
    ): Result<JSONObject> {
        return runCatching {
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (!accessToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
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
                    val message = runCatching {
                        JSONObject(payload).optString("message").ifBlank { payload }
                    }.getOrDefault(payload)
                    error("HTTP $code ${message.ifBlank { "request failed" }}".trim())
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
        const val BASE_URL: String = "https://api-classing.underflo.ink"
    }
}
