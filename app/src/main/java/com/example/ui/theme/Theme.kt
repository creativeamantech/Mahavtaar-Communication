package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = S2SCyanLight,
    onPrimary = S2SDarkBackground,
    secondary = S2SIndigoSecondary,
    onSecondary = S2SDarkTextPrimary,
    tertiary = S2SVioletAccent,
    background = S2SDarkBackground,
    surface = S2SDarkSurface,
    surfaceVariant = S2SDarkSurfaceVariant,
    onBackground = S2SDarkTextPrimary,
    onSurface = S2SDarkTextPrimary,
    onSurfaceVariant = S2SDarkTextSecondary,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = S2SCyanPrimary,
    onPrimary = Color.White,
    secondary = S2SIndigoSecondary,
    onSecondary = Color.White,
    tertiary = S2SVioletAccent,
    background = S2SLightBackground,
    surface = S2SLightSurface,
    surfaceVariant = S2SLightSurfaceVariant,
    onBackground = S2SLightTextPrimary,
    onSurface = S2SLightTextPrimary,
    onSurfaceVariant = S2SLightTextSecondary,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
