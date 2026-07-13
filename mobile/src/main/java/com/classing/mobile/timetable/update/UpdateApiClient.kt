package com.xtawa.classingtime.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.xtawa.classingtime.account.AccountApiClient
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

data class ClientAnnouncement(
    val id: String,
    val title: String,
    val content: String,
    val publishAt: Long,
)

data class AppUpdateRelease(
    val id: String,
    val versionCode: Long,
    val versionName: String,
    val minSupportedVersionCode: Long,
    val title: String,
    val changelog: String,
    val mandatory: Boolean,
    val artifactFileName: String,
    val artifactSize: Long,
    val sha256: String,
    val downloadUrl: String,
	val channel: ReleaseChannel,
)

data class UpdateCheckResult(
    val updateAvailable: Boolean,
	val forceUpdate: Boolean,
    val release: AppUpdateRelease?,
)

enum class ReleaseChannel(val apiValue: String) {
	STABLE("STABLE"),
	BETA("BETA"),
}

object ReleaseChannelPreference {
	private const val PREFS = "classing_update_preferences"
	private const val KEY_CHANNEL = "release_channel"

	fun load(context: Context): ReleaseChannel = runCatching {
		ReleaseChannel.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHANNEL, ReleaseChannel.STABLE.name).orEmpty())
	}.getOrDefault(ReleaseChannel.STABLE)

	fun save(context: Context, channel: ReleaseChannel) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CHANNEL, channel.name).apply()
	}
}

class ClientRequestRateLimitException(val retryAfterSeconds: Long) : IllegalStateException(
    "Request limit reached; retry in $retryAfterSeconds seconds",
)

internal object ClientRequestRateLimiter {
    private const val WINDOW_MILLIS = 60_000L
    private const val MAX_REQUESTS = 3
    private val requests = mutableMapOf<String, ArrayDeque<Long>>()
	private val cooldownUntil = mutableMapOf<String, Long>()

    @Synchronized
    fun acquire(key: String, now: Long = System.currentTimeMillis()): Result<Unit> {
		cooldownUntil[key]?.let { until ->
			if (until > now) {
				return Result.failure(ClientRequestRateLimitException(((until - now + 999L) / 1_000L).coerceAtLeast(1L)))
			}
			cooldownUntil.remove(key)
		}
        val history = requests.getOrPut(key) { ArrayDeque() }
        while (history.isNotEmpty() && now - history.first() >= WINDOW_MILLIS) history.removeFirst()
        if (history.size >= MAX_REQUESTS) {
            val retryAfter = ((WINDOW_MILLIS - (now - history.first()) + 999L) / 1_000L).coerceAtLeast(1L)
            return Result.failure(ClientRequestRateLimitException(retryAfter))
        }
        history.addLast(now)
        return Result.success(Unit)
    }

	@Synchronized
	fun recordCooldown(key: String, retryAfterSeconds: Long, now: Long = System.currentTimeMillis()) {
		cooldownUntil[key] = maxOf(cooldownUntil[key] ?: 0L, now + retryAfterSeconds.coerceAtLeast(1L) * 1_000L)
	}
}

enum class InstallLaunchResult {
    INSTALLER_OPENED,
    PERMISSION_REQUIRED,
}

