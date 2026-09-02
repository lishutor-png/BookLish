package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
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
        modifier = Modifier.testTag("search_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Pencarian Kata",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MinimalTextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_query_input"),
                placeholder = {
                    Text(
                        "Ketik kata atau frasa yang dicari...",
                        color = MinimalTextMuted,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Cari",
                        tint = MinimalPrimary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Hapus Pencarian",
                                tint = MinimalTextSecondary
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MinimalDarkSurfaceVariant,
                    unfocusedContainerColor = MinimalDarkSurfaceVariant,
                    focusedBorderColor = MinimalPrimary,
                    unfocusedBorderColor = MinimalBorder,
                    focusedTextColor = MinimalTextPrimary,
                    unfocusedTextColor = MinimalTextPrimary
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MinimalPrimary,
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (searchQuery.length >= 2) {
                Text(
                    text = "Ditemukan ${searchResults.size} kecocokan",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MinimalPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada hasil untuk \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MinimalTextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.id }) { result ->
                            SearchResultItemCard(
                                result = result,
                                searchQuery = searchQuery,
                                onClick = { onResultClick(result) }
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ketik minimal 2 huruf untuk mulai mencari di dokumen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MinimalTextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultItemCard(
    result: SearchResult,
    searchQuery: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_result_item_${result.pageOrChapterIndex}"),
        shape = RoundedCornerShape(12.dp),
        color = MinimalDarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = result.pageOrChapterTitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MinimalPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Highlight the searched keyword in snippet
            val annotatedSnippet = buildAnnotatedString {
                val snippetText = result.snippet
                var lastIdx = 0
                val pattern = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
                pattern.findAll(snippetText).forEach { match ->
                    if (match.range.first > lastIdx) {
                        append(snippetText.substring(lastIdx, match.range.first))
                    }
                    withStyle(
                        style = SpanStyle(
                            background = MinimalPrimaryContainer,
                            color = MinimalPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(match.value)
                    }
                    lastIdx = match.range.last + 1
                }
                if (lastIdx < snippetText.length) {
                    append(snippetText.substring(lastIdx))
                }
            }

            Text(
                text = annotatedSnippet,
                style = MaterialTheme.typography.bodySmall,
                color = MinimalTextPrimary
            )
        }
    }
}
