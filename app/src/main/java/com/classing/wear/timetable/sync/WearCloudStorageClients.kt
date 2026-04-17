package com.classing.wear.timetable.sync

interface WearCloudStorageClient {
    suspend fun readJson(config: WearCloudConfig): Result<String?>
}

class WearWebDavStorageClient(
    private val wearWebDavClient: WearWebDavClient = WearWebDavClient(),
) : WearCloudStorageClient {
    override suspend fun readJson(config: WearCloudConfig): Result<String?> {
        return wearWebDavClient.readJson(config)
    }
}

class WearGoogleDriveStorageClient(
    private val wearGoogleDriveClient: WearGoogleDriveClient = WearGoogleDriveClient(),
) : WearCloudStorageClient {
    override suspend fun readJson(config: WearCloudConfig): Result<String?> {
        return wearGoogleDriveClient.readJson(config)
    }
}
