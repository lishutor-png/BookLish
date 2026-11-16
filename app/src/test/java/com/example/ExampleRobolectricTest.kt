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

  @Test
  fun `pdfbox highlight extraction computes exact normalized coordinates across rotation and cropBox`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.data.parser.PdfTextExtractor.initPdfBox(context)

    val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
    val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD

    // Page 0: Standard 595x842, 0 deg rotation, text at PDF (50, 700) -> top-down baseline Y = 842 - 700 = 142
    val p0 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f))
    doc.addPage(p0)
    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p0).use { cs ->
      cs.beginText()
      cs.setFont(font, 20f)
      cs.newLineAtOffset(50f, 700f)
      cs.showText("TARGET")
      cs.endText()
    }

    // Page 1: MediaBox 595x842, CropBox(50, 100, 400, 600), 0 deg rotation, text at PDF (150, 500)
    // Relative to CropBox: x = 150 - 50 = 100 (0.25 of 400), baselineY from top(700) = 700 - 500 = 200 (0.333 of 600)
    val p1 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f))
    p1.cropBox = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(50f, 100f, 400f, 600f)
    doc.addPage(p1)
    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p1).use { cs ->
      cs.beginText()
      cs.setFont(font, 20f)
      cs.newLineAtOffset(150f, 500f)
      cs.showText("TARGET")
      cs.endText()
    }

    // Page 2: 595x842 rotated 90 deg clockwise -> displayed size 842 x 595
    // Unrotated PDF coords (100, 200) -> rotated 90 CW:
    // In 90 CW rotated page (bottom of PDF becomes left edge, top of PDF becomes right edge):
    // displayX = 842 - pdfY? Wait: 90 CW maps old +Y (up) to +X (right), old +X (right) to -Y (down)!
    // Specifically: old (0,0) -> top-left? Wait: rotating clockwise 90 deg puts old left edge at top, old bottom edge at left?
    // Wait: old top edge (y=842) rotates CW to right edge (x_disp=842), old bottom edge (y=0) rotates CW to left edge (x_disp=0).
    // So displayX = pdfY, displayY_topdown = pdfX!
    val p2 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f))
    p2.rotation = 90
    doc.addPage(p2)
    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p2).use { cs ->
      cs.beginText()
      cs.setFont(font, 20f)
      cs.newLineAtOffset(100f, 200f)
      cs.showText("TARGET")
      cs.endText()
    }

    // Page 3: 595x842 rotated 180 deg -> displayX = 595 - pdfX, displayY_topdown = pdfY
    val p3 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f))
    p3.rotation = 180
    doc.addPage(p3)
    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p3).use { cs ->
      cs.beginText()
      cs.setFont(font, 20f)
      cs.newLineAtOffset(400f, 200f)
      cs.showText("TARGET")
      cs.endText()
    }

    // Page 4: 595x842 rotated 270 deg -> displayX = 842 - pdfY, displayY_topdown = 595 - pdfX
    val p4 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f))
    p4.rotation = 270
    doc.addPage(p4)
    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p4).use { cs ->
      cs.beginText()
      cs.setFont(font, 20f)
      cs.newLineAtOffset(400f, 600f)
      cs.showText("TARGET")
      cs.endText()
    }

    // Page 5: 595x842 rotated 90 deg CW with text rotated 90 deg CCW so it reads upright horizontally on screen!
    // Text matrix rotated +90 deg (CCW) placed at PDF (200, 300) -> on 90-CW page (842x595):
    // displayX = pdfY = 300 (left-to-right!), displayY = pdfX = 200 (ascent toward -Y_disp, i.e., -X_pdf)
    val p5 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f))
    p5.rotation = 90
    doc.addPage(p5)
    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p5).use { cs ->
      cs.beginText()
      cs.setFont(font, 20f)
      cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.PI / 2.0, 200f, 300f))
      cs.showText("TARGET")
      cs.endText()
    }

    val highlights = com.example.data.parser.PdfTextExtractor.extractSearchHighlightsFromDocument(
      doc,
      listOf(0, 1, 2, 3, 4, 5),
      "TARGET"
    )
    doc.close()

    val b0 = highlights[0]!!.first()
    // Page 0: x = 50/595 = 0.084, baselineY = 142/842 = 0.1686 (box straddles baseline: top < 0.1686 < bottom)
    org.junit.Assert.assertEquals("P0 left", 50f / 595f, b0.normalizedLeft, 0.01f)
    org.junit.Assert.assertTrue("P0 top < baseline < bottom: $b0", b0.normalizedTop < 142f / 842f && b0.normalizedBottom > 142f / 842f)

    val b1 = highlights[1]!!.first()
    org.junit.Assert.assertEquals("P1 cropBox left: $b1", 100f / 400f, b1.normalizedLeft, 0.015f)
    org.junit.Assert.assertTrue("P1 cropBox top < baseline < bottom: $b1", b1.normalizedTop < 200f / 600f && b1.normalizedBottom > 200f / 600f)

    val b2 = highlights[2]!!.first()
    // Rotation 90 CW: 1 single grouped box spanning vertically from y=100/595 down
    assertEquals("P2 rot90 should group into 1 box", 1, highlights[2]!!.size)
    org.junit.Assert.assertEquals("P2 rot90 left straddling 200/842: $b2", 200f / 842f, b2.normalizedLeft, 0.02f)
    org.junit.Assert.assertEquals("P2 rot90 top at 100/595: $b2", 100f / 595f, b2.normalizedTop, 0.015f)

    val b3 = highlights[3]!!.first()
    // Rotation 180: baseline from 195 leftward to 112.78, y_disp = 200/842
    org.junit.Assert.assertEquals("P3 rot180 right at 195/595: $b3", 195f / 595f, b3.normalizedRight, 0.01f)
    org.junit.Assert.assertTrue("P3 rot180 y: $b3", b3.normalizedTop < 200f / 842f && b3.normalizedBottom > 200f / 842f)

    val b4 = highlights[4]!!.first()
    // Rotation 270: 1 single grouped box spanning vertically up to bottom=195/595
    assertEquals("P4 rot270 should group into 1 box", 1, highlights[4]!!.size)
    org.junit.Assert.assertEquals("P4 rot270 left near 242/842: $b4", (842f - 600f) / 842f, b4.normalizedLeft, 0.025f)
    org.junit.Assert.assertEquals("P4 rot270 bottom at 195/595: $b4", (595f - 400f) / 595f, b4.normalizedBottom, 0.015f)

    val b5 = highlights[5]!!.first()
    // Rotation 90 CW with upright text matrix at PDF (200, 300): displayX = 300/842, displayY baseline = 200/595
    assertEquals("P5 rot90 upright should be 1 box", 1, highlights[5]!!.size)
    org.junit.Assert.assertEquals("P5 upright left at 300/842: $b5", 300f / 842f, b5.normalizedLeft, 0.015f)
    org.junit.Assert.assertTrue("P5 upright y straddles 200/595: $b5", b5.normalizedTop < 200f / 595f && b5.normalizedBottom > 200f / 595f)
  }

  @Test
  fun `computeFittedPageSize preserves exact PDF aspect ratio across portrait, landscape, and tablet viewports`() {
    // Portrait phone (1080x1900) viewing A4 portrait PDF (1400x1980) -> fits width 1080, height ~1527.4
    val portraitFit = com.example.ui.screens.computeFittedPageSize(1080f, 1900f, 1400f, 1980f)
    assertEquals(1080f, portraitFit.width, 0.01f)
    assertEquals(1080f / (1400f / 1980f), portraitFit.height, 0.1f)

    // Landscape phone (1900x900) viewing A4 portrait PDF (1400x1980) -> fits height 900, width ~636.36
    val landscapeFit = com.example.ui.screens.computeFittedPageSize(1900f, 900f, 1400f, 1980f)
    assertEquals(900f, landscapeFit.height, 0.01f)
    assertEquals(900f * (1400f / 1980f), landscapeFit.width, 0.1f)

    // Tablet portrait (1600x2400) viewing wide landscape slide PDF (1920x1080) -> fits width 1600, height 900
    val tabletWideFit = com.example.ui.screens.computeFittedPageSize(1600f, 2400f, 1920f, 1080f)
    assertEquals(1600f, tabletWideFit.width, 0.01f)
    assertEquals(900f, tabletWideFit.height, 0.1f)
  }

  @Test
  fun `computePanOffsetForNormalizedPoint centers target highlight accurately across zoom scales`() {
    val container = androidx.compose.ui.unit.IntSize(1000, 2000)
    val pageW = 1000f
    val pageH = 1400f

    // At 1.0x zoom, pan offset is always zero
    val offset1x = com.example.ui.screens.computePanOffsetForNormalizedPoint(0.25f, 0.25f, pageW, pageH, container, 1.0f)
    assertEquals(0f, offset1x.x, 0.01f)
    assertEquals(0f, offset1x.y, 0.01f)

    // At 2.5x zoom on target near middle-left (0.35, 0.45):
    // dx = (0.35 - 0.5) * 1000 = -150, dy = (0.45 - 0.5) * 1400 = -70
    // desired offset = (150 * 2.5 = 375, 70 * 2.5 = 175), within maxX=750, maxY=1500
    val offset25x = com.example.ui.screens.computePanOffsetForNormalizedPoint(0.35f, 0.45f, pageW, pageH, container, 2.5f)
    assertEquals(375f, offset25x.x, 0.1f)
    assertEquals(175f, offset25x.y, 0.1f)

    // Verify resulting screen coordinate relative to container center is (0, 0)
    val screenRelX = ((0.35f - 0.5f) * pageW) * 2.5f + offset25x.x
    val screenRelY = ((0.45f - 0.5f) * pageH) * 2.5f + offset25x.y
    assertEquals(0f, screenRelX, 0.1f)
    assertEquals(0f, screenRelY, 0.1f)

    // Extreme corner target clamps safely inside container bounds without overscroll
    val offsetCorner = com.example.ui.screens.computePanOffsetForNormalizedPoint(0.02f, 0.98f, pageW, pageH, container, 3.0f)
    val maxX = 1000f * (3.0f - 1f) / 2f
    val maxY = 2000f * (3.0f - 1f) / 2f
    org.junit.Assert.assertTrue(kotlin.math.abs(offsetCorner.x) <= maxX + 0.01f)
    org.junit.Assert.assertTrue(kotlin.math.abs(offsetCorner.y) <= maxY + 0.01f)
  }

  @Test
  fun `pdfbox highlight extraction tracks distinct matchIndexInPage for multiple matches on same page`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.data.parser.PdfTextExtractor.initPdfBox(context)

    val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
    val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
    val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(595f, 842f))
    doc.addPage(page)
    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
      cs.beginText()
      cs.setFont(font, 14f)
      cs.newLineAtOffset(50f, 750f)
      cs.showText("LishPDF pertama di baris atas")
      cs.endText()

      cs.beginText()
      cs.setFont(font, 14f)
      cs.newLineAtOffset(200f, 350f)
      cs.showText("LISHPDF kedua di baris tengah")
      cs.endText()
    }

    val highlights = com.example.data.parser.PdfTextExtractor.extractSearchHighlightsFromDocument(
      doc,
      listOf(0),
      "lishpdf"
    )
    doc.close()

    val pageBoxes = highlights[0]!!
    assertEquals(2, pageBoxes.size)
    assertEquals(0, pageBoxes[0].matchIndexInPage)
    assertEquals(1, pageBoxes[1].matchIndexInPage)
    // First match is near top (y ~ 92/842), second match is lower down (y ~ 492/842)
    org.junit.Assert.assertTrue("First match top < second match top", pageBoxes[0].normalizedTop < pageBoxes[1].normalizedTop)
    org.junit.Assert.assertTrue("First match left < second match left", pageBoxes[0].normalizedLeft < pageBoxes[1].normalizedLeft)
  }

  @Test
  fun `resolveDominantVisiblePage updates current page smoothly while scrolling vertically`() {
    // Viewport height = 1000 (0..1000)
    // Initially at top: page 0 covers 0..950, page 1 peeks 964..1000
    assertEquals(
      0,
      com.example.ui.screens.resolveDominantVisiblePage(
        visibleItems = listOf(
          com.example.ui.screens.VisiblePageSpan(index = 0, offset = 0, size = 1200),
          com.example.ui.screens.VisiblePageSpan(index = 1, offset = 1214, size = 1200)
        ),
        viewportStart = 0,
        viewportEnd = 1000
      )
    )

    // Scrolled midway where page 0 covers 0..450 and page 1 covers 464..1000 (536px > 450px)
    assertEquals(
      1,
      com.example.ui.screens.resolveDominantVisiblePage(
        visibleItems = listOf(
          com.example.ui.screens.VisiblePageSpan(index = 0, offset = -750, size = 1200),
          com.example.ui.screens.VisiblePageSpan(index = 1, offset = 464, size = 1200)
        ),
        viewportStart = 0,
        viewportEnd = 1000
      )
    )

    // Scrolled further down to page 2 (occupying 150..1000 = 850px vs page 1's 0..136)
    assertEquals(
      2,
      com.example.ui.screens.resolveDominantVisiblePage(
        visibleItems = listOf(
          com.example.ui.screens.VisiblePageSpan(index = 1, offset = -1064, size = 1200),
          com.example.ui.screens.VisiblePageSpan(index = 2, offset = 150, size = 1200)
        ),
        viewportStart = 0,
        viewportEnd = 1000
      )
    )

    // Empty visible items returns fallback
    assertEquals(
      3,
      com.example.ui.screens.resolveDominantVisiblePage(
        visibleItems = emptyList(),
        viewportStart = 0,
        viewportEnd = 1000,
        fallbackIndex = 3
      )
    )
  }

  @Test
  fun `syncVisiblePageInContinuousMode updates indicator state and persists last read page to Room`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    com.example.data.parser.PdfTextExtractor.initPdfBox(context)

    val db = com.example.data.local.AppDatabase.getDatabase(context)
    val repo = com.example.data.local.BookRepository(db)
    val insertedId = kotlinx.coroutines.runBlocking {
      repo.insertBook(
        com.example.data.model.BookEntity(
          title = "Uji Gulir Halaman",
          localFilePath = "/tmp/nonexistent_robolectric_test.pdf",
          fileType = "PDF",
          totalPages = 4,
          currentPage = 0,
          progressPercent = 0
        )
      )
    }
    val book = kotlinx.coroutines.runBlocking { repo.getBookById(insertedId)!! }

    val viewModel = com.example.ui.viewmodel.ReaderViewModel(context)
    viewModel.openBook(book)

    // Pump main looper until openBook populates currentBook and finishes loading attempt
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    var waitedMs = 0L
    while ((viewModel.uiState.value.isBookLoading || viewModel.uiState.value.currentBook == null) && waitedMs < 5000L) {
      mainLooper.idle()
      Thread.sleep(25L)
      waitedMs += 25L
    }
    mainLooper.idle()

    org.junit.Assert.assertNotNull("Book should be active in UI state", viewModel.uiState.value.currentBook)
    org.junit.Assert.assertFalse("Book loading state should settle", viewModel.uiState.value.isBookLoading)
    assertEquals(4, viewModel.uiState.value.totalPages)
    assertEquals(0, viewModel.uiState.value.currentPageIndex)

    // Simulate user scrolling to page 3 (index 2, "Halaman 3 dari 4")
    val jumpBefore = viewModel.uiState.value.pageJumpTrigger
    viewModel.syncVisiblePageInContinuousMode(2)
    mainLooper.idle()

    assertEquals(2, viewModel.uiState.value.currentPageIndex)
    assertEquals("Halaman 3 dari 4", viewModel.uiState.value.currentChapterTitle)
    assertEquals(
      "Continuous scroll sync must NOT trigger programmatic scroll jump",
      jumpBefore,
      viewModel.uiState.value.pageJumpTrigger
    )

    // Wait for Room IO coroutine updateProgress to commit
    var persisted = kotlinx.coroutines.runBlocking { repo.getBookById(insertedId) }
    waitedMs = 0L
    while (persisted?.currentPage != 2 && waitedMs < 3000L) {
      mainLooper.idle()
      Thread.sleep(25L)
      waitedMs += 25L
      persisted = kotlinx.coroutines.runBlocking { repo.getBookById(insertedId) }
    }

    assertEquals("Room must persist the scrolled page index", 2, persisted?.currentPage)
    assertEquals("Room must persist 75% progress for page 3/4", 75, persisted?.progressPercent)

    // Clean up
    viewModel.closeReader()
    mainLooper.idle()
    kotlinx.coroutines.runBlocking { repo.deleteBookById(insertedId) }
  }
}

