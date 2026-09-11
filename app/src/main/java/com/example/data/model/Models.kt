package com.example.data.model

enum class ArticleStatus {
    SUCCESS,
    FAILED,
    PARTIAL,
    EXTRACTING
}

enum class DateSource(val displayName: String) {
    API("خلاصة API"),
    RSS("تغذية RSS"),
    HTML("وسوم HTML"),
    METADATA("بيانات معيارية"),
    UNKNOWN("تاريخ غير محدد")
}

fun DateSource.ifUnknown(default: DateSource): DateSource = if (this == DateSource.UNKNOWN) default else this

enum class ArticleFreshness(val label: String) {
    LIVE("مباشر"),       // تازه ومحدث مباشرة من المصدر
    CACHED("محفوظ محلياً"),     // مسترجع من قاعدة البيانات المحلية المؤقتة
    OFFLINE("بدون اتصال")     // وضع عدم الاتصال بالإنترنت
}

data class ParsedDateResult(
    val epochMillis: Long,
    val formattedArabic: String,
    val dateSource: DateSource,
    val isValid: Boolean
)

data class ExtractedArticle(
    val id: Long = 0,
    val title: String,
    val summary: String,
    val content: String,
    val sourceName: String,
    val sourceUrl: String,
    val publishedAt: String,
    val category: String,
    val imageUrls: List<String> = emptyList(),
    val status: ArticleStatus = ArticleStatus.SUCCESS,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val dataSizeKb: Long = 0,
    val successfulImagesCount: Int = 0,
    val totalImagesCount: Int = 0,
    // Deduplication & Timeline Audit Fields
    val deduplicationKey: String = "",
    val canonicalUrl: String = sourceUrl,
    val normalizedUrl: String = "",
    val publishedAtEpoch: Long = 0L,
    val fetchedAtEpoch: Long = 0L,
    val createdAtEpoch: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val dateSource: DateSource = DateSource.UNKNOWN,
    val isNew: Boolean = false,
    val freshness: ArticleFreshness = ArticleFreshness.LIVE,
    val contentHash: String = "",
    val isSavedOffline: Boolean = false
)

data class CustomNewsSource(
    val id: Long = 0,
    val name: String,
    val url: String,
    val category: String = "عام",
    val isEnabled: Boolean = true,
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserSettings(
    val id: Int = 1,
    val preferredCategories: List<String> = listOf("تكنولوجيا", "سياسة", "اقتصاد", "رياضة", "صحة", "ثقافة"),
    val notificationsEnabled: Boolean = true,
    val notificationFrequencyMinutes: Long = 60L,
    val autoSyncEnabled: Boolean = true,
    val autoSaveOffline: Boolean = false
)

data class ExtractedMediaImage(
    val id: String,
    val imageUrl: String,
    val articleId: Long,
    val articleTitle: String,
    val sourceName: String,
    val sizeKb: Long,
    val isDownloaded: Boolean = true
)

enum class ExtractionStage {
    IDLE,
    ANALYZING,          // مراحل: الاتصال، تحليل الصفحة، اكتشاف المقالات
    EXTRACTING,         // استخراج المقالات والصور
    PAUSED,             // إيقاف مؤقت
    SUCCESS,            // اكتمل الاستخراج
    INTERNET_LOST,      // انقطاع الإنترنت
    STORAGE_LOW,        // مساحة التخزين منخفضة
    ERROR_NO_ARTICLES,  // لم يتم العثور على مقالات
    ERROR_CONNECTION    // تعذر الوصول إلى الموقع
}

data class ExtractionStats(
    val totalSources: Int = 0,
    val totalArticles: Int = 0,
    val totalImages: Int = 0,
    val totalStorageKb: Long = 0
)
