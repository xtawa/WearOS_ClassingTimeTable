package com.xtawa.classingtime.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xtawa.classingtime.BuildConfig
import com.xtawa.classingtime.account.AccountEdition
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ClientIntegritySnapshot(
    val packageName: String,
    val platform: String,
    val market: String,
    val versionCode: Long,
    val signingCertSha256: String,
)

class ClientSignatureException(message: String) : IllegalStateException(message)

/** Outcome of the pre-flight signature check against the API. */
enum class ClientTrustCheckOutcome {
    /** The API confirmed this build is trusted. */
    TRUSTED,

    /** The API definitively rejected this build (tampered, unknown package or wrong edition). */
    REJECTED,

    /**
     * The check could not be completed (rate limited, server error, policy not configured).
     * The caller proceeds and lets the real API call be the authority; the result is not cached.
     */
    INCONCLUSIVE,
}

object ClientIntegrity {
    const val PLATFORM_MOBILE = "ANDROID_MOBILE"
    private const val CHECK_PATH = "/api/v1/client/signature/check"
    private const val TRUST_CACHE_TTL_MS = 5 * 60 * 1_000L
    private const val SIGNATURE_ERROR_MESSAGE = "签名异常，客户端可能被非法修改，已禁止使用在线功能"
    private val SIGNATURE_REJECTION_CODES = setOf(
        "CLIENT_SIGNATURE_INVALID",
        "CLIENT_MARKET_INVALID",
    )

    /**
     * Classifies a signature-check HTTP response. Only an explicit 403 rejection (or a
     * signature-specific error code) is treated as proof of tampering; throttling, outages and
     * missing server policy are transient and must never be presented as a tampered client.
     */
    fun classifyTrustCheck(status: Int, errorCode: String?): ClientTrustCheckOutcome {
        if (status in 200..299) return ClientTrustCheckOutcome.TRUSTED
        val code = errorCode.orEmpty().trim()
        return when {
            code in SIGNATURE_REJECTION_CODES -> ClientTrustCheckOutcome.REJECTED
            status == 403 && code.isBlank() -> ClientTrustCheckOutcome.REJECTED
            else -> ClientTrustCheckOutcome.INCONCLUSIVE
        }
    }

    @Volatile private var trustCache: TrustCache? = null

    fun snapshot(context: Context, platform: String = PLATFORM_MOBILE): ClientIntegritySnapshot {
        val appContext = context.applicationContext
        return ClientIntegritySnapshot(
            packageName = appContext.packageName,
            platform = platform,
            market = BuildConfig.CLIENT_MARKET,
            versionCode = versionCode(appContext),
            signingCertSha256 = signingCertSha256(appContext).orEmpty(),
        )
    }

    fun applyHeaders(
        connection: HttpURLConnection,
        context: Context,
        platform: String = PLATFORM_MOBILE,
    ) {
        connection.setRequestProperty("X-Classing-Account-Market", AccountEdition.selected(context))
        if (BuildConfig.DEBUG) return
        snapshot(context, platform).headers().forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }
    }

    fun requestHeaders(context: Context): Map<String, String> =
        (if (BuildConfig.DEBUG) emptyMap() else snapshot(context).headers()) + ("X-Classing-Account-Market" to AccountEdition.selected(context))

    suspend fun ensureTrusted(
        context: Context,
        baseUrl: String,
        platform: String = PLATFORM_MOBILE,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (BuildConfig.DEBUG) return@runCatching
            val snapshot = snapshot(context, platform)
            if (snapshot.signingCertSha256.isBlank()) {
                throw ClientSignatureException(SIGNATURE_ERROR_MESSAGE)
            }
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
            val now = System.currentTimeMillis()
            trustCache?.takeIf { it.matches(normalizedBaseUrl, snapshot, now) }?.let {
                return@runCatching
            }
            val connection = (URL(normalizedBaseUrl + CHECK_PATH).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                applyHeaders(this, context, platform)
                doInput = true
            }
            try {
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                val error = runCatching { JSONObject(body) }.getOrNull()
                val code = error?.optString("code").orEmpty()
                when (classifyTrustCheck(status, code)) {
                    ClientTrustCheckOutcome.REJECTED -> {
                        val detail = code.ifBlank { "HTTP $status" }
                        throw ClientSignatureException("$SIGNATURE_ERROR_MESSAGE ($detail)")
                    }
                    // Throttled or unavailable: do not cache, let the real request decide.
                    ClientTrustCheckOutcome.INCONCLUSIVE -> return@runCatching
                    ClientTrustCheckOutcome.TRUSTED -> Unit
                }
                trustCache = TrustCache(normalizedBaseUrl, snapshot, now + TRUST_CACHE_TTL_MS)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun ClientIntegritySnapshot.headers(): Map<String, String> = mapOf(
        "X-Classing-Client-Platform" to platform,
        "X-Classing-Client-Market" to market,
        "X-Classing-Package-Name" to packageName,
        "X-Classing-Version-Code" to versionCode.toString(),
        "X-Classing-Signing-Cert-Sha256" to signingCertSha256,
    ).filterValues { it.isNotBlank() }

    private data class TrustCache(
        val baseUrl: String,
        val snapshot: ClientIntegritySnapshot,
        val expiresAt: Long,
    ) {
        fun matches(baseUrl: String, snapshot: ClientIntegritySnapshot, now: Long): Boolean {
            return this.baseUrl == baseUrl && this.snapshot == snapshot && expiresAt > now
        }
    }

    private fun signingCertSha256(context: Context): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo(context, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                .orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo(context, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        val signature = signatures.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun versionCode(context: Context): Long {
        val info = packageInfo(context, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }.takeIf { it > 0L } ?: BuildConfig.VERSION_CODE.toLong()
    }

    private fun packageInfo(context: Context, flags: Int): PackageInfo {
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, flags)
        }
    }
}
