package com.classing.shared.sync

enum class CloudProvider(val wireValue: String) {
    WEBDAV("WEBDAV"),
    GOOGLE_DRIVE("GOOGLE_DRIVE"),
    OFFICIAL("OFFICIAL"),
    ;

    companion object {
        fun fromWire(raw: String?): CloudProvider {
            return entries.firstOrNull { it.wireValue == raw?.trim()?.uppercase() } ?: WEBDAV
        }
    }
}
