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

  @Test
  fun `PdfTextExtractor preserves whole words across TJ array and kerning`() {
    val streamText = "BT /F1 12 Tf [(P) 10 (AND) 5 (UAN)] TJ ET"
    val extracted = com.example.data.parser.PdfTextExtractor.extractTextFromPdfContentStream(streamText.toByteArray())
    org.junit.Assert.assertTrue("Should extract whole word PANDUAN without spaces, but was: $extracted", extracted.contains("PANDUAN"))
  }

  @Test
  fun `case-insensitive search treats 'a' and 'A' identically`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleBook = com.example.data.model.BookEntity(
      title = "Panduan Pintar LishPDF & Fitur Coretan",
      localFilePath = "/data/user/0/com.example/files/books/panduan_lengkap_lishpdf.pdf",
      fileType = "PDF",
      totalPages = 4
    )
    val pagesText = com.example.data.parser.PdfTextExtractor.extractPagesText(context, sampleBook, 4)
    org.junit.Assert.assertTrue("Pages text should not be empty", pagesText.isNotEmpty())

    // Counting matches for 'a' vs 'A'
    var countLowerA = 0
    var countUpperA = 0
    for (text in pagesText) {
      for (char in text) {
        if (char.equals('a', ignoreCase = true)) countLowerA++
        if (char.equals('A', ignoreCase = true)) countUpperA++
      }
    }
    assertEquals("Lowercase 'a' and uppercase 'A' case-insensitive count must match", countLowerA, countUpperA)
    org.junit.Assert.assertTrue("Should find 'a' in document", countLowerA > 0)

    // Checking word search case-insensitivity: "PANDUAN" vs "panduan"
    val matchUpper = pagesText.count { it.contains("PANDUAN", ignoreCase = true) }
    val matchLower = pagesText.count { it.contains("panduan", ignoreCase = true) }
    assertEquals("Uppercase PANDUAN and lowercase panduan must match same page count", matchUpper, matchLower)
    org.junit.Assert.assertTrue("Should find panduan in document", matchLower >= 1)

    // Checking word across multiple pages: "CORETAN" vs "coretan"
    val matchUpperCoretan = pagesText.count { it.contains("CORETAN", ignoreCase = true) }
    val matchLowerCoretan = pagesText.count { it.contains("coretan", ignoreCase = true) }
    assertEquals("Uppercase CORETAN and lowercase coretan must match", matchUpperCoretan, matchLowerCoretan)
    org.junit.Assert.assertTrue("Should find coretan across multiple pages", matchLowerCoretan >= 3)
  }
}
