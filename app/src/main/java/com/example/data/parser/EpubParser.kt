package com.example.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class EpubChapter(
    val id: String,
    val title: String,
    val plainText: String,
    val htmlContent: String,
    val order: Int
)

data class ParsedEpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
    val totalWords: Int = 0
)

object EpubParser {
    private const val TAG = "EpubParser"

    fun parse(context: Context, uri: Uri): ParsedEpubBook {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream for $uri")
        return parseFromStream(inputStream, uri.lastPathSegment ?: "Unknown Book")
    }

    fun parseFromFile(file: File): ParsedEpubBook {
        val inputStream = FileInputStream(file)
        return parseFromStream(inputStream, file.nameWithoutExtension)
    }

    fun parseFromStream(inputStream: InputStream, fallbackTitle: String): ParsedEpubBook {
        val zipFiles = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zipFiles[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // 1. Locate container.xml
        val containerXml = zipFiles["META-INF/container.xml"]?.let { String(it, Charsets.UTF_8) }
        var opfPath = "OEBPS/content.opf"
        if (containerXml != null) {
            val rootFileMatch = Regex("full-path=\"([^\"]+)\"").find(containerXml)
            if (rootFileMatch != null) {
                opfPath = rootFileMatch.groupValues[1]
            }
        }

        // Check if opf exists or find any .opf
        var opfBytes = zipFiles[opfPath]
        if (opfBytes == null) {
            val foundOpf = zipFiles.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            if (foundOpf != null) {
                opfPath = foundOpf
                opfBytes = zipFiles[foundOpf]
            }
        }

        val baseDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        var bookTitle = fallbackTitle
        var bookAuthor = "Penulis Tidak Diketahui"
        val manifest = mutableMapOf<String, String>() // id -> href
        val spine = mutableListOf<String>() // list of idref

        if (opfBytes != null) {
            val opfContent = String(opfBytes, Charsets.UTF_8)

            // Extract title
            val titleMatch = Regex("<dc:title[^>]*>([^<]+)</dc:title>", RegexOption.IGNORE_CASE).find(opfContent)
            if (titleMatch != null) {
                bookTitle = cleanHtmlEntities(titleMatch.groupValues[1].trim())
            }

            // Extract author/creator
            val authorMatch = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>", RegexOption.IGNORE_CASE).find(opfContent)
            if (authorMatch != null) {
                bookAuthor = cleanHtmlEntities(authorMatch.groupValues[1].trim())
            }

            // Extract manifest items
            val itemRegex = Regex("<item\\s+[^>]*>", RegexOption.IGNORE_CASE)
            itemRegex.findAll(opfContent).forEach { match ->
                val tag = match.value
                val idMatch = Regex("id=\"([^\"]+)\"").find(tag)
                val hrefMatch = Regex("href=\"([^\"]+)\"").find(tag)
                if (idMatch != null && hrefMatch != null) {
                    manifest[idMatch.groupValues[1]] = hrefMatch.groupValues[1]
                }
            }

            // Extract spine items
            val spineItemRegex = Regex("<itemref\\s+[^>]*idref=\"([^\"]+)\"[^>]*>", RegexOption.IGNORE_CASE)
            spineItemRegex.findAll(opfContent).forEach { match ->
                spine.add(match.groupValues[1])
            }
        }

        // Build chapters in spine order
        val chapters = mutableListOf<EpubChapter>()
        var chapterIndex = 0

        // If spine is found, iterate through it
        if (spine.isNotEmpty()) {
            for (idref in spine) {
                val href = manifest[idref] ?: continue
                val fullPath = normalizeZipPath(baseDir + href)
                val fileData = zipFiles[fullPath] ?: zipFiles[href]
                    ?: zipFiles.entries.firstOrNull { it.key.endsWith(href) }?.value
                if (fileData != null) {
                    val rawHtml = String(fileData, Charsets.UTF_8)
                    val parsedChapter = createChapterFromHtml(rawHtml, idref, chapterIndex)
                    if (parsedChapter.plainText.isNotBlank()) {
                        chapters.add(parsedChapter)
                        chapterIndex++
                    }
                }
            }
        }

        // Fallback: If spine was empty or couldn't parse, load all .html / .xhtml files sorted
        if (chapters.isEmpty()) {
            val htmlFiles = zipFiles.keys.filter {
                it.endsWith(".html", ignoreCase = true) || it.endsWith(".xhtml", ignoreCase = true) || it.endsWith(".htm", ignoreCase = true)
            }.sorted()

            for ((idx, fileKey) in htmlFiles.withIndex()) {
                val data = zipFiles[fileKey] ?: continue
                val rawHtml = String(data, Charsets.UTF_8)
                val chapter = createChapterFromHtml(rawHtml, fileKey, idx)
                if (chapter.plainText.isNotBlank()) {
                    chapters.add(chapter)
                }
            }
        }

        // Final fallback if empty
        if (chapters.isEmpty()) {
            chapters.add(
                EpubChapter(
                    id = "ch_fallback",
                    title = "Halaman 1",
                    plainText = "Tidak dapat mengekstrak teks dari format EPUB ini secara langsung.",
                    htmlContent = "<p>Tidak dapat mengekstrak teks dari format EPUB ini secara langsung.</p>",
                    order = 0
                )
            )
        }

        val totalWords = chapters.sumOf { it.plainText.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }

        return ParsedEpubBook(
            title = bookTitle,
            author = bookAuthor,
            chapters = chapters,
            totalWords = totalWords
        )
    }

    private fun createChapterFromHtml(rawHtml: String, id: String, order: Int): EpubChapter {
        // Extract title from <title> or <h1> or <h2>
        var title = ""
        val titleTag = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(rawHtml)
        if (titleTag != null && titleTag.groupValues[1].isNotBlank()) {
            title = titleTag.groupValues[1].trim()
        } else {
            val h1Tag = Regex("<h1[^>]*>([^<]+)</h1>", RegexOption.IGNORE_CASE).find(rawHtml)
            if (h1Tag != null && h1Tag.groupValues[1].isNotBlank()) {
                title = h1Tag.groupValues[1].trim()
            } else {
                val h2Tag = Regex("<h2[^>]*>([^<]+)</h2>", RegexOption.IGNORE_CASE).find(rawHtml)
                if (h2Tag != null && h2Tag.groupValues[1].isNotBlank()) {
                    title = h2Tag.groupValues[1].trim()
                }
            }
        }

        title = cleanHtmlEntities(title)
        if (title.isBlank() || title.equals("Untitled", ignoreCase = true)) {
            title = "Bab ${order + 1}"
        }

        // Clean HTML to structured plain text
        var bodyContent = rawHtml
        val bodyMatch = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE).find(rawHtml)
        if (bodyMatch != null) {
            bodyContent = bodyMatch.groupValues[1]
        }

        // Remove script and style tags
        bodyContent = bodyContent.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        bodyContent = bodyContent.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

        // Format block elements with double newlines
        var text = bodyContent
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</h1>|</h2>|</h3>|</h4>|</h5>|</h6>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</div>|</blockquote>|</tr>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), " ") // Strip all remaining tags

        text = cleanHtmlEntities(text)

        // Clean up excessive whitespace while keeping paragraph breaks
        val cleanedLines = text.lines().map { it.trim() }
        val finalParagraphs = mutableListOf<String>()
        var emptyCount = 0

        for (line in cleanedLines) {
            if (line.isEmpty()) {
                emptyCount++
                if (emptyCount <= 1 && finalParagraphs.isNotEmpty()) {
                    finalParagraphs.add("")
                }
            } else {
                emptyCount = 0
                finalParagraphs.add(line)
            }
        }

        val plainText = finalParagraphs.joinToString("\n").trim()

        return EpubChapter(
            id = id,
            title = title,
            plainText = plainText,
            htmlContent = rawHtml,
            order = order
        )
    }

    private fun cleanHtmlEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { match ->
                try {
                    match.groupValues[1].toInt().toChar().toString()
                } catch (e: Exception) {
                    ""
                }
            }
    }

    private fun normalizeZipPath(path: String): String {
        val parts = path.split("/")
        val stack = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            } else if (part != "." && part.isNotEmpty()) {
                stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
