package com.example.data.parser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.BookRepository
import com.example.data.model.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleBooksProvider {

    suspend fun initializeSampleBooksIfFirstRun(context: Context, repository: BookRepository) =
        withContext(Dispatchers.IO) {
            val booksDir = File(context.filesDir, "books")
            if (!booksDir.exists()) booksDir.mkdirs()

            // 1. Generate Sample EPUB
            val epubFile = File(booksDir, "seni_belajar_efektif.epub")
            if (!epubFile.exists()) {
                createSampleEpub(epubFile)
            }

            // 2. Generate Sample PDF
            val pdfFile = File(booksDir, "panduan_belajar_booklish.pdf")
            if (!pdfFile.exists()) {
                createSamplePdf(pdfFile)
            }

            // Check if already in database
            // If repository has no books, insert the sample books
            val sampleEpubEntity = BookEntity(
                id = 1,
                title = "Seni Belajar Efektif & Membaca Mendalam",
                author = "BookLish Academy",
                fileUri = "",
                localFilePath = epubFile.absolutePath,
                fileType = "EPUB",
                totalPages = 6,
                currentPage = 0,
                progressPercent = 0,
                coverColorHex = "#0284C7",
                isSample = true,
                fileSizeFormatted = "${(epubFile.length() / 1024).coerceAtLeast(12)} KB"
            )

            val samplePdfEntity = BookEntity(
                id = 2,
                title = "Panduan Membaca Aktif & Coretan PDF",
                author = "Dr. Arisandi Prasetya",
                fileUri = "",
                localFilePath = pdfFile.absolutePath,
                fileType = "PDF",
                totalPages = 4,
                currentPage = 0,
                progressPercent = 0,
                coverColorHex = "#059669",
                isSample = true,
                fileSizeFormatted = "${(pdfFile.length() / 1024).coerceAtLeast(18)} KB"
            )

            repository.insertBook(sampleEpubEntity)
            repository.insertBook(samplePdfEntity)
        }

    private fun createSampleEpub(outputFile: File) {
        val chapters = listOf(
            Triple(
                "ch1.xhtml",
                "Bab 1: Seni Membaca Aktif & Pemahaman Mendalam",
                """
                <h2>Bab 1: Seni Membaca Aktif & Pemahaman Mendalam</h2>
                <p>Membaca bukan sekadar memindai kata-kata di halaman dengan mata, melainkan sebuah dialog intelektual aktif antara pembaca dan penulis. Ketika kita membaca secara pasif, informasi cenderung menguap dalam hitungan jam.</p>
                <p>Dalam metode membaca aktif (Active Reading), Anda diajak untuk selalu mengajukan tiga pertanyaan kunci sebelum dan sesudah membaca setiap bab:</p>
                <ul>
                    <li><strong>Apa tujuan utama penulis menyampaikan konsep ini?</strong></li>
                    <li><strong>Bagaimana konsep ini berhubungan dengan apa yang sudah saya ketahui?</strong></li>
                    <li><strong>Bagaimana saya bisa mengaplikasikannya dalam kehidupan sehari-hari?</strong></li>
                </ul>
                <p>Gunakan fitur <em>Stabilo</em> di aplikasi BookLish untuk menandai premis-premis penting, serta <em>Bolpoin Coretan</em> untuk menuliskan pertanyaan reflektif atau sintesis Anda langsung di margin halaman.</p>
                <p>Pahami pula bahwa setiap genre bacaan menuntut ritme yang berbeda. Buku non-fiksi membutuhkan pembacaan terstruktur (inspeksional), sementara sastra membutuhkan keheningan dan imajinasi visual yang kaya.</p>
                """.trimIndent()
            ),
            Triple(
                "ch2.xhtml",
                "Bab 2: Teknik Feynman & Mind Mapping dalam Belajar",
                """
                <h2>Bab 2: Teknik Feynman & Mind Mapping dalam Belajar</h2>
                <p>Richard Feynman, fisikawan pemenang Hadiah Nobel, memiliki metode legendaris untuk menguasai konsep apapun secara mendalam. Prinsip intinya sederhana: <em>"Jika Anda tidak bisa menjelaskannya secara sederhana kepada anak berusia 12 tahun, maka Anda belum benar-benar memahaminya."</em></p>
                <p>Empat langkah praktis Teknik Feynman:</p>
                <ol>
                    <li>Pilih topik atau materi yang ingin dipelajari.</li>
                    <li>Ajarkan konsep tersebut secara tertulis atau lisan dengan bahasa yang sangat sederhana tanpa jargon teknis.</li>
                    <li>Identifikasi bagian-bagian penjelasan yang masih rancu, membingungkan, atau terputus (Knowledge Gaps).</li>
                    <li>Kembalilah ke buku rujukan, pelajari kembali celah tersebut, lalu sederhanakan kembali menggunakan analogi dunia nyata.</li>
                </ol>
                <p>Kombinasikan teknik ini dengan <strong>Mind Mapping</strong> (Peta Pikiran). Hubungkan ide sentral dengan cabang-cabang konsep menggunakan warna dan kata kunci agar memori visual otak terpicu secara optimal.</p>
                """.trimIndent()
            ),
            Triple(
                "ch3.xhtml",
                "Bab 3: Manajemen Waktu Belajar: Pomodoro & Time Blocking",
                """
                <h2>Bab 3: Manajemen Waktu Belajar: Pomodoro & Time Blocking</h2>
                <p>Salah satu hambatan terbesar dalam belajar dan membaca adalah prokrastinasi (menunda-nunda) dan kelelahan mental akibat multitasking. Otak manusia tidak dirancang untuk fokus terbelah dalam durasi panjang.</p>
                <p><strong>Teknik Pomodoro</strong> yang dikembangkan oleh Francesco Cirillo menawarkan solusi ritme kerja yang selaras dengan neurobiologi fokus manusia:</p>
                <ul>
                    <li>Fokus penuh tanpa distraksi selama <strong>25 menit</strong> (1 Pomodoro).</li>
                    <li>Istirahat santai selama <strong>5 menit</strong> (regangkan badan, minum air).</li>
                    <li>Setelah 4 siklus Pomodoro, ambil istirahat panjang selama <strong>15–30 menit</strong>.</li>
                </ul>
                <p>Selama sesi fokus, singkirkan notifikasi ponsel dan ciptakan suasana baca yang tenang. Mode malam (Night Mode) di BookLish dirancang khusus untuk mengurangi emisi cahaya biru yang melelahkan mata saat belajar di malam hari.</p>
                """.trimIndent()
            ),
            Triple(
                "ch4.xhtml",
                "Bab 4: Strategi Menghafal: Spaced Repetition & Flashcards",
                """
                <h2>Bab 4: Strategi Menghafal: Spaced Repetition & Flashcards</h2>
                <p>Kurva Lupa Ebbinghaus (Forgetting Curve) menunjukkan bahwa tanpa pengulangan terencana, manusia kehilangan hingga 70% informasi yang baru dipelajari dalam tempo 24 jam pertama.</p>
                <p>Solusi paling terbukti secara ilmiah adalah <strong>Spaced Repetition System (SRS)</strong>—mengulang materi dengan interval waktu yang bertahap semakin melebar (hari ke-1, hari ke-3, hari ke-7, hari ke-14, hari ke-30).</p>
                <p>Manfaatkan fitur <em>Ringkasan Otomatis</em> dan <em>Flashcards</em> di aplikasi BookLish untuk mengekstrak poin-poin esensial dari dokumen PDF dan EPUB, lalu jadwalkan kuis mandiri secara berkala untuk memperkuat retensi memori jangka panjang.</p>
                """.trimIndent()
            ),
            Triple(
                "ch5.xhtml",
                "Bab 5: Membaca Cepat (Speed Reading) Tanpa Kehilangan Esensi",
                """
                <h2>Bab 5: Membaca Cepat (Speed Reading) Tanpa Kehilangan Esensi</h2>
                <p>Mitos terbesar tentang membaca cepat adalah bahwa Anda harus mengorbankan pemahaman demi kecepatan. Sebenarnya, membaca lambat sering kali memicu lamunan pikiran (mind wandering) karena otak merasa kurang tertantang.</p>
                <p>Berikut pilar penting membaca cepat:</p>
                <ul>
                    <li><strong>Minimalisir Subvokalisasi:</strong> Berhentilah melafalkan setiap kata di dalam pikiran Anda. Latih mata untuk menangkap frasa 3-4 kata sekaligus (Visual Chunking).</li>
                    <li><strong>Gunakan Pemandu Visual:</strong> Gunakan jari atau stylus bolpoin untuk memandu tatapan mata bergerak secara halus tanpa melompat kembali ke kalimat sebelumnya (Regression).</li>
                    <li><strong>Skimming & Scanning:</strong> Kenali struktur bab, judul sub-bab, kalimat pembuka paragraf, dan kesimpulan sebelum masuk ke rincian.</li>
                </ul>
                """.trimIndent()
            ),
            Triple(
                "ch6.xhtml",
                "Bab 6: Membangun Kebiasaan Membaca Seumur Hidup",
                """
                <h2>Bab 6: Membangun Kebiasaan Membaca Seumur Hidup</h2>
                <p>Warren Buffett pernah berkata bahwa membaca 500 halaman setiap hari bekerja seperti bunga majemuk (compound interest) bagi pengetahuan. Pengetahuan Anda akan terakumulasi dan berlipat ganda dari waktu ke waktu.</p>
                <p>Untuk membangun kebiasaan membaca yang abadi:</p>
                <ol>
                    <li>Mulai dari target kecil: 10–15 halaman setiap hari pada jam yang sama (misalnya setelah bangun tidur atau sebelum tidur).</li>
                    <li>Bawalah buku digital di dalam aplikasi BookLish ke mana pun Anda pergi, manfaatkan waktu luang menunggu atau perjalanan untuk membaca.</li>
                    <li>Tuliskan refleksi singkat atau rangkuman 1 paragraf setelah menyelesaikan setiap buku.</li>
                </ol>
                <p>Selamat membaca, menjelajah ilmu baru, dan menikmati perjalanan belajar Anda bersama BookLish!</p>
                """.trimIndent()
            )
        )

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // 1. mimetype
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. META-INF/container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent()
            zos.write(containerXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Chapters
            for ((filename, _, htmlBody) in chapters) {
                zos.putNextEntry(ZipEntry("OEBPS/$filename"))
                val fullHtml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>BookLish Study</title>
                        <style>
                            body { font-family: sans-serif; line-height: 1.6; color: #222; }
                            h2 { color: #0284C7; margin-top: 20px; }
                            p { margin-bottom: 12px; }
                        </style>
                    </head>
                    <body>
                        $htmlBody
                    </body>
                    </html>
                """.trimIndent()
                zos.write(fullHtml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 4. OEBPS/content.opf
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val manifestItems = chapters.mapIndexed { idx, (fn, _, _) ->
                "<item id=\"item$idx\" href=\"$fn\" media-type=\"application/xhtml+xml\"/>"
            }.joinToString("\n        ")

            val spineItems = chapters.mapIndexed { idx, _ ->
                "<itemref idref=\"item$idx\"/>"
            }.joinToString("\n        ")

            val opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Seni Belajar Efektif &amp; Membaca Mendalam</dc:title>
                        <dc:creator>BookLish Academy</dc:creator>
                        <dc:language>id</dc:language>
                    </metadata>
                    <manifest>
                        $manifestItems
                    </manifest>
                    <spine>
                        $spineItems
                    </spine>
                </package>
            """.trimIndent()
            zos.write(opfContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun createSamplePdf(outputFile: File) {
        val pdfDoc = PdfDocument()

        val pagesData = listOf(
            Pair(
                "PANDUAN BELAJAR & MODUL MATERI PDF",
                listOf(
                    "SELAMAT DATANG DI BOOKLISH PDF READER",
                    "Aplikasi ini didesain ringan, cepat, dan nyaman untuk membaca:",
                    "• Bolpoin Gambar: Beri coretan dan catatan tangan dengan ukuran yang dapat diatur.",
                    "• Stabilo Teks: Tandai kalimat dan poin materi penting dengan warna cerah.",
                    "• Penghapus Coretan: Hapus coretan secara presisi atau bersihkan halaman.",
                    "• Pencarian Kata: Temukan kata kunci materi secara instan di seluruh dokumen.",
                    "• Bookmark Cepat: Simpan penanda halaman beserta catatan pribadi.",
                    "• Mode Malam & Font: Sesuaikan ukuran font, jarak baris, dan tema gelap/terang."
                )
            ),
            Pair(
                "BAB 1: SISTEMATIKA ANALISIS DATA & JURNAL",
                listOf(
                    "Struktur Analisis Dokumen Akademik:",
                    "1. Abstrak & Latar Belakang: Intisari permasalahan dan urgensi riset.",
                    "2. Tinjauan Pustaka: Landasan teori dan hipotesis yang diajukan.",
                    "3. Metodologi Penelitian: Desain eksperimen dan teknik pengumpulan data kuantitatif.",
                    "4. Hasil & Pembahasan: Interpretasi temuan dengan komparasi literatur terdahulu.",
                    "5. Kesimpulan & Rekomendasi: Implikasi praktis dan saran untuk penelitian lanjutan.",
                    "Catatan: Gunakan Stabilo kuning untuk menandai argumen utama peneliti."
                )
            ),
            Pair(
                "BAB 2: STRATEGI PEMAHAMAN FORMULA & DIAGRAM",
                listOf(
                    "Langkah Efektif Mempelajari Diagram & Grafik:",
                    "• Baca judul grafik dan identifikasi variabel pada sumbu X dan sumbu Y.",
                    "• Cermati satuan pengukuran dan skala yang digunakan.",
                    "• Amati tren utama: Apakah terjadi kenaikan, penurunan, atau fluktuasi periodik?",
                    "• Temukan titik anomali (outliers) dan hubungkan dengan konteks studi.",
                    "• Gunakan bolpoin di BookLish untuk melingkari titik kritis pada grafik ini."
                )
            ),
            Pair(
                "BAB 3: RANGKUMAN KUIS & PERSIAPAN UJIAN",
                listOf(
                    "Checklist Kesiapan Ujian & Diskusi Kelompok:",
                    "[ ] Review seluruh catatan bolpoin dan highlight stabilo pada dokumen.",
                    "[ ] Uji pemahaman dengan meninjau penanda bookmark & poin penting.",
                    "[ ] Kerjakan latihan studi kasus tanpa melihat kunci jawaban.",
                    "[ ] Diskusi kelompok untuk menguji pemahaman konsep-konsep tersulit.",
                    "Sukses selalu untuk studi Anda bersama aplikasi BookLish!"
                )
            )
        )

        for ((idx, pageInfo) in pagesData.withIndex()) {
            val pageInfoBuilder = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create() // A4 size
            val page = pdfDoc.startPage(pageInfoBuilder)
            val canvas: Canvas = page.canvas

            // Background
            val bgPaint = Paint().apply { color = Color.rgb(250, 248, 245) }
            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

            // Header Banner
            val headerPaint = Paint().apply { color = Color.rgb(2, 132, 199) }
            canvas.drawRect(0f, 0f, 595f, 60f, headerPaint)

            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("BookLish Reader - Modul Belajar PDF", 30f, 38f, titlePaint)

            // Page Title
            val pageTitlePaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 18f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(pageInfo.first, 35f, 110f, pageTitlePaint)

            // Divider line
            val linePaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 2f
            }
            canvas.drawLine(35f, 125f, 560f, 125f, linePaint)

            // Body Lines
            val bodyPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            var currentY = 160f
            for (line in pageInfo.second) {
                if (line.startsWith("•") || line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.") || line.startsWith("4.") || line.startsWith("5.") || line.startsWith("[")) {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    bodyPaint.textSize = 13.5f
                } else if (line.endsWith(":")) {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    bodyPaint.textSize = 14.5f
                    currentY += 8f
                } else {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    bodyPaint.textSize = 14f
                }
                canvas.drawText(line, 40f, currentY, bodyPaint)
                currentY += 32f
            }

            // Interactive Sample Visual Box
            val boxPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(40f, 560f, 555f, 740f, 12f, 12f, boxPaint)
            canvas.drawRoundRect(40f, 560f, 555f, 740f, 12f, 12f, borderPaint)

            val boxTitlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("Area Coretan & Latihan Catatan (Gunakan Tool Bolpoin/Stabilo):", 55f, 590f, boxTitlePaint)

            val hintPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            canvas.drawText("Pilih ikon Bolpoin di atas untuk menulis langsung atau Stabilo untuk mewarnai baris.", 55f, 620f, hintPaint)

            // Footer
            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 11f
                isAntiAlias = true
            }
            canvas.drawText("Halaman ${idx + 1} dari ${pagesData.size} | BookLish Interactive PDF", 35f, 800f, footerPaint)

            pdfDoc.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
    }
}
