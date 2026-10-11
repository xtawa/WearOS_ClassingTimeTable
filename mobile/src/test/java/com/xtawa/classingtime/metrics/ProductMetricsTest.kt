package com.xtawa.classingtime.metrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ProductMetricsTest {
 @Test fun optInRetentionBoundsAndRevocation() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  ProductMetrics.setEnabled(context,false)
  ProductMetrics.record(context,ProductEvent.CREATION_STARTED)
  assertEquals(0,JSONObject(ProductMetrics.export(context)).getJSONArray("events").length())
  ProductMetrics.setEnabled(context,true)
  ProductMetrics.record(context,ProductEvent.ACTIVE_DAY,now=TimeUnit.DAYS.toMillis(100))
  ProductMetrics.record(context,ProductEvent.ACTIVE_DAY,now=TimeUnit.DAYS.toMillis(100))
  assertEquals(1,JSONObject(ProductMetrics.export(context)).getJSONArray("events").length())
  ProductMetrics.record(context,ProductEvent.CREATION_COMPLETED,elapsedMs=20000,now=TimeUnit.DAYS.toMillis(129))
  val data=JSONObject(ProductMetrics.export(context));assertEquals(1,data.getJSONArray("events").length()); assertFalse(data.toString().contains("title"))
  ProductMetrics.setEnabled(context,false)
  assertEquals(0,JSONObject(ProductMetrics.export(context)).getJSONArray("events").length()); assertEquals("",JSONObject(ProductMetrics.export(context)).getString("installation"))
 }
}
