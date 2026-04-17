package com.classing.shared.sync

enum class CloudProvider(val wireValue: String) {
    WEBDAV("WEBDAV"),
    GOOGLE_DRIVE("GOOGLE_DRIVE"),
    ;

    companion object {
        fun fromWire(raw: String?): CloudProvider {
            return entries.firstOrNull { it.wireValue == raw?.trim()?.uppercase() } ?: WEBDAV
        }
    }
}
