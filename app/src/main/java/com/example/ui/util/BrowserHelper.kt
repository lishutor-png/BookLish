package com.example.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object BrowserHelper {

    /**
     * Opens the provided URL in an external web browser installed on the user's phone.
     * Uses an Intent chooser so the user can select their preferred browser (Chrome, Firefox, etc.).
     */
    fun openInBrowser(context: Context, rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(context, "Tautan kosong atau tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        var url = trimmed
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true) &&
            !url.startsWith("mailto:", ignoreCase = true)
        ) {
            url = "https://$url"
        }

        try {
            val uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Buka tautan dengan browser:").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            try {
                val directIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(directIntent)
            } catch (e2: Exception) {
                Toast.makeText(
                    context,
                    "Tidak dapat membuka browser: ${e2.localizedMessage ?: "Tidak ada aplikasi browser yang ditemukan"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Copies the URL to the Android system clipboard.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Tautan PDF") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Tautan berhasil disalin", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal menyalin tautan", Toast.LENGTH_SHORT).show()
        }
    }
}
