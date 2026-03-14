package com.classing.shared.sync

object WearDataLayerContracts {
    const val PATH_SYNC_LESSONS = "/classing/mobile_sync_lessons"
    const val PATH_SYNC_ACK = "/classing/wear_sync_ack"
    const val PATH_SYNC_REQUEST = "/classing/request_mobile_sync"

    const val KEY_PAYLOAD = "payload"
    const val KEY_FORMAT = "format"
    const val KEY_TIMEZONE = "timezone"
    const val KEY_GENERATED_AT = "generatedAt"
    const val KEY_UPDATED_AT = "updatedAt"

    const val KEY_SUCCESS = "success"
    const val KEY_REQUESTED_LESSON_COUNT = "requestedLessonCount"
    const val KEY_APPLIED_LESSON_COUNT = "appliedLessonCount"
    const val KEY_SYNCED_AT = "syncedAt"
    const val KEY_SOURCE = "source"
    const val KEY_ERROR = "error"

    const val SOURCE_WEARABLE_API = "WEARABLE_API"
    const val SOURCE_WEAROS_APP = "WEAROS_APP"
}

