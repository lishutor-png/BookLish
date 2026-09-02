package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MinimalPrimary,
    onPrimary = MinimalOnPrimary,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer,
    secondary = MinimalSecondary,
    onSecondary = MinimalOnSecondary,
    secondaryContainer = MinimalSecondaryContainer,
    onSecondaryContainer = MinimalOnSecondaryContainer,
    tertiary = MinimalTertiary,
    onTertiary = MinimalOnTertiary,
    background = MinimalDarkBackground,
    onBackground = MinimalTextPrimary,
    surface = MinimalDarkSurface,
    onSurface = MinimalTextPrimary,
    surfaceVariant = MinimalDarkSurfaceVariant,
    onSurfaceVariant = MinimalTextSecondary,
    outline = MinimalOutline,
    outlineVariant = MinimalOutlineVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MinimalLightPrimary,
    onPrimary = MinimalLightOnPrimary,
    primaryContainer = MinimalLightPrimaryContainer,
    onPrimaryContainer = MinimalLightOnPrimaryContainer,
    secondary = MinimalSecondary,
    onSecondary = MinimalOnSecondary,
    secondaryContainer = MinimalSecondaryContainer,
    onSecondaryContainer = MinimalOnSecondaryContainer,
    tertiary = MinimalTertiary,
    onTertiary = MinimalOnTertiary,
    background = MinimalLightBackground,
    onBackground = MinimalLightTextPrimary,
    surface = MinimalLightSurface,
    onSurface = MinimalLightTextPrimary,
    surfaceVariant = MinimalLightSurfaceVariant,
    onSurfaceVariant = MinimalLightTextSecondary,
    outline = MinimalLightBorder,
    outlineVariant = MinimalLightBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to Clean Minimalism dark aesthetic
  dynamicColor: Boolean = false,
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
