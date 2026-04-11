package com.classing.shared.sync

object WearDataLayerContracts {
    const val PATH_SYNC_LESSONS = "/classing/mobile_sync_lessons"
    const val PATH_SYNC_ACK = "/classing/wear_sync_ack"
    const val PATH_SYNC_REQUEST = "/classing/request_mobile_sync"
    const val PATH_CLOUD_CONFIG = "/classing/cloud_config"
    const val PATH_WEAR_SETTINGS_SNAPSHOT = "/classing/wear_settings_snapshot"
    const val PATH_PHONE_CLOUD_SYNC_REQUEST = "/classing/phone_cloud_sync_request"

    const val KEY_PAYLOAD = "payload"
    const val KEY_FORMAT = "format"
    const val KEY_TIMEZONE = "timezone"
    const val KEY_GENERATED_AT = "generatedAt"
    const val KEY_REQUESTED_AT = "requestedAt"
    const val KEY_UPDATED_AT = "updatedAt"
    const val KEY_REVISION = "revision"

    const val KEY_SUCCESS = "success"
    const val KEY_REQUESTED_LESSON_COUNT = "requestedLessonCount"
    const val KEY_APPLIED_LESSON_COUNT = "appliedLessonCount"
    const val KEY_SYNCED_AT = "syncedAt"
    const val KEY_SOURCE = "source"
    const val KEY_ERROR = "error"
    const val KEY_TRIGGER = "trigger"
    const val KEY_CONFIG_PAYLOAD = "configPayload"
    const val KEY_SETTINGS_PAYLOAD = "settingsPayload"
    const val KEY_REQUEST_PAYLOAD = "requestPayload"
    const val KEY_WEAR_WEBDAV_SNAPSHOT = "wearWebdavSnapshot"

    const val SOURCE_WEARABLE_API = "WEARABLE_API"
    const val SOURCE_WEAROS_APP = "WEAROS_APP"
    const val SOURCE_CLOUD_SYNC = "CLOUD_SYNC"
}
