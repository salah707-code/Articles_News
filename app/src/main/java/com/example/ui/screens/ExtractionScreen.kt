package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArticleStatus
import com.example.data.model.ExtractedArticle
import com.example.data.model.ExtractionStage
import com.example.engine.LiveExtractionUiState
import com.example.ui.formatStorageSize
import com.example.ui.theme.ColorError
import com.example.ui.theme.ColorSuccess
import com.example.ui.theme.ColorWarning

@Composable
fun ExtractionScreen(
    state: LiveExtractionUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onContinueWithoutImages: () -> Unit,
    onRetryFailed: () -> Unit,
    onRetrySingleArticle: (Long) -> Unit,
    onSaveAll: () -> Unit,
    onViewArticles: () -> Unit,
    onSelectArticle: (ExtractedArticle) -> Unit,
    onNewExtraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // Target domain header
        if (state.targetUrl.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.siteName.ifBlank { "الموقع المستهدف" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.targetUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 1. Connection Restored notification if available
        if (state.connectionRestoredMessage != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorSuccess.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = ColorSuccess)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.connectionRestoredMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorSuccess
                        )
                    }
                }
            }
        }

        // Render according to the active Stage
        when (state.stage) {
            ExtractionStage.IDLE -> {
                item {
                    IdleStateView(onNewExtraction = onNewExtraction)
                }
            }

            ExtractionStage.ANALYZING -> {
                item {
                    AnalyzingStateView(state = state, onCancel = onCancel)
                }
            }

            ExtractionStage.EXTRACTING, ExtractionStage.PAUSED -> {
                item {
                    ExtractingStateView(
                        state = state,
                        onPause = onPause,
                        onResume = onResume,
                        onCancel = onCancel
                    )
                }

                // Notice: failure of an article does not stop the workflow
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ملاحظة: فشل مقال أو صورة لا يوقف استمرار بقية عملية الاستخراج.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Live articles stream
                item {
                    Text(
                        text = "المقالات الجاري استخراجها (${state.currentSessionArticles.size}):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.currentSessionArticles) { article ->
                    LiveArticleItemCard(
                        article = article,
                        onRetrySingle = { onRetrySingleArticle(article.id) },
                        onClick = { onSelectArticle(article) }
                    )
                }
            }

            ExtractionStage.SUCCESS -> {
                item {
                    SuccessStateView(
                        state = state,
                        onViewArticles = onViewArticles,
                        onRetryFailed = onRetryFailed,
                        onSaveAll = onSaveAll
                    )
                }

                item {
                    Text(
                        text = "نتائج المقالات المستخرجة (${state.currentSessionArticles.size}):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.currentSessionArticles) { article ->
                    LiveArticleItemCard(
                        article = article,
                        onRetrySingle = { onRetrySingleArticle(article.id) },
                        onClick = { onSelectArticle(article) }
                    )
                }
            }

            ExtractionStage.INTERNET_LOST -> {
                item {
                    InternetLostStateView(
                        state = state,
                        onResume = onResume,
                        onCancel = onCancel
                    )
                }
            }

            ExtractionStage.STORAGE_LOW -> {
                item {
                    StorageLowStateView(
                        state = state,
                        onContinueWithoutImages = onContinueWithoutImages,
                        onCancel = onCancel
                    )
                }
            }

            ExtractionStage.ERROR_NO_ARTICLES -> {
                item {
                    NoArticlesErrorView(
                        state = state,
                        onNewExtraction = onNewExtraction
                    )
                }
            }

            ExtractionStage.ERROR_CONNECTION -> {
                item {
                    ConnectionErrorView(
                        state = state,
                        onRetry = onNewExtraction
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// Sub-views for each required stage
// ----------------------------------------------------

@Composable
fun IdleStateView(onNewExtraction: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "جاهز لبدء استخراج جديد",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ألصق رابط أي موقع أو صفحة إخبارية للبدء في اكتشاف المقالات وتنزيل المحتوى والصور فورياً.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onNewExtraction,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("الانتقال إلى إدخال رابط")
            }
        }
    }
}

@Composable
fun AnalyzingStateView(
    state: LiveExtractionUiState,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("analyzing_state_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "جاري تحليل الموقع...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "الفحص المنهجي لبنية HTML والمقالات دون RSS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5 Required Analysis Phases
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalysisStepRow(
                    label = "الاتصال بالموقع",
                    status = if (state.stepConnectionOk) StepStatus.COMPLETED else StepStatus.ACTIVE
                )
                AnalysisStepRow(
                    label = "تحليل الصفحة",
                    status = when {
                        state.stepAnalysisOk -> StepStatus.COMPLETED
                        state.stepConnectionOk -> StepStatus.ACTIVE
                        else -> StepStatus.PENDING
                    }
                )
                AnalysisStepRow(
                    label = "اكتشاف المقالات",
                    status = when {
                        state.stepDiscoveryDone -> StepStatus.COMPLETED
                        state.stepAnalysisOk -> StepStatus.ACTIVE
                        else -> StepStatus.PENDING
                    }
                )
                AnalysisStepRow(
                    label = "استخراج المحتوى",
                    status = if (state.stepContentDone) StepStatus.COMPLETED else StepStatus.PENDING
                )
                AnalysisStepRow(
                    label = "تنزيل الصور",
                    status = if (state.stepImagesDone) StepStatus.COMPLETED else StepStatus.PENDING
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dynamic progress & articles found count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "نسبة التقدم: ${state.progressPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (state.totalArticlesCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ColorSuccess.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "تم العثور على ${state.totalArticlesCount} مقالًا",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorSuccess,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إلغاء العملية")
            }
        }
    }
}

enum class StepStatus { PENDING, ACTIVE, COMPLETED }

@Composable
fun AnalysisStepRow(label: String, status: StepStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            StepStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "تم",
                    tint = ColorSuccess,
                    modifier = Modifier.size(20.dp)
                )
            }
            StepStatus.ACTIVE -> {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = "جاري",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            StepStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Default.RadioButtonUnchecked,
                    contentDescription = "في الانتظار",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (status == StepStatus.ACTIVE) FontWeight.Bold else FontWeight.Normal,
            color = when (status) {
                StepStatus.COMPLETED -> MaterialTheme.colorScheme.onSurface
                StepStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                StepStatus.PENDING -> MaterialTheme.colorScheme.outline
            }
        )
    }
}

