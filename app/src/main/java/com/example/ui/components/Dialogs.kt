package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AddBookmarkDialog(
    chapterTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MinimalDarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Tambah Bookmark",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MinimalTextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Tandai: $chapterTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinimalPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Catatan Refleksi (Opsional)", color = MinimalTextMuted) },
                    placeholder = { Text("Misal: Bagian penting untuk kuis esok...", color = MinimalTextMuted.copy(alpha = 0.6f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MinimalDarkSurfaceVariant,
                        unfocusedContainerColor = MinimalDarkSurfaceVariant,
                        focusedBorderColor = MinimalPrimary,
                        unfocusedBorderColor = MinimalBorder,
                        focusedTextColor = MinimalTextPrimary,
                        unfocusedTextColor = MinimalTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("bookmark_note_input"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(noteText) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MinimalPrimary,
                    contentColor = MinimalDarkBackground
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_add_bookmark_button")
            ) {
                Text("Simpan Bookmark", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Batal", color = MinimalTextSecondary)
            }
        }
    )
}

@Composable
fun ClearPageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MinimalDarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Hapus Semua Coretan?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MinimalTextPrimary
            )
        },
        text = {
            Text(
                "Seluruh coretan bolpoin dan stabilo pada halaman ini akan dihapus. Anda tetap dapat menggunakan Undo jika diperlukan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MinimalTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MinimalError),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_clear_page_button")
            ) {
                Text("Hapus Semua", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Batal", color = MinimalTextSecondary)
            }
        }
    )
}
