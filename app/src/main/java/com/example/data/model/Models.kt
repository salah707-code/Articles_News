package com.example.data.model

enum class ArticleStatus {
    SUCCESS,
    FAILED,
    PARTIAL,
    EXTRACTING
}

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
    val totalImagesCount: Int = 0
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
