package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LatencyMetrics
import com.example.data.model.S2SConfig
import com.example.data.model.S2SState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Speech to Speech", appName)
  }

  @Test
  fun `test latency metrics sub-800ms target validation`() {
    val sub800Metrics = LatencyMetrics(
      sttLatencyMs = 180L,
      ttftMs = 120L,
      ttsLatencyMs = 150L,
      totalLatencyMs = 450L,
      targetMet = true
    )
    assertTrue(sub800Metrics.totalLatencyMs < LatencyMetrics.LATENCY_TARGET_MS)
    assertTrue(sub800Metrics.targetMet)
  }

  @Test
  fun `test default s2s config parameters`() {
    val config = S2SConfig()
    assertEquals(16000, config.sampleRate)
    assertTrue(config.continuousConversation)
    assertTrue(config.allowBargeIn)
  }
}
