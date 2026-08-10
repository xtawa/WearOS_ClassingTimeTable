package com.classing.wear.timetable.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * Runtime capability probe for the host device.
 *
 * Not every watch that can install this APK ships a usable Google Play services stack:
 * several China-market Wear OS builds (e.g. HyperOS-skinned units) either omit GMS or keep it
 * disabled, which makes the phone <-> watch Data Layer permanently unavailable. Probing the
 * platform lets the app degrade to independent (official cloud) mode instead of retrying calls
 * that can never succeed.
 */
object DevicePlatformCapabilities {
    private const val GMS_PACKAGE = "com.google.android.gms"

    /** True when the app really runs on a watch form factor. */
    fun isWatchFormFactor(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    /** True when a Google Play services package is installed and enabled on this device. */
    fun hasGooglePlayServices(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(GMS_PACKAGE, 0).enabled
    }.getOrDefault(false)

    /** The Wearable Data Layer is backed by GMS, so it is only usable when GMS is present. */
    fun isDataLayerAvailable(context: Context): Boolean = hasGooglePlayServices(context)
}
