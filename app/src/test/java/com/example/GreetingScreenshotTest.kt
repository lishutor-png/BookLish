package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.BookEntity
import com.example.ui.screens.HomeScreen
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
  fun home_screen_screenshot() {
    val sampleBooks = listOf(
      BookEntity(
        id = 1,
        title = "Seni Belajar Efektif & Membaca Mendalam",
        author = "BookLish Academy",
        fileUri = "",
        localFilePath = "",
        fileType = "PDF",
        totalPages = 6,
        currentPage = 2,
        progressPercent = 33,
        coverColorHex = "#0284C7",
        isSample = true
      ),
      BookEntity(
        id = 2,
        title = "Panduan Pintar & Ringkasan Materi PDF",
        author = "Dr. Arisandi Prasetya",
        fileUri = "",
        localFilePath = "",
        fileType = "PDF",
        totalPages = 4,
        currentPage = 0,
        progressPercent = 0,
        coverColorHex = "#059669",
        isSample = true
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        HomeScreen(
          books = sampleBooks,
          onOpenBook = {},
          onImportUri = {},
          onDeleteBook = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home_screen.png")
  }
}
