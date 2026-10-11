package com.xtawa.classingtime.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class WearAckOrderingTest {
 @Test fun earlyLateDuplicateWrongNodeAndExpiredAcknowledgements() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val prefs=context.getSharedPreferences("wear_sync_payload_baseline",Context.MODE_PRIVATE)
  // Protocol storage keys intentionally validated against real publisher state.
  fun pending(id:String,revision:Long,at:Long=System.currentTimeMillis()) { prefs.edit().putString("pending_baseline_$id",JSONObject().put("nodeId","watch").put("revision",revision).put("createdAt",at).put("baseline",JSONObject().put("lessons",JSONObject()).put("exceptions",JSONObject())).toString()).commit() }
  prefs.edit().clear().commit()
  assertFalse(WearDataLayerSyncPublisher.confirmBaselineFromAck(context,"early","watch"))
  pending("old",10);pending("new",20)
  assertFalse(WearDataLayerSyncPublisher.confirmBaselineFromAck(context,"new","other-watch"))
  assertTrue(WearDataLayerSyncPublisher.confirmBaselineFromAck(context,"new","watch"))
  assertFalse(WearDataLayerSyncPublisher.confirmBaselineFromAck(context,"old","watch"))
  assertFalse(WearDataLayerSyncPublisher.confirmBaselineFromAck(context,"new","watch"))
  pending("expired",30,1)
  assertFalse(WearDataLayerSyncPublisher.confirmBaselineFromAck(context,"expired","watch"))
 }
}
