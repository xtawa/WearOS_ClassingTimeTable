package com.xtawa.classingtime.sync

interface MobileCloudStorageClient {
    suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit>
    suspend fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult>
    suspend fun writeJson(config: CloudRuntimeConfig, payload: String, expectedVersion: String?): Result<Unit>
}

data class CloudReadResult(val payload: String?, val versionToken: String?)

class CloudWriteConflictException(message: String = "Cloud document changed during sync") : Exception(message)
class UnsafeCloudStorageException(message: String) : Exception(message)

internal suspend fun <T> retryConditionalCloudUpdate(
    maxAttempts: Int,
    block: suspend (attempt: Int) -> T,
): T {
    require(maxAttempts > 0)
    var lastConflict: CloudWriteConflictException? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block(attempt)
        } catch (conflict: CloudWriteConflictException) {
            lastConflict = conflict
        }
    }
    throw lastConflict ?: CloudWriteConflictException()
}

class WebDavCloudStorageClient(
    private val webDavHttpClient: WebDavHttpClient = WebDavHttpClient(),
) : MobileCloudStorageClient {
    override suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> {
        return webDavHttpClient.testConnection(config)
    }

    override suspend fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult> {
        return webDavHttpClient.readJson(config)
    }

    override suspend fun writeJson(config: CloudRuntimeConfig, payload: String, expectedVersion: String?): Result<Unit> {
        return webDavHttpClient.writeJson(config, payload, expectedVersion)
    }
}

class GoogleDriveCloudStorageClient(
    private val googleDriveHttpClient: GoogleDriveHttpClient = GoogleDriveHttpClient(),
) : MobileCloudStorageClient {
    override suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> {
        return googleDriveHttpClient.testConnection(config)
    }

    override suspend fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult> {
        return googleDriveHttpClient.readJson(config)
    }

    override suspend fun writeJson(config: CloudRuntimeConfig, payload: String, expectedVersion: String?): Result<Unit> {
        return googleDriveHttpClient.writeJson(config, payload, expectedVersion)
    }
}

class OfficialCloudStorageClient(
    private val officialCloudHttpClient: OfficialCloudHttpClient = OfficialCloudHttpClient(),
) : MobileCloudStorageClient {
    override suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> {
        return officialCloudHttpClient.testConnection(config)
    }

    override suspend fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult> {
        return officialCloudHttpClient.readJson(config)
    }

    override suspend fun writeJson(config: CloudRuntimeConfig, payload: String, expectedVersion: String?): Result<Unit> {
        return officialCloudHttpClient.writeJson(config, payload, expectedVersion)
    }
}
