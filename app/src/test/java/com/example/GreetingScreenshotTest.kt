package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.LatencyMetrics
import com.example.data.model.S2SState
import com.example.ui.component.AudioOrbVisualizer
import com.example.ui.component.MetricsHudCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
          Column {
            MetricsHudCard(
              metrics = LatencyMetrics(
                sttLatencyMs = 175L,
                ttftMs = 110L,
                ttsLatencyMs = 145L,
                totalLatencyMs = 430L,
                targetMet = true,
                isReal = true
              )
            )
            AudioOrbVisualizer(
              state = S2SState.LISTENING,
              amplitude = 0.65f,
              isSpeaking = false
            )
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
