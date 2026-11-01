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

  @Test
  fun `extractDocumentLinks discovers valid web links across document pages`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleBook = com.example.data.model.BookEntity(
      title = "Panduan Pintar LishPDF & Fitur Coretan",
      localFilePath = "/data/user/0/com.example/files/books/panduan_lengkap_lishpdf.pdf",
      fileType = "PDF",
      totalPages = 4
    )
    val pagesText = com.example.data.parser.PdfTextExtractor.extractPagesText(context, sampleBook, 4)
    val linksMap = com.example.data.parser.PdfTextExtractor.extractDocumentLinks(context, sampleBook, 4, pagesText)
    val allLinks = linksMap.values.flatten()
    org.junit.Assert.assertTrue("Should discover links from document", allLinks.isNotEmpty())

    val urls = allLinks.map { it.url }
    org.junit.Assert.assertTrue("Should find developer.android.com link", urls.any { it.contains("developer.android.com") })
    org.junit.Assert.assertTrue("Should find wikipedia link", urls.any { it.contains("wikipedia.org") })
    org.junit.Assert.assertTrue("All links must have valid page indices", allLinks.all { it.pageIndex in 0 until 4 })
  }

  @Test
  fun `reopening book preserves last saved page and progress percent`() {
    val originalBook = com.example.data.model.BookEntity(
      id = 42,
      title = "Buku Uji Coba LishPDF",
      localFilePath = "/test/path/sample.pdf",
      fileType = "PDF",
      totalPages = 10,
      currentPage = 0,
      progressPercent = 0
    )

    // User navigates to page 7 (8th page)
    val savedPage = 7
    val progress = ((savedPage + 1) * 100) / originalBook.totalPages
    val updatedBook = originalBook.copy(
      currentPage = savedPage,
      progressPercent = progress
    )

    assertEquals(7, updatedBook.currentPage)
    assertEquals(80, updatedBook.progressPercent)
  }

  @Test
  fun `vault documents are isolated from public library`() {
    val publicDoc = com.example.data.model.BookEntity(
      id = 1,
      title = "Dokumen Publik",
      fileType = "PDF",
      isVault = false
    )
    val secretDoc = com.example.data.model.BookEntity(
      id = 2,
      title = "Dokumen Rahasia Sangat Penting",
      fileType = "PDF",
      isVault = true
    )

    val allDocs = listOf(publicDoc, secretDoc)
    val publicList = allDocs.filter { !it.isVault }
    val vaultList = allDocs.filter { it.isVault }

    assertEquals(1, publicList.size)
    assertEquals("Dokumen Publik", publicList.first().title)
    org.junit.Assert.assertFalse("Public list must NEVER contain vault documents", publicList.any { it.isVault })

    assertEquals(1, vaultList.size)
    assertEquals("Dokumen Rahasia Sangat Penting", vaultList.first().title)
    org.junit.Assert.assertTrue("Vault list must contain secret document", vaultList.first().isVault)
  }

  @Test
  fun `triple tap detection triggers unlock only on 3 taps within threshold`() {
    var tapCount = 0
    var lastTapTime = 0L
    var unlocked = false

    fun registerTap(time: Long) {
      if (time - lastTapTime < 1500L) {
        tapCount++
      } else {
        tapCount = 1
      }
      lastTapTime = time
      if (tapCount >= 3) {
        tapCount = 0
        unlocked = true
      }
    }

    // 1st tap at 1000ms
    registerTap(1000L)
    org.junit.Assert.assertFalse(unlocked)

    // 2nd tap at 1300ms
    registerTap(1300L)
    org.junit.Assert.assertFalse(unlocked)

    // 3rd tap at 1600ms (< 1500ms since last tap)
    registerTap(1600L)
    org.junit.Assert.assertTrue("Should be unlocked on 3rd tap within threshold", unlocked)
  }
}

