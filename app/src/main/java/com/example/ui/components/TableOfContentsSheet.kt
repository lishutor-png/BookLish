package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.parser.EpubChapter
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(
    bookTitle: String,
    chapters: List<EpubChapter>,
    currentPageIndex: Int,
    totalPages: Int,
    isPdf: Boolean,
    onSelectPageOrChapter: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MinimalDarkSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MinimalBorder)
            )
        },
        modifier = Modifier.testTag("toc_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Daftar Isi & Bab",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MinimalTextPrimary
            )
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MinimalPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPdf) {
                    items(totalPages) { pageIdx ->
                        val isCurrent = pageIdx == currentPageIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPageOrChapter(pageIdx) }
                                .testTag("toc_page_$pageIdx"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MinimalPrimaryContainer else MinimalDarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrent) MinimalPrimary else MinimalBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Halaman ${pageIdx + 1}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isCurrent) MinimalPrimary else MinimalTextPrimary
                                )
                                if (isCurrent) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Halaman Aktif",
                                        tint = MinimalPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(chapters) { index, chapter ->
                        val isCurrent = index == currentPageIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPageOrChapter(index) }
                                .testTag("toc_chapter_$index"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MinimalPrimaryContainer else MinimalDarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrent) MinimalPrimary else MinimalBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isCurrent) MinimalPrimary else MinimalTextPrimary
                                    )
                                    Text(
                                        text = "${chapter.plainText.split(Regex("\\s+")).filter { it.isNotBlank() }.size} kata",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) MinimalPrimary.copy(alpha = 0.8f) else MinimalTextMuted
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Bab Aktif",
                                        tint = MinimalPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
