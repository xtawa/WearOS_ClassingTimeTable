package com.classing.shared.sync

object CloudSyncContracts {
    const val DOCUMENT_FORMAT = "classing_cloud_sync_v1"
    const val DEFAULT_REMOTE_PATH = "/classing/classing_sync.json"
    const val DEFAULT_DRIVE_FILE_NAME = "classing_sync.json"

    const val TRIGGER_APP_START = "APP_START"
    const val TRIGGER_FOREGROUND_TICK = "FOREGROUND_TICK"
    const val TRIGGER_SETTINGS_CHANGED = "SETTINGS_CHANGED"
    const val TRIGGER_MANUAL = "MANUAL"
    const val TRIGGER_WEAR_REQUEST = "WEAR_REQUEST"

    const val KEY_FORMAT = "format"
    const val KEY_UPDATED_AT = "updatedAt"
    const val KEY_REVISION = "revision"
    const val KEY_SOURCE = "source"
    const val KEY_TIMETABLE = "timetable"
    const val KEY_MOBILE_SETTINGS = "mobileSettings"
    const val KEY_WEAR_SETTINGS = "wearSettings"
    const val KEY_LESSONS = "lessons"
    const val KEY_WEEK_NUMBER_MODE = "weekNumberMode"
    const val KEY_SEMESTER_WEEK_START_DATE = "semesterWeekStartDate"
    const val KEY_SETTINGS_PAYLOAD = "settings"
    const val KEY_NAMESPACE_UPDATED_AT = "settingsUpdatedAt"

    const val KEY_CLOUD_PROVIDER = "cloudProvider"
    const val KEY_DRIVE_FILE_NAME = "driveFileName"
    const val KEY_DRIVE_ACCESS_TOKEN = "driveAccessToken"
    const val KEY_DRIVE_ACCESS_TOKEN_EXPIRE_AT = "driveAccessTokenExpireAt"
}
