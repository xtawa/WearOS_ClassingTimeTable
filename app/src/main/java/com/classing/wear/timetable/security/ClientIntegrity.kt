package com.classing.wear.timetable.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.classing.wear.timetable.BuildConfig
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

object ClientIntegrity {
    const val PLATFORM_WEAR = "ANDROID_WEAR"
    private const val CHECK_PATH = "/api/v1/client/signature/check"
    private const val TRUST_CACHE_TTL_MS = 5 * 60 * 1_000L
    private const val SIGNATURE_ERROR_MESSAGE = "签名异常，客户端可能被非法修改，已禁止使用在线功能"

    @Volatile private var trustCache: TrustCache? = null

    fun snapshot(context: Context, platform: String = PLATFORM_WEAR): ClientIntegritySnapshot {
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
        platform: String = PLATFORM_WEAR,
    ) {
        if (BuildConfig.DEBUG) return
        snapshot(context, platform).headers().forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }
    }

    suspend fun ensureTrusted(
        context: Context,
        baseUrl: String,
        platform: String = PLATFORM_WEAR,
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
                if (status !in 200..299) {
                    val error = runCatching { JSONObject(body) }.getOrNull()
                    val detail = error?.optString("code").orEmpty().ifBlank { "HTTP $status" }
                    throw ClientSignatureException("$SIGNATURE_ERROR_MESSAGE ($detail)")
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
