package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudSyncV2

/** Missing fields in this client's settings snapshot are not deletions by another platform. */
internal fun shouldDeleteMissingCloudRecord(domain: String): Boolean = domain !in setOf(
    CloudSyncV2.DOMAIN_MOBILE_SETTINGS,
    CloudSyncV2.DOMAIN_WEAR_SETTINGS,
    CloudSyncV2.DOMAIN_CLOUD_CONFIG,
)