@Composable
fun ExtractingStateView(
    state: LiveExtractionUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("extracting_state_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = if (state.stage == ExtractionStage.PAUSED) "الاستخراج متوقف مؤقتاً" else "جاري استخراج المقالات",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "المستخرج: ${state.currentArticleIndex} / ${state.totalArticlesCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        text = "${state.progressPercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Linear Progress Bar
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Active Article Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "المقال الحالي:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.currentArticleTitle.ifBlank { "جاري تحميل تفاصيل المقال..." },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // عدد الصور
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "${state.currentArticleImagesCount} صور",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // عدد مقال/دقيقة
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "${state.articlesPerMinute} مقال/دقيقة",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // الوقت المتبقي
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = state.remainingTimeText.ifBlank { "لحظات" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: "إيقاف مؤقت" / "استئناف" — "إلغاء"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.stage == ExtractionStage.PAUSED) {
                    Button(
                        onClick = onResume,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("btn_resume_extraction")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استئناف")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onPause,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("btn_pause_extraction")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إيقاف مؤقت")
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("btn_cancel_extraction")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إلغاء")
                }
            }
        }
    }
}

@Composable
fun SuccessStateView(
    state: LiveExtractionUiState,
    onViewArticles: () -> Unit,
    onRetryFailed: () -> Unit,
    onSaveAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("success_state_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ColorSuccess.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ColorSuccess,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "اكتمل الاستخراج",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ColorSuccess
            )
            Text(
                text = "تم استخلاص المحتوى والوسائط بنجاح وتجهيزها للحفظ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Metrics Grid:
            // - {عدد المقالات} مقال
            // - {عدد الصور} صورة
            // - {عدد الناجح} ناجح
            // - {عدد الفاشل} يحتاج إلى إعادة المحاولة
            // - {حجم البيانات}
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBadge(
                        title = "المقالات",
                        value = "${state.currentSessionArticles.size} مقال",
                        icon = Icons.Default.Article,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBadge(
                        title = "الصور",
                        value = "${state.totalExtractedImages} صورة",
                        icon = Icons.Default.PhotoLibrary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBadge(
                        title = "الناجح",
                        value = "${state.successCount} ناجح",
                        icon = Icons.Default.CheckCircle,
                        tint = ColorSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBadge(
                        title = "الفاشل",
                        value = "${state.failedCount} يحتاج إعادة",
                        icon = Icons.Default.Warning,
                        tint = if (state.failedCount > 0) ColorError else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                }

                MetricBadge(
                    title = "حجم البيانات",
                    value = formatStorageSize(state.totalDataSizeKb),
                    icon = Icons.Default.Storage,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // If some images failed to download
            if (state.totalExtractedImages > state.successfulImagesCount) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "تعذر تنزيل الصورة مع ${state.successfulImagesCount} / ${state.totalExtractedImages}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorWarning
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: "عرض المقالات" — "إعادة محاولة الفاشلة" — "حفظ الكل"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveAll,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_save_all")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ الكل في المكتبة")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewArticles,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("btn_view_articles")
                    ) {
                        Text("عرض المقالات")
                    }

                    if (state.failedCount > 0) {
                        FilledTonalButton(
                            onClick = onRetryFailed,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("btn_retry_failed_all")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة محاولة الفاشلة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBadge(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InternetLostStateView(
    state: LiveExtractionUiState,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("internet_lost_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ColorError.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = ColorError,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "انقطع الاتصال بالإنترنت",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ColorError
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Dynamic preserved elements text
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "تم حفظ ${state.savedItemsSoFar} عنصرًا حتى الآن.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Text(
                text = "يمكنك استئناف العملية تلقائياً بمجرد عودة الاتصال دون فقدان ما تم استخراجه.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Buttons: "استئناف" — "إلغاء"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("btn_resume_internet")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استئناف")
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("إلغاء")
                }
            }
        }
    }
}

@Composable
fun StorageLowStateView(
    state: LiveExtractionUiState,
    onContinueWithoutImages: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("storage_low_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ColorWarning.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = ColorWarning,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "مساحة التخزين منخفضة",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ColorWarning
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Storage metrics:
            // - {المساحة المتبقية}
            // - {الحجم المطلوب}
            // - {عدد المقالات}
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("المساحة المتبقية:", style = MaterialTheme.typography.bodyMedium)
                        Text("${state.availableStorageMb} ميجابايت", fontWeight = FontWeight.Bold, color = ColorError)
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("الحجم المطلوب المتوقع:", style = MaterialTheme.typography.bodyMedium)
                        Text("${state.requiredStorageMb} ميجابايت", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("عدد المقالات المعلقة:", style = MaterialTheme.typography.bodyMedium)
                        Text("${state.pendingArticlesInStorageAlert} مقالاً", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Buttons: "إدارة التخزين" — "المتابعة بدون الصور" — "إلغاء"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onContinueWithoutImages,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_continue_no_images")
                ) {
                    Icon(Icons.Default.ImageNotSupported, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("المتابعة بدون الصور")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { /* Manage storage */ },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("إدارة التخزين")
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        }
    }
}

@Composable
fun NoArticlesErrorView(
    state: LiveExtractionUiState,
    onNewExtraction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("no_articles_error_card")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ColorWarning.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = ColorWarning,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "لم يتم العثور على مقالات قابلة للاستخراج في هذه الصفحة.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "تم فحص ${state.scannedPagesCount} صفحة بدون نتائج صالحة.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onNewExtraction,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تجربة رابط موقع آخر")
            }
        }
    }
}

@Composable
fun ConnectionErrorView(
    state: LiveExtractionUiState,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("connection_error_card")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ColorError.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = ColorError,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "تعذر الوصول إلى الموقع",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ColorError
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = state.errorMessage.ifBlank { "تحقق من اتصال الإنترنت وحاول مرة أخرى." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إعادة المحاولة")
            }
        }
    }
}

@Composable
fun LiveArticleItemCard(
    article: ExtractedArticle,
    onRetrySingle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFailed = article.status == ArticleStatus.FAILED
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isFailed) ColorError.copy(alpha = 0.15f) else ColorSuccess.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isFailed) "فشل" else "ناجح",
                        color = if (isFailed) ColorError else ColorSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details / error
            if (isFailed) {
                Text(
                    text = "${article.errorMessage ?: "خطأ في استخراج المقال"} (المحاولات: ${article.retryCount} من ${article.maxRetries})",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorError
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRetrySingle,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp).testTag("btn_retry_single_${article.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إعادة المحاولة", fontSize = 12.sp)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${article.successfulImagesCount} صور",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formatStorageSize(article.dataSizeKb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = article.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
