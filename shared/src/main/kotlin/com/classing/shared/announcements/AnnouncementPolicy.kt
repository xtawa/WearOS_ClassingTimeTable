package com.classing.shared.announcements

data class AnnouncementPolicy(
 val frequency: String = "EVERY_LAUNCH", val maxDisplays: Int = 0, val cooldownSeconds: Int = 0,
 val minVersionCode: Long = 0, val maxVersionCode: Long = 0,
 val versionCodes: List<Long> = emptyList(), val versionNames: List<String> = emptyList(), val editions: List<String> = emptyList(),
 val delaySeconds: Int = 0, val minReadSeconds: Int = 0, val autoCloseSeconds: Int = 0,
 val style: String = "CENTER", val dismissible: Boolean = true, val resetOnUpdate: Boolean = false,
 val closeLabel: String = "", val actionLabel: String = "", val actionUrl: String = "",
) {
 fun targets(code: Long, name: String, edition: String): Boolean =
  (minVersionCode == 0L || code >= minVersionCode) && (maxVersionCode == 0L || code <= maxVersionCode) &&
  (versionCodes.isEmpty() || code in versionCodes) && (versionNames.isEmpty() || name in versionNames) && (editions.isEmpty() || edition in editions)
 fun eligible(count: Int, lastShown: Long, now: Long): Boolean {
  if (frequency !in setOf("ONCE", "EVERY_LAUNCH", "ONCE_PER_VERSION", "LIMITED")) return false
  if (frequency in setOf("ONCE", "ONCE_PER_VERSION") && count > 0) return false
  if (frequency == "LIMITED" && maxDisplays < 1 || maxDisplays > 0 && count >= maxDisplays) return false
  return lastShown == 0L || now - lastShown >= cooldownSeconds.coerceIn(0,31536000).toLong() * 1000
 }
 fun counterKey(id: String, revision: Long, code: Long): String = id +
  (if (resetOnUpdate) ":r$revision" else "") + (if (frequency == "ONCE_PER_VERSION") ":v$code" else "")
}
