package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = LishPdfPrimary,
    onPrimary = LishPdfOnPrimary,
    primaryContainer = LishDarkSurfaceVariant,
    onPrimaryContainer = LishDarkTextPrimary,
    secondary = LishPdfSecondary,
    onSecondary = LishPdfOnSecondary,
    secondaryContainer = LishDarkSurfaceVariant,
    onSecondaryContainer = LishDarkTextPrimary,
    tertiary = LishPdfTertiary,
    onTertiary = LishPdfOnTertiary,
    background = LishDarkBackground,
    onBackground = LishDarkTextPrimary,
    surface = LishDarkSurface,
    onSurface = LishDarkTextPrimary,
    surfaceVariant = LishDarkSurfaceVariant,
    onSurfaceVariant = LishDarkTextSecondary,
    outline = LishDarkBorder,
    outlineVariant = LishDarkBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LishPdfPrimary,
    onPrimary = LishPdfOnPrimary,
    primaryContainer = LishPdfPrimaryContainer,
    onPrimaryContainer = LishPdfOnPrimaryContainer,
    secondary = LishPdfSecondary,
    onSecondary = LishPdfOnSecondary,
    secondaryContainer = LishPdfSecondaryContainer,
    onSecondaryContainer = LishPdfOnSecondaryContainer,
    tertiary = LishPdfTertiary,
    onTertiary = LishPdfOnTertiary,
    background = CleanWhiteBackground,
    onBackground = LishPdfTextPrimary,
    surface = CleanWhiteSurface,
    onSurface = LishPdfTextPrimary,
    surfaceVariant = CleanWhiteSurfaceVariant,
    onSurfaceVariant = LishPdfTextSecondary,
    outline = LishPdfBorder,
    outlineVariant = LishPdfOutlineVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Default to Putih Bersih (Clean White)
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

