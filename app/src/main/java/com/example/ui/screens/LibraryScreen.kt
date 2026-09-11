package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ArticleFreshness
import com.example.data.model.ArticleStatus
import com.example.data.model.DateSource
import com.example.data.model.ExtractedArticle
import com.example.data.model.ExtractionStats
import com.example.ui.FilterState
import com.example.ui.formatStorageSize
import com.example.ui.theme.ColorError
import com.example.ui.theme.ColorSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    articles: List<ExtractedArticle>,
    stats: ExtractionStats,
    filterState: FilterState,
    freshnessState: ArticleFreshness = ArticleFreshness.CACHED,
    onRefreshLibrary: () -> Unit = {},
    onSearchChange: (String) -> Unit,
    onSourceFilterChange: (String?) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onStatusFilterChange: (ArticleStatus?) -> Unit,
    onToggleOfflineFilter: (Boolean) -> Unit = {},
    onToggleArticleOffline: (ExtractedArticle) -> Unit = {},
    onClearFilters: () -> Unit,
    onSelectArticle: (ExtractedArticle) -> Unit,
    onDeleteArticle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("الكل", "تكنولوجيا", "اقتصاد", "علوم", "سياسة", "عام")
    val sources = articles.map { it.sourceName }.distinct()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // Library Stats & Freshness Header
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("library_stats_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مكتبة المحتوى المستخرج",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "تدقيق التواريخ ومنع التكرار مفعل",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Freshness Badge + Refresh button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (freshnessState) {
                                    ArticleFreshness.LIVE -> ColorSuccess.copy(alpha = 0.2f)
                                    ArticleFreshness.CACHED -> MaterialTheme.colorScheme.surfaceVariant
                                    ArticleFreshness.OFFLINE -> ColorError.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = freshnessState.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (freshnessState) {
                                        ArticleFreshness.LIVE -> ColorSuccess
                                        ArticleFreshness.CACHED -> MaterialTheme.colorScheme.onSurfaceVariant
                                        ArticleFreshness.OFFLINE -> ColorError
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            IconButton(
                                onClick = onRefreshLibrary,
                                modifier = Modifier.size(32.dp).testTag("refresh_library_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تحديث القائمة والتحقق من الحداثة",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${stats.totalArticles} مقال",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("مقالات محفوظة", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(
                                text = "${stats.totalSources} مصدر",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("مصادر مستقلة", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(
                                text = "${stats.totalImages} صورة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("وسائط مستخرجة", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(
                                text = formatStorageSize(stats.totalStorageKb),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("مستخدم محلياً", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("ابحث في المقالات والمصادر...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_articles_input")
            )
        }

        // Category Filter Chips
        item {
            Text(
                text = "التصنيف:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = (cat == "الكل" && filterState.selectedCategory == null) ||
                            (filterState.selectedCategory == cat)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (cat == "الكل") onCategoryFilterChange(null)
                            else onCategoryFilterChange(cat)
                        },
                        label = { Text(cat) }
                    )
                }
            }
        }

        // Status Filter Chips
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "الحالة:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                FilterChip(
                    selected = filterState.selectedStatus == null,
                    onClick = { onStatusFilterChange(null) },
                    label = { Text("الكل") }
                )
                FilterChip(
                    selected = filterState.selectedStatus == ArticleStatus.SUCCESS,
                    onClick = { onStatusFilterChange(ArticleStatus.SUCCESS) },
                    label = { Text("ناجح") }
                )
                FilterChip(
                    selected = filterState.selectedStatus == ArticleStatus.FAILED,
                    onClick = { onStatusFilterChange(ArticleStatus.FAILED) },
                    label = { Text("يحتاج إعادة محاولة") }
                )

                FilterChip(
                    selected = filterState.onlyOfflineSaved,
                    onClick = { onToggleOfflineFilter(!filterState.onlyOfflineSaved) },
                    leadingIcon = {
                        Icon(
                            if (filterState.onlyOfflineSaved) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("المحفوظة أوفلاين") },
                    modifier = Modifier.testTag("filter_offline_chip")
                )
            }
        }

        // Articles Count Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "النتائج (${articles.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (filterState.searchQuery.isNotBlank() || filterState.selectedCategory != null || filterState.selectedStatus != null) {
                    TextButton(onClick = onClearFilters) {
                        Text("إعادة ضبط التصفية")
                    }
                }
            }
        }

        // Articles List
        if (articles.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "لا توجد مقالات مطابقة لشروط البحث",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(articles) { article ->
                LibraryArticleCard(
                    article = article,
                    onClick = { onSelectArticle(article) },
                    onDelete = { onDeleteArticle(article.id) },
                    onToggleOffline = { onToggleArticleOffline(article) }
                )
            }
        }
    }
}

@Composable
fun LibraryArticleCard(
    article: ExtractedArticle,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleOffline: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isFailed = article.status == ArticleStatus.FAILED
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("article_card_${article.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top badges: Source & Category & New Badge & Offline Badge & Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = article.sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = article.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Explicit New vs Archive Badge
                    if (article.isNew) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "🔥 جديد",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "أرشيف",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Offline Badge
                    if (article.isSavedOffline) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ColorSuccess.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    tint = ColorSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "أوفلاين",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isFailed) ColorError.copy(alpha = 0.15f) else ColorSuccess.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isFailed) "يحتاج إعادة محاولة" else "ناجح",
                        color = if (isFailed) ColorError else ColorSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title & Thumbnail if available
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        lineHeight = 18.sp
                    )
                }

                if (article.imageUrls.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 80.dp, height = 70.dp)
                    ) {
                        AsyncImage(
                            model = article.imageUrls.first(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata: Date + Source Type + Images + Storage
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(article.publishedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = article.dateSource.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("${article.successfulImagesCount} صور", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(formatStorageSize(article.dataSizeKb), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleOffline,
                        modifier = Modifier.size(28.dp).testTag("card_toggle_offline_${article.id}")
                    ) {
                        Icon(
                            if (article.isSavedOffline) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                            contentDescription = if (article.isSavedOffline) "محفوظ أوفلاين" else "حفظ أوفلاين",
                            tint = if (article.isSavedOffline) ColorSuccess else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
