package com.example.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.BookEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

object PdfTextExtractor {

    private const val TAG = "PdfTextExtractor"

    /**
     * Extracts text page-by-page from a PDF file.
     * Returns a list of strings where index i is the text of page i.
     */
    fun extractPagesText(
        context: Context,
        book: BookEntity,
        totalPages: Int
    ): List<String> {
        // 1. Check if this is a sample book with known structured content
        val sampleText = SamplePdfProvider.getSampleBookPagesText(book)
        if (sampleText != null && sampleText.isNotEmpty()) {
            return sampleText
        }

        // 2. Try reading from local file path or URI
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
            Log.e(TAG, "Error extracting text from PDF ${book.title}", e)
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
        // Strategy 1: Find Page objects and their /Contents references
        val textAscii = String(bytes, Charsets.ISO_8859_1)

        // Find all stream objects in the PDF
        val streamObjs = mutableMapOf<Int, PdfStreamObj>()
        val objHeaderRegex = Regex("""(\d+)\s+(\d+)\s+obj\b""")
        val streamMarker = "stream".toByteArray(Charsets.ISO_8859_1)
        val endstreamMarker = "endstream".toByteArray(Charsets.ISO_8859_1)

        var searchIndex = 0
        while (searchIndex < bytes.size) {
            val streamPos = indexOfBytes(bytes, streamMarker, searchIndex)
            if (streamPos == -1) break

            // Find preceding obj declaration
            val headerSearchStart = maxOf(0, streamPos - 400)
            val headerSubstr = String(bytes.sliceArray(headerSearchStart until streamPos), Charsets.ISO_8859_1)
            val match = objHeaderRegex.findAll(headerSubstr).lastOrNull()

            if (match != null) {
                val objId = match.groupValues[1].toIntOrNull() ?: 0
                val gen = match.groupValues[2].toIntOrNull() ?: 0
                val isFlate = headerSubstr.contains("/FlateDecode")

                // Locate actual stream start (skipping CRLF or LF)
                var dataStart = streamPos + streamMarker.size
                if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
                if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

                // Find endstream
                val endStreamPos = indexOfBytes(bytes, endstreamMarker, dataStart)
                if (endStreamPos != -1 && endStreamPos > dataStart) {
                    var dataEnd = endStreamPos
                    // Trim trailing CRLF before endstream
                    if (dataEnd > dataStart && bytes[dataEnd - 1] == '\n'.code.toByte()) dataEnd--
                    if (dataEnd > dataStart && bytes[dataEnd - 1] == '\r'.code.toByte()) dataEnd--

                    val streamBytes = bytes.copyOfRange(dataStart, dataEnd)
                    streamObjs[objId] = PdfStreamObj(objId, gen, isFlate, streamBytes)
                    searchIndex = endStreamPos + endstreamMarker.size
                    continue
                }
            }
            searchIndex = streamPos + streamMarker.size
        }

        // Find all Page objects (from <id> 0 obj to endobj containing /Type /Page)
        val pageObjRegex = Regex("""(\d+)\s+\d+\s+obj\b[\s\S]*?/Type\s*/Page\b[\s\S]*?endobj""", RegexOption.DOT_MATCHES_ALL)
        val pageMatches = pageObjRegex.findAll(textAscii).toList()

        val pagesText = mutableListOf<String>()

        if (pageMatches.isNotEmpty()) {
            for (pm in pageMatches) {
                val pageDict = pm.value
                val contentsIds = extractContentsIds(pageDict)
                val pageBuilder = StringBuilder()

                for (cId in contentsIds) {
                    val streamObj = streamObjs[cId]
                    if (streamObj != null) {
                        val uncompressed = decompressStream(streamObj)
                        val text = extractTextFromPdfContentStream(uncompressed)
                        if (text.isNotBlank()) {
                            if (pageBuilder.isNotEmpty()) pageBuilder.append(" ")
                            pageBuilder.append(text)
                        }
                    }
                }
                pagesText.add(cleanExtractedText(pageBuilder.toString()))
            }
        }

        // If page matches found fewer pages than expected or was empty,
        // fallback to decompressing all streams sequentially
        if (pagesText.isEmpty() || pagesText.all { it.isBlank() }) {
            val allStreamTexts = mutableListOf<String>()
            for (streamObj in streamObjs.values) {
                val uncompressed = decompressStream(streamObj)
                val text = extractTextFromPdfContentStream(uncompressed)
                if (text.isNotBlank()) {
                    allStreamTexts.add(cleanExtractedText(text))
                }
            }

            if (allStreamTexts.isNotEmpty()) {
                if (expectedPages > 0 && allStreamTexts.size >= expectedPages) {
                    return allStreamTexts.take(expectedPages)
                }
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
                    if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                    i++
                    continue
                }

                if (ch == '(') {
                    // Literal string in parentheses
                    val strBuilder = StringBuilder()
                    i++
                    var parenDepth = 1
                    var escaped = false
                    while (i < block.length && parenDepth > 0) {
                        val c = block[i]
                        if (escaped) {
                            when (c) {
                                'n' -> strBuilder.append(' ')
                                'r' -> strBuilder.append(' ')
                                't' -> strBuilder.append(' ')
                                'b', 'f' -> {}
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
                    val extracted = strBuilder.toString()
                    if (extracted.isNotBlank()) {
                        if (extracted.startsWith(" ") && sb.isNotEmpty() && !sb.endsWith(" ")) {
                            sb.append(" ")
                        }
                        sb.append(extracted.trim())
                        if (extracted.endsWith(" ") || !inArray) {
                            if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                                sb.append(" ")
                            }
                        }
                    }
                } else if (ch == '<' && i + 1 < block.length && block[i + 1] != '<') {
                    // Hex string <...>
                    val hexBuilder = StringBuilder()
                    i++
                    while (i < block.length && block[i] != '>') {
                        if (block[i].isLetterOrDigit()) {
                            hexBuilder.append(block[i])
                        }
                        i++
                    }
                    if (i < block.length && block[i] == '>') i++
                    val hexStr = hexBuilder.toString()
                    val decoded = decodeHexString(hexStr)
                    if (decoded.isNotBlank()) {
                        if (decoded.startsWith(" ") && sb.isNotEmpty() && !sb.endsWith(" ")) {
                            sb.append(" ")
                        }
                        sb.append(decoded.trim())
                        if (decoded.endsWith(" ") || !inArray) {
                            if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                                sb.append(" ")
                            }
                        }
                    }
                } else if (inArray && (ch == '-' || ch == '+') && i + 2 < block.length && block[i + 1].isDigit()) {
                    // Check for kerning offset in TJ array: negative spacing indicates word break
                    var numStr = "$ch"
                    var k = i + 1
                    while (k < block.length && (block[k].isDigit() || block[k] == '.')) {
                        numStr += block[k]
                        k++
                    }
                    val spacing = numStr.toFloatOrNull() ?: 0f
                    if (spacing <= -150f && sb.isNotEmpty() && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                    i = k
                    continue
                } else {
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
            // Check for UTF-16BE (starts with FEFF or 4 hex digits per char)
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

    private fun cleanExtractedText(text: String): String {
        return text.replace(Regex("""\s+"""), " ").trim()
    }
}
