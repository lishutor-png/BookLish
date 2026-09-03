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

object SamplePdfProvider {

    private const val PREFS_NAME = "lishpdf_prefs"
    private const val KEY_SAMPLE_INITIALIZED = "sample_pdf_initialized_v3"

    suspend fun initializeSampleBooksIfFirstRun(context: Context, repository: BookRepository) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isAlreadyInitialized = prefs.getBoolean(KEY_SAMPLE_INITIALIZED, false)

            if (isAlreadyInitialized) {
                // Never re-add deleted sample documents!
                return@withContext
            }

            val booksDir = File(context.filesDir, "books")
            if (!booksDir.exists()) booksDir.mkdirs()

            // 1. Generate Sample PDF 1: Panduan Lengkap LishPDF
            val pdfFile1 = File(booksDir, "panduan_lengkap_lishpdf.pdf")
            if (!pdfFile1.exists()) {
                createGuideSamplePdf(pdfFile1)
            }

            // 2. Generate Sample PDF 2: Lembar Catatan Studi & Sketsa
            val pdfFile2 = File(booksDir, "modul_studi_dan_sketsa.pdf")
            if (!pdfFile2.exists()) {
                createStudyNotesSamplePdf(pdfFile2)
            }

            val samplePdfEntity1 = BookEntity(
                title = "Panduan Pintar LishPDF & Fitur Coretan",
                author = "LishPDF Studio",
                fileUri = "",
                localFilePath = pdfFile1.absolutePath,
                fileType = "PDF",
                totalPages = 4,
                currentPage = 0,
                progressPercent = 0,
                coverColorHex = "#DC2626",
                isSample = true,
                fileSizeFormatted = "${(pdfFile1.length() / 1024).coerceAtLeast(16)} KB"
            )

            val samplePdfEntity2 = BookEntity(
                title = "Modul Catatan Studi & Lembar Sketsa",
                author = "Dr. Arisandi Prasetya",
                fileUri = "",
                localFilePath = pdfFile2.absolutePath,
                fileType = "PDF",
                totalPages = 3,
                currentPage = 0,
                progressPercent = 0,
                coverColorHex = "#0284C7",
                isSample = true,
                fileSizeFormatted = "${(pdfFile2.length() / 1024).coerceAtLeast(14)} KB"
            )

            repository.insertBook(samplePdfEntity1)
            repository.insertBook(samplePdfEntity2)

