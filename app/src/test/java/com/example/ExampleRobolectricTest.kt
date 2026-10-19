package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("LishPDF", appName)
    val openWith = context.getString(R.string.open_with_pdf_reader)
    assertEquals("Buka dengan LishPDF", openWith)
  }

  @Test
  fun `intent filter resolves ACTION_VIEW for pdf mimeType`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
      setDataAndType(android.net.Uri.parse("content://com.android.providers.media.documents/document/123"), "application/pdf")
      addCategory(android.content.Intent.CATEGORY_DEFAULT)
    }
    val activities = context.packageManager.queryIntentActivities(intent, 0)
    val matched = activities.any { it.activityInfo.packageName == context.packageName }
    org.junit.Assert.assertTrue("LishPDF MainActivity should resolve application/pdf VIEW intents", matched)
  }

  @Test
  fun `intent filter resolves ACTION_SEND for pdf mimeType`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
      type = "application/pdf"
      addCategory(android.content.Intent.CATEGORY_DEFAULT)
    }
    val activities = context.packageManager.queryIntentActivities(intent, 0)
    val matched = activities.any { it.activityInfo.packageName == context.packageName }
    org.junit.Assert.assertTrue("LishPDF MainActivity should resolve application/pdf SEND intents", matched)
  }

  @Test
  fun `default reading view mode is CONTINUOUS_VERTICAL`() {
    val settings = com.example.data.model.ReadingSettings()
    assertEquals(com.example.data.model.ReaderViewMode.CONTINUOUS_VERTICAL, settings.viewMode)
  }

  @Test
  fun `multi-page text search finds matches across multiple pages`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleBook = com.example.data.model.BookEntity(
      title = "Panduan Pintar LishPDF & Fitur Coretan",
      localFilePath = "/data/user/0/com.example/files/books/panduan_lengkap_lishpdf.pdf",
      fileType = "PDF",
      totalPages = 4
    )
    val pagesText = com.example.data.parser.PdfTextExtractor.extractPagesText(context, sampleBook, 4)
    assertEquals(4, pagesText.size)

    // "bolpoin" appears on Page 1 (Hal 1), Page 2 (Hal 2), and Page 4 (Hal 4)
    val query = "bolpoin"
    val matchingPages = mutableListOf<Int>()
    for ((pageIdx, text) in pagesText.withIndex()) {
      if (text.contains(query, ignoreCase = true)) {
        matchingPages.add(pageIdx)
      }
    }

    org.junit.Assert.assertTrue("Keyword 'bolpoin' should be found on multiple pages", matchingPages.size >= 2)
    org.junit.Assert.assertTrue("Should match on page 0", matchingPages.contains(0))
    org.junit.Assert.assertTrue("Should match on page 1", matchingPages.contains(1))
  }

  @Test
  fun `PdfTextExtractor extracts plain text from content stream bytes`() {
    val streamText = "BT /F1 12 Tf (Halo Dunia LishPDF) Tj ET"
    val extracted = com.example.data.parser.PdfTextExtractor.extractTextFromPdfContentStream(streamText.toByteArray())
    org.junit.Assert.assertTrue(extracted.contains("Halo Dunia LishPDF"))
  }
}
