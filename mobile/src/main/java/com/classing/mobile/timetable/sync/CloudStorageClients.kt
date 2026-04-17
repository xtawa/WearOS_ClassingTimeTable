package com.xtawa.classingtime.sync

interface MobileCloudStorageClient {
    suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit>
    suspend fun readJson(config: CloudRuntimeConfig): Result<String?>
    suspend fun writeJson(config: CloudRuntimeConfig, payload: String): Result<Unit>
}

class WebDavCloudStorageClient(
    private val webDavHttpClient: WebDavHttpClient = WebDavHttpClient(),
) : MobileCloudStorageClient {
    override suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> {
        return webDavHttpClient.testConnection(config)
    }

    override suspend fun readJson(config: CloudRuntimeConfig): Result<String?> {
        return webDavHttpClient.readJson(config)
    }

    override suspend fun writeJson(config: CloudRuntimeConfig, payload: String): Result<Unit> {
        return webDavHttpClient.writeJson(config, payload)
    }
}

class GoogleDriveCloudStorageClient(
    private val googleDriveHttpClient: GoogleDriveHttpClient = GoogleDriveHttpClient(),
) : MobileCloudStorageClient {
    override suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> {
        return googleDriveHttpClient.testConnection(config)
    }

    override suspend fun readJson(config: CloudRuntimeConfig): Result<String?> {
        return googleDriveHttpClient.readJson(config)
    }

    override suspend fun writeJson(config: CloudRuntimeConfig, payload: String): Result<Unit> {
        return googleDriveHttpClient.writeJson(config, payload)
    }
}