            // Mark as initialized so deleted files are never regenerated
            prefs.edit().putBoolean(KEY_SAMPLE_INITIALIZED, true).apply()
        }

    private fun createGuideSamplePdf(outputFile: File) {
        val pdfDoc = PdfDocument()

        val pagesData = listOf(
            Pair(
                "SELAMAT DATANG DI LISHPDF",
                listOf(
                    "FITUR UTAMA APLIKASI PEMBACA PDF:",
                    "• Bolpoin Gambar & Coretan: Tulis catatan tangan langsung di atas dokumen dengan warna & ketebalan presisi.",
                    "• Stabilo Highlight Teks: Berikan penanda transparan yang indah pada kalimat kunci.",
                    "• Gestur 2 Jari Pintar (Pinch & Pan): Saat mode menulis aktif, gunakan 2 jari untuk zoom in / zoom out dan menggeser kanvas tanpa menggores coretan yang tidak sengaja.",
                    "• Penghapus Coretan: Sentuh garis coretan yang ingin dihapus atau bersihkan seluruh halaman.",
                    "• Bookmark Cepat: Simpan penanda halaman penting disertai catatan ringkas.",
                    "• Tema Putih Bersih: Tampilan jernih, kontras tinggi, dan nyaman untuk membaca dokumen tebal."
                )
            ),
            Pair(
                "PANDUAN GESTUR 2 JARI & MENULIS",
                listOf(
                    "Cara Menggunakan Gestur Zoom & Geser Kanvas:",
                    "1. Menulis / Menggambar (1 Jari): Sentuh dan tarik 1 jari pada layar dengan alat Bolpoin atau Stabilo aktif.",
                    "2. Zoom & Geser (2 Jari): Tempelkan 2 jari di layar, lalu cubit (pinch) untuk zoom dan geser kanvas bebas.",
                    "3. Presisi Maksimal: Perbesar dokumen hingga 300% untuk menulis catatan kecil di margin dokumen.",
                    "4. Reset Zoom Cepat: Ketuk tombol zoom 100% di toolbar untuk kembali ke ukuran pas layar.",
                    "5. Undo & Redo: Kembalikan atau ulangi goresan coretan kapan saja dengan tombol panah melingkar."
                )
            ),
            Pair(
                "METODE MEMBACA AKTIF PADA PDF",
                listOf(
                    "Tips Memaksimalkan Pemahaman Dokumen:",
                    "• Tandai premis utama dengan Stabilo kuning atau hijau.",
                    "• Tuliskan pertanyaan reflektif atau istilah baru pada margin kanan.",
                    "• Berikan penanda Bookmark pada bab atau rumus penting untuk akses instan dari daftar isi.",
                    "• Gunakan fitur Cari Kata untuk melompat langsung ke istilah teknis di seluruh halaman dokumen.",
                    "• Seluruh goresan coretan otomatis tersimpan offline di database perangkat Anda."
                )
            ),
            Pair(
                "LEMBAR LATIHAN CORETAN & CATATAN",
                listOf(
                    "Ruang Uji Coba Fitur Bolpoin & Stabilo:",
                    "Silakan coba aktifkan mode Bolpoin atau Stabilo di toolbar bawah.",
                    "Cobalah menulis tanda tangan, melingkari teks di bawah, atau membuat sketsa:",
                    "[  ] Coba coret dengan bolpoin merah / biru.",
                    "[  ] Coba stabilo kuning pada baris ini.",
                    "[  ] Coba zoom kanvas dengan 2 jari untuk menulis lebih detail.",
                    "[  ] Simpan bookmark halaman ini."
                )
            )
        )

        for ((idx, pageInfo) in pagesData.withIndex()) {
            val pageInfoBuilder = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create() // A4
            val page = pdfDoc.startPage(pageInfoBuilder)
            val canvas: Canvas = page.canvas

            // Crisp White Background
            val bgPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

            // Header Banner in LishPDF Crimson Red
            val headerPaint = Paint().apply { color = Color.rgb(220, 38, 38) }
            canvas.drawRect(0f, 0f, 595f, 58f, headerPaint)

            val logoPaint = Paint().apply {
                color = Color.WHITE
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("LishPDF - Panduan Membaca & Coretan Pintar", 30f, 36f, logoPaint)

            // Page Title
            val pageTitlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 17f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(pageInfo.first, 35f, 105f, pageTitlePaint)

            // Divider line
            val linePaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1.8f
            }
            canvas.drawLine(35f, 120f, 560f, 120f, linePaint)

            // Body Lines
            val bodyPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 13.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            var currentY = 155f
            for (line in pageInfo.second) {
                if (line.endsWith(":")) {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    bodyPaint.textSize = 14f
                    bodyPaint.color = Color.rgb(15, 23, 42)
                    currentY += 8f
                } else if (line.startsWith("•") || line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.") || line.startsWith("4.") || line.startsWith("5.") || line.startsWith("[")) {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    bodyPaint.textSize = 13f
                    bodyPaint.color = Color.rgb(51, 65, 85)
                } else {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    bodyPaint.textSize = 13.5f
                    bodyPaint.color = Color.rgb(71, 85, 105)
                }
                canvas.drawText(line, 40f, currentY, bodyPaint)
                currentY += 32f
            }

            // Interactive Exercise Canvas Box
            val boxPaint = Paint().apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            canvas.drawRoundRect(35f, 560f, 560f, 750f, 12f, 12f, boxPaint)
            canvas.drawRoundRect(35f, 560f, 560f, 750f, 12f, 12f, borderPaint)

            val boxTitlePaint = Paint().apply {
                color = Color.rgb(220, 38, 38)
                textSize = 12.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("Area Latihan Coretan Langsung (Bolpoin / Stabilo):", 50f, 590f, boxTitlePaint)

            val hintPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            canvas.drawText("Pilih bolpoin untuk menulis, dan gunakan gestur 2 jari untuk zoom/geser kanvas.", 50f, 618f, hintPaint)

            // Footer
            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 11f
                isAntiAlias = true
            }
            canvas.drawText("Halaman ${idx + 1} dari ${pagesData.size} | LishPDF Document", 35f, 805f, footerPaint)

            pdfDoc.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
    }

    private fun createStudyNotesSamplePdf(outputFile: File) {
        val pdfDoc = PdfDocument()

        val pagesData = listOf(
            Pair(
                "MODUL KAJIAN RISET & ANALISIS DATA",
                listOf(
                    "Struktur Sistematika Analisis:",
                    "1. Latar Belakang & Identifikasi Masalah: Rumusan signifikansi topik.",
                    "2. Kajian Pustaka & Kerangka Teoretis: Penelusuran literatur komparatif.",
                    "3. Metode & Desain Pengujian: Teknik pengumpulan data kuantitatif & kualitatif.",
                    "4. Analisis Hasil & Pembahasan: Interpretasi statistik serta temuan utama.",
                    "5. Kesimpulan & Rekomendasi Aksi: Implikasi strategis ke depan."
                )
            ),
            Pair(
                "FORMULA & VISUALISASI DIAGRAM",
                listOf(
                    "Langkah Membedah Diagram & Grafik Penelitian:",
                    "• Identifikasi sumbu X (variabel independen) dan sumbu Y (variabel dependen).",
                    "• Teliti satuan ukur, margin kesalahan, dan tingkat kepercayaan (confidence level).",
                    "• Analisis pola tren: Eksponensial, linier, atau saturasi musiman.",
                    "• Lingkari titik anomali menggunakan alat Bolpoin untuk dibahas dalam tim.",
                    "• Gunakan Stabilo pada bagian kesimpulan hipotesis."
                )
            ),
            Pair(
                "RINGKASAN AKHIR & RENCANA AKSI",
                listOf(
                    "Daftar Checklist Tindak Lanjut:",
                    "[  ] Review seluruh anotasi coretan bolpoin dan stabilo pada dokumen.",
                    "[  ] Simpan bookmark pada lembar data utama untuk presentasi.",
                    "[  ] Ekspor dan bagikan catatan kepada rekan riset.",
                    "[  ] Terapkan rekomendasi hasil studi dalam rencana kerja tahun berjalan."
                )
            )
        )

        for ((idx, pageInfo) in pagesData.withIndex()) {
            val pageInfoBuilder = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create()
            val page = pdfDoc.startPage(pageInfoBuilder)
            val canvas: Canvas = page.canvas

            val bgPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

            // Header Banner in Navy / Blue
            val headerPaint = Paint().apply { color = Color.rgb(2, 132, 199) }
            canvas.drawRect(0f, 0f, 595f, 58f, headerPaint)

            val logoPaint = Paint().apply {
                color = Color.WHITE
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("LishPDF - Modul Catatan Studi & Lembar Sketsa", 30f, 36f, logoPaint)

            val pageTitlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 17f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(pageInfo.first, 35f, 105f, pageTitlePaint)

            val linePaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1.8f
            }
            canvas.drawLine(35f, 120f, 560f, 120f, linePaint)

            val bodyPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 13.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            var currentY = 155f
            for (line in pageInfo.second) {
                if (line.endsWith(":")) {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    bodyPaint.textSize = 14f
                    bodyPaint.color = Color.rgb(15, 23, 42)
                    currentY += 8f
                } else if (line.startsWith("•") || line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.") || line.startsWith("4.") || line.startsWith("5.") || line.startsWith("[")) {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    bodyPaint.textSize = 13f
                    bodyPaint.color = Color.rgb(51, 65, 85)
                } else {
                    bodyPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    bodyPaint.textSize = 13.5f
                    bodyPaint.color = Color.rgb(71, 85, 105)
                }
                canvas.drawText(line, 40f, currentY, bodyPaint)
                currentY += 32f
            }

            // Interactive Box
            val boxPaint = Paint().apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            canvas.drawRoundRect(35f, 560f, 560f, 750f, 12f, 12f, boxPaint)
            canvas.drawRoundRect(35f, 560f, 560f, 750f, 12f, 12f, borderPaint)

            val boxTitlePaint = Paint().apply {
                color = Color.rgb(2, 132, 199)
                textSize = 12.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("Catatan Tambahan & Anotasi Sketsa Mahasiswa:", 50f, 590f, boxTitlePaint)

            val hintPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            canvas.drawText("Gunakan alat Bolpoin atau Stabilo untuk mencatat ide dan analisis Anda.", 50f, 618f, hintPaint)

            // Footer
            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 11f
                isAntiAlias = true
            }
            canvas.drawText("Halaman ${idx + 1} dari ${pagesData.size} | LishPDF Modul Studi", 35f, 805f, footerPaint)

            pdfDoc.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
    }
}
