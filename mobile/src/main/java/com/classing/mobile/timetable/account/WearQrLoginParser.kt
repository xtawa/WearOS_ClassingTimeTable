package com.xtawa.classingtime.account

import java.net.URI
import java.net.URLDecoder

internal fun parseWearLoginAuthorizationId(rawValue: String): String? {
    val uri = runCatching { URI(rawValue.trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("classing", ignoreCase = true) || !uri.host.equals("wear-login", ignoreCase = true)) {
        return null
    }
    val authorizationId = uri.rawQuery.orEmpty()
        .split('&')
        .asSequence()
        .mapNotNull { part ->
            val split = part.split('=', limit = 2)
            if (split.size != 2) null else split[0] to URLDecoder.decode(split[1], Charsets.UTF_8.name())
        }
        .firstOrNull { it.first == "authorizationId" }
        ?.second
        ?.trim()
        .orEmpty()
    return authorizationId.takeIf {
        it.length in 8..128 && it.all { char -> char.isLetterOrDigit() || char == '_' || char == '-' }
    }
}
