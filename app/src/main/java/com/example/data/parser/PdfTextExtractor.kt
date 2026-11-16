package com.example.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.BookEntity
import com.example.data.model.PdfLinkItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

object PdfTextExtractor {

    private const val TAG = "PdfTextExtractor"
    private var isPdfBoxInitialized = false

    fun initPdfBox(context: Context) {
        if (!isPdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isPdfBoxInitialized = true
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize PDFBoxResourceLoader", e)
            }
        }
    }

    /**
     * Extracts text page-by-page from a PDF file.
     * Returns a list of strings where index i is the text of page i.
     */
    fun extractPagesText(
        context: Context,
        book: BookEntity,
        totalPages: Int
    ): List<String> {
        // 1. Instant check: known structured content for sample books
        val sampleText = SamplePdfProvider.getSampleBookPagesText(book)
        if (sampleText != null && sampleText.isNotEmpty()) {
            return sampleText
        }

        // 2. Primary: Industry-standard text extraction via PDFBox
        try {
            initPdfBox(context)
            val file = if (book.localFilePath.isNotBlank()) File(book.localFilePath) else null
            val document: PDDocument? = when {
                file != null && file.exists() -> PDDocument.load(file)
                book.fileUri.isNotBlank() -> {
                    context.contentResolver.openInputStream(Uri.parse(book.fileUri))?.use {
                        PDDocument.load(it)
                    }
                }
                else -> null
            }

            if (document != null) {
                val pages = mutableListOf<String>()
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val numPages = document.numberOfPages
                for (p in 1..numPages) {
                    stripper.startPage = p
                    stripper.endPage = p
                    val text = stripper.getText(document)
                    pages.add(cleanExtractedText(text))
                }
                document.close()
                if (pages.any { it.isNotBlank() }) {
                    return pages
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "PDFBox extraction failed for ${book.title}, using fallback stream parser", e)
        }

        // 3. Fallback: Direct PDF stream parser with word-preservation
        return try {
            val file = if (book.localFilePath.isNotBlank()) File(book.localFilePath) else null
            if (file != null && file.exists()) {
                file.inputStream().use { inputStream ->
                    parsePdfStreamToPages(inputStream, totalPages)
                }
            } else if (book.fileUri.isNotBlank()) {
                context.contentResolver.openInputStream(Uri.parse(book.fileUri))?.use { inputStream ->
                    parsePdfStreamToPages(inputStream, totalPages)
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback text extraction for ${book.title}", e)
            emptyList()
        }
    }

    /**
     * Parses PDF binary content to extract plain text for each page.
     */
    fun parsePdfStreamToPages(inputStream: InputStream, expectedPages: Int): List<String> {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.size < 10) return emptyList()

            // Verify PDF header %PDF-
            val header = String(bytes.sliceArray(0 until minOf(bytes.size, 1024)), Charsets.ISO_8859_1)
            if (!header.contains("%PDF")) {
                return emptyList()
            }

            extractPagesFromPdfBytes(bytes, expectedPages)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PDF stream", e)
            emptyList()
        }
    }

    private data class PdfStreamObj(
        val objId: Int,
        val gen: Int,
        val isFlate: Boolean,
        val streamData: ByteArray
    )

    private fun extractPagesFromPdfBytes(bytes: ByteArray, expectedPages: Int): List<String> {
        val streamObjects = mutableMapOf<Int, PdfStreamObj>()
        val pageDicts = mutableListOf<String>()

        val pdfContent = String(bytes, Charsets.ISO_8859_1)

        // Find all page dictionary objects (/Type /Page)
        val pageObjRegex = Regex("""(\d+)\s+(\d+)\s+obj\s*<<([^>]*/Type\s*/Page\b[^>]*)>>""", RegexOption.DOT_MATCHES_ALL)
        for (match in pageObjRegex.findAll(pdfContent)) {
            pageDicts.add(match.groupValues[3])
        }

        // Find all stream objects: obj ... stream ... endstream
        val streamMarker = "stream".toByteArray(Charsets.ISO_8859_1)
        val endStreamMarker = "endstream".toByteArray(Charsets.ISO_8859_1)
        val objMarker = "obj".toByteArray(Charsets.ISO_8859_1)

        var searchPos = 0
        while (searchPos < bytes.size) {
            val streamIdx = indexOfBytes(bytes, streamMarker, searchPos)
            if (streamIdx == -1) break

            var dataStart = streamIdx + streamMarker.size
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endStreamIdx = indexOfBytes(bytes, endStreamMarker, dataStart)
            if (endStreamIdx == -1) break

            var dataEnd = endStreamIdx
            if (dataEnd > dataStart && bytes[dataEnd - 1] == '\n'.code.toByte()) dataEnd--
            if (dataEnd > dataStart && bytes[dataEnd - 1] == '\r'.code.toByte()) dataEnd--

            val headerStart = maxOf(0, streamIdx - 512)
            val headerString = String(bytes.sliceArray(headerStart until streamIdx), Charsets.ISO_8859_1)

            val objMatch = Regex("""(\d+)\s+(\d+)\s+obj""").findAll(headerString).lastOrNull()
            if (objMatch != null) {
                val objId = objMatch.groupValues[1].toIntOrNull() ?: 0
                val gen = objMatch.groupValues[2].toIntOrNull() ?: 0
                val isFlate = headerString.contains("/FlateDecode")
                val streamBytes = bytes.sliceArray(dataStart until maxOf(dataStart, dataEnd))
                streamObjects[objId] = PdfStreamObj(objId, gen, isFlate, streamBytes)
            }

            searchPos = endStreamIdx + endStreamMarker.size
        }

        // Map pages to their stream content
        val pagesText = mutableListOf<String>()

        for (dict in pageDicts) {
            val contentIds = extractContentsIds(dict)
            val pageSb = StringBuilder()
            for (id in contentIds) {
                val streamObj = streamObjects[id] ?: continue
                val decompressed = decompressStream(streamObj)
                val text = extractTextFromPdfContentStream(decompressed)
                if (text.isNotBlank()) {
                    if (pageSb.isNotEmpty() && !pageSb.endsWith(" ")) {
                        pageSb.append(" ")
                    }
                    pageSb.append(text)
                }
            }
            pagesText.add(cleanExtractedText(pageSb.toString()))
        }

        if (pagesText.isEmpty() || pagesText.all { it.isBlank() }) {
            val allStreamTexts = mutableListOf<String>()
            for ((_, obj) in streamObjects) {
                val decompressed = decompressStream(obj)
                val text = extractTextFromPdfContentStream(decompressed)
                if (text.isNotBlank()) {
                    allStreamTexts.add(cleanExtractedText(text))
                }
            }
            if (allStreamTexts.isNotEmpty()) {
                return allStreamTexts
            }
        }

        return pagesText
    }

    private fun extractContentsIds(pageDict: String): List<Int> {
        val ids = mutableListOf<Int>()
        val arrayMatch = Regex("""/Contents\s*\[\s*([^\]]+)\]""").find(pageDict)
        if (arrayMatch != null) {
            val refs = Regex("""(\d+)\s+\d+\s+R""").findAll(arrayMatch.groupValues[1])
            for (ref in refs) {
                ref.groupValues[1].toIntOrNull()?.let { ids.add(it) }
            }
        } else {
            val singleMatch = Regex("""/Contents\s+(\d+)\s+\d+\s+R""").find(pageDict)
            singleMatch?.groupValues?.get(1)?.toIntOrNull()?.let { ids.add(it) }
        }
        return ids
    }

    private fun decompressStream(streamObj: PdfStreamObj): ByteArray {
        if (!streamObj.isFlate) {
            return streamObj.streamData
        }
        return try {
            val inflater = Inflater()
            inflater.setInput(streamObj.streamData)
            val buffer = ByteArray(4096)
            val outputStream = ByteArrayOutputStream()
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            outputStream.toByteArray()
        } catch (e: Exception) {
            try {
                InflaterInputStream(ByteArrayInputStream(streamObj.streamData)).use { it.readBytes() }
            } catch (e2: Exception) {
                streamObj.streamData
            }
        }
    }

    /**
     * Extracts text from decompressed PDF content stream operators (BT ... ET, Tj, TJ, ', ")
     * CRITICAL: Preserves whole words by NOT inserting spurious spaces between consecutive glyphs or operators.
     */
    fun extractTextFromPdfContentStream(streamBytes: ByteArray): String {
        val content = String(streamBytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        // Match all BT ... ET blocks
        val btEtRegex = Regex("""BT\s+(.*?)\s+ET""", RegexOption.DOT_MATCHES_ALL)
        val btBlocks = btEtRegex.findAll(content).map { it.groupValues[1] }.toList()
        val blocks = if (btBlocks.isNotEmpty()) btBlocks else listOf(content)

        for (block in blocks) {
            var i = 0
            var inArray = false

            while (i < block.length) {
                val ch = block[i]

                if (ch == '[') {
                    inArray = true
                    i++
                    continue
                } else if (ch == ']') {
                    inArray = false
                    i++
                    continue
                }

                // Check for line break operators in PDF (T*, ', ", Td with delta y != 0)
                if (!inArray && (ch == '\'' || ch == '"')) {
                    if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                }

                if (ch == '(') {
                    // Literal string in parentheses: (Hello World)
                    val strBuilder = StringBuilder()
                    i++
                    var parenDepth = 1
                    var escaped = false

                    while (i < block.length && parenDepth > 0) {
                        val c = block[i]
                        if (escaped) {
                            when (c) {
                                'n', 'r', 't' -> strBuilder.append(' ')
                                '\\' -> strBuilder.append('\\')
                                '(' -> strBuilder.append('(')
                                ')' -> strBuilder.append(')')
                                in '0'..'7' -> {
                                    var octalStr = "$c"
                                    if (i + 1 < block.length && block[i + 1] in '0'..'7') {
                                        i++
                                        octalStr += block[i]
                                        if (i + 1 < block.length && block[i + 1] in '0'..'7') {
                                            i++
                                            octalStr += block[i]
                                        }
                                    }
                                    val code = octalStr.toIntOrNull(8) ?: 32
                                    if (code in 32..126) strBuilder.append(code.toChar())
                                }
                                else -> strBuilder.append(c)
                            }
                            escaped = false
                        } else {
                            if (c == '\\') {
                                escaped = true
                            } else if (c == '(') {
                                parenDepth++
                                strBuilder.append(c)
                            } else if (c == ')') {
                                parenDepth--
                                if (parenDepth > 0) strBuilder.append(c)
                            } else {
                                strBuilder.append(c)
                            }
                        }
                        i++
                    }

                    val literal = strBuilder.toString()
                    if (literal.isNotEmpty()) {
                        // Keep words together: if literal starts with space or ends with space, respect it
                        sb.append(literal)
                    }
                    continue
                } else if (ch == '<' && i + 1 < block.length && block[i + 1] != '<') {
                    // Hex string <00480065006C006C006F>
                    val hexBuilder = StringBuilder()
                    i++
                    while (i < block.length && block[i] != '>') {
                        val c = block[i]
                        if ((c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')) {
                            hexBuilder.append(c)
                        }
                        i++
                    }
                    if (i < block.length && block[i] == '>') i++

                    val decoded = decodeHexString(hexBuilder.toString())
                    if (decoded.isNotEmpty()) {
                        sb.append(decoded)
                    }
                    continue
                } else if (inArray && (ch == '-' || ch == '+' || ch.isDigit())) {
                    // TJ array kerning adjustment: only significant negative values represent actual space
                    var numStr = "$ch"
                    var k = i + 1
                    while (k < block.length && (block[k].isDigit() || block[k] == '.')) {
                        numStr += block[k]
                        k++
                    }
                    val spacing = numStr.toFloatOrNull() ?: 0f
                    // In PDF standard, standard word space is about -200 to -350 units.
                    // Negative spacing <= -150 indicates an intentional inter-word space.
                    if (spacing <= -150f && sb.isNotEmpty() && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                    i = k
                    continue
                } else {
                    // Check for T* or Tj operators
                    if (ch == 'T' && i + 1 < block.length && block[i + 1] == '*') {
                        if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                            sb.append(" ")
                        }
                        i += 2
                        continue
                    }
                    i++
                }
            }

            if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                sb.append(" ")
            }
        }

        return sb.toString()
    }

    private fun decodeHexString(hex: String): String {
        return try {
            val cleanHex = if (hex.length % 2 != 0) hex + "0" else hex
            val sb = StringBuilder()
            var j = 0
            if (cleanHex.startsWith("FEFF", ignoreCase = true) || (cleanHex.length >= 4 && cleanHex.startsWith("00"))) {
                while (j + 3 < cleanHex.length) {
                    val code = cleanHex.substring(j, j + 4).toIntOrNull(16) ?: 0
                    if (code in 32..65533) sb.append(code.toChar())
                    j += 4
                }
            } else {
                while (j + 1 < cleanHex.length) {
                    val code = cleanHex.substring(j, j + 2).toIntOrNull(16) ?: 0
                    if (code in 32..126 || code in 160..255) sb.append(code.toChar())
                    j += 2
                }
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray, fromIndex: Int = 0): Int {
        if (target.isEmpty() || fromIndex >= source.size) return -1
        outer@ for (i in fromIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    fun cleanExtractedText(text: String): String {
        return text.replace(Regex("""[^\S\r\n]+"""), " ")
            .replace(Regex("""\n+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Extracts all hyperlinks from the PDF document:
     * 1. Interactive link annotations (PDAnnotationLink) with PDActionURI.
     * 2. Plain-text URLs matched from the extracted text of each page.
     */
    fun extractDocumentLinks(
        context: Context,
        book: BookEntity,
        totalPages: Int,
        pageTexts: List<String>
    ): Map<Int, List<PdfLinkItem>> {
        val linksByPage = mutableMapOf<Int, MutableList<PdfLinkItem>>()

        // 1. Extract formal PDF link annotations via PDFBox
        try {
            initPdfBox(context)
            val file = if (book.localFilePath.isNotBlank()) File(book.localFilePath) else null
            val document: PDDocument? = when {
                file != null && file.exists() -> PDDocument.load(file)
                book.fileUri.isNotBlank() -> {
                    context.contentResolver.openInputStream(Uri.parse(book.fileUri))?.use {
                        PDDocument.load(it)
                    }
                }
                else -> null
            }

            if (document != null) {
                val numPages = document.numberOfPages
                for (p in 0 until numPages) {
                    try {
                        val pdPage = document.getPage(p)
                        val annotations = pdPage.annotations ?: emptyList()
                        val pw = pdPage.mediaBox.width.coerceAtLeast(1f)
                        val ph = pdPage.mediaBox.height.coerceAtLeast(1f)
                        val pageList = linksByPage.getOrPut(p) { mutableListOf() }

                        for (annot in annotations) {
                            if (annot is PDAnnotationLink) {
                                val action = annot.action
                                val uri = if (action is PDActionURI) action.uri else null
                                if (!uri.isNullOrBlank()) {
                                    val cleanUri = uri.trim()
                                    val rect = annot.rectangle
                                    val normLeft = if (rect != null) (rect.lowerLeftX / pw).coerceIn(0f, 1f) else 0f
                                    val normRight = if (rect != null) (rect.upperRightX / pw).coerceIn(0f, 1f) else 1f
                                    val normTop = if (rect != null) ((ph - rect.upperRightY) / ph).coerceIn(0f, 1f) else 0f
                                    val normBottom = if (rect != null) ((ph - rect.lowerLeftY) / ph).coerceIn(0f, 1f) else 1f

                                    // Avoid duplicates
                                    if (pageList.none { it.url.equals(cleanUri, ignoreCase = true) }) {
                                        pageList.add(
                                            PdfLinkItem(
                                                id = "annot_link_${p}_${pageList.size}_${cleanUri.hashCode()}",
                                                url = cleanUri,
                                                displayText = cleanUri,
                                                pageIndex = p,
                                                normalizedLeft = normLeft,
                                                normalizedTop = normTop,
                                                normalizedRight = normRight,
                                                normalizedBottom = normBottom,
                                                hasExactBounds = rect != null && rect.width > 0 && rect.height > 0
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } catch (ePage: Exception) {
                        Log.w(TAG, "Error reading annotations on page $p", ePage)
                    }
                }
                document.close()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed PDFBox link annotation extraction for ${book.title}", e)
        }

        // 2. Scan extracted page texts for plain-text URLs (e.g. https://... or www....)
        val urlRegex = Regex("""(?i)\b(?:https?://|www\.)[a-z0-9+&@#/%?=~_|!:,.;-]*[a-z0-9+&@#/%=~_|-]""")
        for ((pageIdx, text) in pageTexts.withIndex()) {
            if (text.isBlank()) continue
            val pageList = linksByPage.getOrPut(pageIdx) { mutableListOf() }
            val existingUrls = pageList.map { it.url.lowercase().trim() }.toSet()

            val matches = urlRegex.findAll(text)
            for (m in matches) {
                val foundUrl = m.value.trim().trimEnd('.', ',', ';', ':', ')', ']', '}')
                if (foundUrl.length <= 4) continue
                val normalizedUrl = if (foundUrl.startsWith("www.", ignoreCase = true)) "https://$foundUrl" else foundUrl
                if (!existingUrls.contains(foundUrl.lowercase()) && !existingUrls.contains(normalizedUrl.lowercase())) {
                    pageList.add(
                        PdfLinkItem(
                            id = "text_link_${pageIdx}_${pageList.size}_${foundUrl.hashCode()}",
                            url = normalizedUrl,
                            displayText = foundUrl,
                            pageIndex = pageIdx,
                            hasExactBounds = false
                        )
                    )
                }
            }
        }

        return linksByPage.filterValues { it.isNotEmpty() }
    }

    /**
     * Extracts exact normalized [0..1] bounding boxes for all search matches on the specified pages
     * using PDFBox TextPosition geometry, accounting for CropBox, page rotation, and font ascent/descent.
     */
    fun extractSearchHighlightsForPages(
        context: Context,
        book: BookEntity,
        pageIndices: List<Int>,
        query: String
    ): Map<Int, List<SearchHighlightBox>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || pageIndices.isEmpty()) return emptyMap()

        var document: PDDocument? = null
        return try {
            initPdfBox(context)
            val file = if (book.localFilePath.isNotBlank()) File(book.localFilePath) else null
            document = when {
                file != null && file.exists() -> PDDocument.load(file)
                book.fileUri.isNotBlank() -> {
                    context.contentResolver.openInputStream(Uri.parse(book.fileUri))?.use {
                        PDDocument.load(it)
                    }
                }
                else -> null
            }

            if (document != null) {
                extractSearchHighlightsFromDocument(document, pageIndices, trimmed)
            } else {
                emptyMap()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed PDFBox search highlight extraction for ${book.title}", e)
            emptyMap()
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extracts exact normalized [0..1] bounding boxes from an already-loaded PDDocument.
     */
    fun extractSearchHighlightsFromDocument(
        document: PDDocument,
        pageIndices: List<Int>,
        query: String
    ): Map<Int, List<SearchHighlightBox>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || pageIndices.isEmpty()) return emptyMap()

        val result = mutableMapOf<Int, List<SearchHighlightBox>>()
        val numPages = document.numberOfPages

        val flexibleRegex = try {
            if (trimmed.length > 1) {
                val escapedLetters = trimmed.map { Regex.escape(it.toString()) }
                Regex(escapedLetters.joinToString("""[\s\u00A0]*"""), RegexOption.IGNORE_CASE)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        for (pageIdx in pageIndices.distinct()) {
            if (pageIdx < 0 || pageIdx >= numPages) continue
            try {
                val boxes = extractPageHighlights(document, pageIdx, trimmed, flexibleRegex)
                if (boxes.isNotEmpty()) {
                    result[pageIdx] = boxes
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed extracting highlights on page $pageIdx", e)
            }
        }
        return result
    }

    private fun extractPageHighlights(
        document: PDDocument,
        pageIndex: Int,
        query: String,
        flexibleRegex: Regex?
    ): List<SearchHighlightBox> {
        val pdPage: PDPage = document.getPage(pageIndex)
        val mediaBox = pdPage.mediaBox
        val cropBox = pdPage.cropBox ?: mediaBox
        val rotation = ((pdPage.rotation % 360) + 360) % 360

        val textBuilder = StringBuilder()
        val charPositions = mutableListOf<TextPosition?>()

        val stripper = object : PDFTextStripper() {
            init {
                sortByPosition = true
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }

            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                for (tp in textPositions) {
                    val u = tp.unicode ?: ""
                    if (u.isEmpty()) continue
                    for (ch in u) {
                        if (ch == '\u00A0' || ch.isWhitespace()) {
                            if (textBuilder.isNotEmpty() && textBuilder.last() != ' ') {
                                textBuilder.append(' ')
                                charPositions.add(null)
                            }
                        } else {
                            textBuilder.append(ch)
                            charPositions.add(tp)
                        }
                    }
                }
            }

            override fun writeWordSeparator() {
                if (textBuilder.isNotEmpty() && textBuilder.last() != ' ') {
                    textBuilder.append(' ')
                    charPositions.add(null)
                }
            }

            override fun writeLineSeparator() {
                if (textBuilder.isNotEmpty() && textBuilder.last() != ' ') {
                    textBuilder.append(' ')
                    charPositions.add(null)
                }
            }
        }

        stripper.getText(document)
        val pageStr = textBuilder.toString()
        if (pageStr.isEmpty()) return emptyList()

        // Find matches using the exact same matching order as ReaderViewModel.performSearch
        val matchRanges = mutableListOf<IntRange>()
        var startIdx = 0
        while (startIdx < pageStr.length) {
            val found = pageStr.indexOf(query, startIdx, ignoreCase = true)
            if (found == -1) break
            matchRanges.add(found until (found + query.length))
            startIdx = found + maxOf(1, query.length)
        }

        if (matchRanges.isEmpty() && flexibleRegex != null) {
            for (m in flexibleRegex.findAll(pageStr)) {
                matchRanges.add(m.range)
            }
        }

        if (matchRanges.isEmpty()) return emptyList()

        // PDFBox LegacyPDFStreamEngine already transforms TextPosition.getXDirAdj() and getYDirAdj()
        // into rotated CropBox top-left display space (0..dispW x 0..dispH), matching PdfRenderer!
        val dispW: Float = if (rotation == 90 || rotation == 270) {
            cropBox.height.coerceAtLeast(1f)
        } else {
            cropBox.width.coerceAtLeast(1f)
        }
        val dispH: Float = if (rotation == 90 || rotation == 270) {
            cropBox.width.coerceAtLeast(1f)
        } else {
            cropBox.height.coerceAtLeast(1f)
        }

        val boxes = mutableListOf<SearchHighlightBox>()

        for ((matchIdx, range) in matchRanges.withIndex()) {
            val tps = mutableListOf<TextPosition>()
            for (idx in range) {
                if (idx in charPositions.indices) {
                    val tp = charPositions[idx]
                    if (tp != null) tps.add(tp)
                }
            }
            if (tps.isEmpty()) continue

            fun resolveDisplayDirection(tp: TextPosition): Int {
                val tm = tp.textMatrix
                val ux = tm?.scaleX ?: 1f
                val uy = tm?.shearY ?: 0f
                val dispDx: Float
                val dispDy: Float
                when (rotation) {
                    90 -> {
                        dispDx = uy
                        dispDy = ux
                    }
                    180 -> {
                        dispDx = -ux
                        dispDy = uy
                    }
                    270 -> {
                        dispDx = -uy
                        dispDy = -ux
                    }
                    else -> {
                        dispDx = ux
                        dispDy = -uy
                    }
                }
                return if (kotlin.math.abs(dispDx) >= kotlin.math.abs(dispDy)) {
                    if (dispDx >= 0f) 0 else 180
                } else {
                    if (dispDy >= 0f) 90 else 270
                }
            }

            // Group TextPositions by visual line along their reading axis so multi-line matches produce tight boxes per line
            val lineGroups = mutableListOf<MutableList<TextPosition>>()
            var currentGroup = mutableListOf(tps.first())
            lineGroups.add(currentGroup)

            for (k in 1 until tps.size) {
                val prev = currentGroup.last()
                val curr = tps[k]
                val prevDir = resolveDisplayDirection(prev)
                val currDir = resolveDisplayDirection(curr)
                val lineTol = maxOf(prev.heightDir, curr.heightDir, prev.fontSizeInPt * 0.5f, 4f)
                val baselineDiff = when (currDir) {
                    90, 270 -> kotlin.math.abs(curr.x - prev.x)
                    else -> kotlin.math.abs(curr.y - prev.y)
                }
                if (prevDir == currDir && baselineDiff <= lineTol) {
                    currentGroup.add(curr)
                } else {
                    currentGroup = mutableListOf(curr)
                    lineGroups.add(currentGroup)
                }
            }

            for (group in lineGroups) {
                var minX = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE

                for (tp in group) {
                    val bx = tp.x
                    val by = tp.y
                    val adv = tp.widthDirAdj.coerceAtLeast(0f)
                    val dir = resolveDisplayDirection(tp)

                    // Derive true ascent above baseline and descent below baseline from font descriptor when available
                    val descriptor = try { tp.font?.fontDescriptor } catch (_: Exception) { null }
                    val effectivePt = tp.fontSizeInPt.takeIf { it > 0.5f } ?: (tp.heightDir * 1.4f)
                    val ascentPt = if (descriptor != null && descriptor.ascent > 0f) {
                        (descriptor.ascent / 1000f) * effectivePt
                    } else {
                        maxOf(tp.heightDir * 1.55f, effectivePt * 0.78f)
                    }
                    val descentPt = if (descriptor != null && descriptor.descent < 0f) {
                        (kotlin.math.abs(descriptor.descent) / 1000f) * effectivePt
                    } else {
                        maxOf(tp.heightDir * 0.42f, effectivePt * 0.22f)
                    }

                    val glyphLeft: Float
                    val glyphRight: Float
                    val glyphTop: Float
                    val glyphBottom: Float

                    when (dir) {
                        90 -> {
                            // Text advances downward (+Y), ascent is toward +X, descent is toward -X
                            glyphLeft = bx - descentPt
                            glyphRight = bx + ascentPt
                            glyphTop = by
                            glyphBottom = by + adv
                        }
                        180 -> {
                            // Text advances leftward (-X), ascent is toward +Y, descent is toward -Y
                            glyphLeft = bx - adv
                            glyphRight = bx
                            glyphTop = by - descentPt
                            glyphBottom = by + ascentPt
                        }
                        270 -> {
                            // Text advances upward (-Y), ascent is toward -X, descent is toward +X
                            glyphLeft = bx - ascentPt
                            glyphRight = bx + descentPt
                            glyphTop = by - adv
                            glyphBottom = by
                        }
                        else -> {
                            // Standard horizontal left-to-right (+X), ascent is toward -Y, descent is toward +Y
                            glyphLeft = bx
                            glyphRight = bx + adv
                            glyphTop = by - ascentPt
                            glyphBottom = by + descentPt
                        }
                    }

                    if (glyphLeft < minX) minX = glyphLeft
                    if (glyphRight > maxX) maxX = glyphRight
                    if (glyphTop < minY) minY = glyphTop
                    if (glyphBottom > maxY) maxY = glyphBottom
                }

                if (minX < maxX && minY < maxY) {
                    boxes.add(
                        SearchHighlightBox(
                            normalizedLeft = (minX / dispW).coerceIn(0f, 1f),
                            normalizedTop = (minY / dispH).coerceIn(0f, 1f),
                            normalizedRight = (maxX / dispW).coerceIn(0f, 1f),
                            normalizedBottom = (maxY / dispH).coerceIn(0f, 1f),
                            matchIndexInPage = matchIdx
                        )
                    )
                }
            }
        }

        return boxes
    }
}