class UpdateApiClient(
    private val baseUrl: String = AccountApiClient.BASE_URL,
) {
    suspend fun fetchAnnouncements(): Result<List<ClientAnnouncement>> {
        ClientRequestRateLimiter.acquire("announcements").exceptionOrNull()?.let { return Result.failure(it) }
		return requestJson("announcements", "/api/v1/client/announcements?platform=ANDROID_MOBILE").map { root ->
        val items = root.optJSONArray("announcements") ?: return@map emptyList()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    ClientAnnouncement(
                        id = item.optString("announcementId"),
                        title = item.optString("title"),
                        content = item.optString("content"),
                        publishAt = item.optLong("publishAt"),
                    ),
                )
            }
        }
        }
    }

	suspend fun checkLatest(versionCode: Long, channel: ReleaseChannel = ReleaseChannel.STABLE): Result<UpdateCheckResult> {
        ClientRequestRateLimiter.acquire("releases").exceptionOrNull()?.let { return Result.failure(it) }
		return requestJson(
			"releases",
			"/api/v1/client/releases/latest?platform=ANDROID_MOBILE&channel=${channel.apiValue}&versionCode=$versionCode",
        ).map { root ->
            val releaseJson = root.optJSONObject("release")
            UpdateCheckResult(
                updateAvailable = root.optBoolean("updateAvailable", false),
				forceUpdate = root.optBoolean("forceUpdate", false),
                release = releaseJson?.let(::parseRelease),
            )
        }
    }

    suspend fun downloadRelease(
        context: Context,
        release: AppUpdateRelease,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
			ClientRequestRateLimiter.acquire("release-download").getOrThrow()
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "classing-${release.versionCode}.apk")
            val partial = File(directory, "${target.name}.part")
			if (partial.length() > release.artifactSize && release.artifactSize > 0L) partial.delete()
			val resumeAt = partial.length()
			val requiredBytes = (release.artifactSize - resumeAt).coerceAtLeast(0L)
			require(directory.usableSpace > requiredBytes + 8L * 1024L * 1024L) { "not enough storage space for update" }
            val url = if (release.downloadUrl.startsWith("http://") || release.downloadUrl.startsWith("https://")) {
                release.downloadUrl
            } else {
                baseUrl.trimEnd('/') + "/" + release.downloadUrl.trimStart('/')
            }
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
				if (resumeAt > 0L) connection.setRequestProperty("Range", "bytes=$resumeAt-")
                val status = connection.responseCode
				if (status == 429) {
					val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceAtLeast(1L) ?: 60L
					ClientRequestRateLimiter.recordCooldown("release-download", retryAfter)
					throw ClientRequestRateLimitException(retryAfter)
				}
                if (status !in 200..299) {
                    val error = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    error("HTTP $status ${error.take(160)}".trim())
                }
				val append = status == HttpURLConnection.HTTP_PARTIAL && resumeAt > 0L
				if (status == HttpURLConnection.HTTP_PARTIAL) {
					require(connection.getHeaderField("Content-Range")?.startsWith("bytes $resumeAt-") == true) { "invalid range response" }
				}
				if (!append && resumeAt > 0L) partial.delete()
				val startingBytes = if (append) resumeAt else 0L
				val responseBytes = connection.getHeaderFieldLong("Content-Length", -1L)
				val total = release.artifactSize.takeIf { it > 0L }
					?: (responseBytes.takeIf { it > 0L }?.plus(startingBytes) ?: 0L)
                val digest = MessageDigest.getInstance("SHA-256")
				if (append) partial.inputStream().use { existing ->
					val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
					while (true) {
						val count = existing.read(buffer)
						if (count < 0) break
						digest.update(buffer, 0, count)
					}
				}
				var downloaded = startingBytes
                var lastPercent = -1
                connection.inputStream.use { input ->
					FileOutputStream(partial, append).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                            }
                        }
                        output.fd.sync()
                    }
                }
				if (release.artifactSize > 0L && downloaded != release.artifactSize) {
					partial.delete()
					error("downloaded file size does not match release metadata")
				}
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
				if (!actualHash.equals(release.sha256, ignoreCase = true)) {
					partial.delete()
					error("downloaded file checksum does not match release metadata")
				}
                target.delete()
                require(partial.renameTo(target)) { "downloaded file could not be finalized" }
                withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                target
            } finally {
                connection.disconnect()
            }
        }
    }

	private suspend fun requestJson(rateKey: String, path: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
				if (status == 429) {
					val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceAtLeast(1L) ?: 60L
					ClientRequestRateLimiter.recordCooldown(rateKey, retryAfter)
					throw ClientRequestRateLimitException(retryAfter)
				}
				if (status !in 200..299) error("HTTP $status ${body.take(160)}".trim())
                JSONObject(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseRelease(item: JSONObject): AppUpdateRelease = AppUpdateRelease(
        id = item.optString("releaseId"),
        versionCode = item.optLong("versionCode"),
        versionName = item.optString("versionName"),
        minSupportedVersionCode = item.optLong("minSupportedVersionCode"),
        title = item.optString("title"),
        changelog = item.optString("changelog"),
        mandatory = item.optBoolean("mandatory", false),
        artifactFileName = item.optString("artifactFileName"),
        artifactSize = item.optLong("artifactSize"),
        sha256 = item.optString("sha256"),
        downloadUrl = item.optString("downloadUrl"),
		channel = runCatching { ReleaseChannel.valueOf(item.optString("channel", ReleaseChannel.STABLE.name)) }.getOrDefault(ReleaseChannel.STABLE),
    )
}

fun launchUpdateInstaller(context: Context, apk: File): InstallLaunchResult {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallLaunchResult.PERMISSION_REQUIRED
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    return InstallLaunchResult.INSTALLER_OPENED
}
