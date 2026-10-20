package com.example.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.BookEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
}
