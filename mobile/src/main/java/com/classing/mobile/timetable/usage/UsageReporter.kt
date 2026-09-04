package com.xtawa.classingtime.usage

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xtawa.classingtime.BuildConfig
import com.xtawa.classingtime.security.ClientIntegrity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object UsageReporter {
    private const val PREF_NAME = "classing_usage_collection"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_INSTALL_SERIAL = "install_serial"
    private const val UNIQUE_WORK_NAME = "classing_device_usage_upload"
    private const val REPORT_PATH = "/api/v1/client/device-usage"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun installationSerial(context: Context): String {
        val preferences = prefs(context)
        preferences.getString(KEY_INSTALL_SERIAL, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val value = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_INSTALL_SERIAL, value).apply()
        return value
    }

    fun onAppStart(context: Context) {
        if (!isEnabled(context)) {
            cancelPeriodicUpload(context)
            return
        }
        schedulePeriodicUpload(context)
        uploadNow(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            schedulePeriodicUpload(context)
            uploadNow(context)
        } else {
            cancelPeriodicUpload(context)
        }
    }

    fun uploadNow(context: Context) {
        if (!isEnabled(context)) return
        val appContext = context.applicationContext
        uploadScope.launch {
            runCatching { upload(appContext) }
        }
    }

    internal suspend fun upload(context: Context) = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) return@withContext
        val baseUrl = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext

        ClientIntegrity.ensureTrusted(context, baseUrl, ClientIntegrity.PLATFORM_MOBILE).getOrThrow()
        val body = buildPayload(context).toString().toByteArray(Charsets.UTF_8)
        val connection = (URL(baseUrl + REPORT_PATH).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            ClientIntegrity.applyHeaders(this, context, ClientIntegrity.PLATFORM_MOBILE)
            doOutput = true
            setFixedLengthStreamingMode(body.size)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = (connection.errorStream ?: connection.inputStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                throw UsageUploadException(status, message.ifBlank { "HTTP $status" })
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(context: Context): JSONObject {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return JSONObject()
            .put("serial", installationSerial(context))
            .put("androidId", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty())
            .put("installedAt", packageInfo.firstInstallTime)
            .put("timezone", TimeZone.getDefault().id)
            .put("manufacturer", Build.MANUFACTURER.orEmpty())
            .put("brand", Build.BRAND.orEmpty())
            .put("model", Build.MODEL.orEmpty())
            .put("device", Build.DEVICE.orEmpty())
            .put("product", Build.PRODUCT.orEmpty())
            .put("hardware", Build.HARDWARE.orEmpty())
            .put("board", Build.BOARD.orEmpty())
            .put("osVersion", Build.VERSION.RELEASE.orEmpty())
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("securityPatch", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH.orEmpty() else "")
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.orEmpty().toList()))
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("appVersionName", packageInfo.versionName.orEmpty())
            .put("appVersionCode", appVersionCode)
    }

    private fun schedulePeriodicUpload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UsageUploadWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancelPeriodicUpload(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}

class UsageUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!UsageReporter.isEnabled(applicationContext)) return Result.success()
        return try {
            UsageReporter.upload(applicationContext)
            Result.success()
        } catch (error: UsageUploadException) {
            if (error.statusCode == 429 || error.statusCode >= 500) Result.retry() else Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.success()
        }
    }
}

private class UsageUploadException(
    val statusCode: Int,
    message: String,
) : IOException(message)
