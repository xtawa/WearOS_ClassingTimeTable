package com.classing.shared.announcements
import kotlin.test.*
class AnnouncementPolicyTest {
 @Test fun oldVersionAndWrongEditionNeverReceiveLaunchPopup() {
  val p=AnnouncementPolicy(minVersionCode=113,versionNames=listOf("1.1.3"),editions=listOf("CN"))
  assertFalse(p.targets(112,"1.1.3","CN"));assertFalse(p.targets(113,"1.1.3","GLOBAL"));assertTrue(p.targets(113,"1.1.3","CN"))
 }
 @Test fun limitsAndCooldownApplyTogether() {
  val p=AnnouncementPolicy(frequency="EVERY_LAUNCH",maxDisplays=3,cooldownSeconds=60)
  assertFalse(p.eligible(3,1000,90000));assertFalse(p.eligible(1,1000,60000));assertTrue(p.eligible(1,1000,61000))
 }
 @Test fun onceRevisionAndVersionKeysDoNotAccidentallyReset() {
  val p=AnnouncementPolicy(frequency="ONCE")
  assertEquals(p.counterKey("a",1,113),p.counterKey("a",2,114));assertFalse(p.eligible(1,0,10000))
  assertNotEquals(p.copy(resetOnUpdate=true).counterKey("a",1,113),p.copy(resetOnUpdate=true).counterKey("a",2,113))
  assertNotEquals(p.copy(frequency="ONCE_PER_VERSION").counterKey("a",1,113),p.copy(frequency="ONCE_PER_VERSION").counterKey("a",1,114))
 }
}
