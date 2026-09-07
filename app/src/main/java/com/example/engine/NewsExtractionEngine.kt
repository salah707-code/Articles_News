package com.example.engine

import com.example.data.model.ArticleStatus
import com.example.data.model.ExtractedArticle
import com.example.data.model.ExtractedMediaImage
import com.example.data.model.ExtractionStage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class LiveExtractionUiState(
    val stage: ExtractionStage = ExtractionStage.IDLE,
    val targetUrl: String = "",
    val siteName: String = "",
    val progressPercent: Int = 0,
    val currentArticleIndex: Int = 0,
    val totalArticlesCount: Int = 0,
    val currentArticleTitle: String = "",
    val currentArticleImagesCount: Int = 0,
    val articlesPerMinute: Int = 0,
    val remainingTimeText: String = "",
    val totalDataSizeKb: Long = 0,
    // Phase steps in analysis
    val stepConnectionOk: Boolean = false,
    val stepAnalysisOk: Boolean = false,
    val stepDiscoveryActive: Boolean = false,
    val stepDiscoveryDone: Boolean = false,
    val stepContentDone: Boolean = false,
    val stepImagesDone: Boolean = false,
    // Extraction metrics
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val totalExtractedImages: Int = 0,
    val successfulImagesCount: Int = 0,
    // Error / Alert details
    val errorMessage: String = "",
    val errorReasonDynamic: String = "",
    val currentRetryAttempt: Int = 1,
    val maxRetryAttempts: Int = 3,
    val scannedPagesCount: Int = 1,
    // Internet lost state
    val savedItemsSoFar: Int = 0,
    val connectionRestoredMessage: String? = null,
    // Low storage state
    val availableStorageMb: Int = 45,
    val requiredStorageMb: Int = 220,
    val pendingArticlesInStorageAlert: Int = 18,
    // Articles extracted in current session
    val currentSessionArticles: List<ExtractedArticle> = emptyList(),
    val isRunningInBackground: Boolean = false,
    val skipImagesDueToStorage: Boolean = false
)

class NewsExtractionEngine(private val externalScope: CoroutineScope) {

    private val _uiState = MutableStateFlow(LiveExtractionUiState())
    val uiState: StateFlow<LiveExtractionUiState> = _uiState.asStateFlow()

    private var extractionJob: Job? = null
    private var isPaused = false
    private var startTimeMillis = 0L

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun startExtraction(url: String, simulateScenario: String? = null) {
        extractionJob?.cancel()
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        val domain = extractDomain(formattedUrl)

        _uiState.value = LiveExtractionUiState(
            stage = ExtractionStage.ANALYZING,
            targetUrl = formattedUrl,
            siteName = domain,
            progressPercent = 5,
            currentRetryAttempt = 1,
            maxRetryAttempts = 3,
            stepConnectionOk = false,
            stepAnalysisOk = false,
            stepDiscoveryActive = true
        )

        startTimeMillis = System.currentTimeMillis()
        isPaused = false

        extractionJob = externalScope.launch(Dispatchers.Default) {
            executeExtractionWorkflow(formattedUrl, domain, simulateScenario)
        }
    }

    private suspend fun executeExtractionWorkflow(
        url: String,
        domain: String,
        simulateScenario: String?
    ) {
        try {
            // Check for immediate scenario overrides
            when (simulateScenario) {
                "ERROR_CONNECTION" -> {
                    delay(800)
                    _uiState.value = _uiState.value.copy(
                        stage = ExtractionStage.ERROR_CONNECTION,
                        errorMessage = "تحقق من اتصال الإنترنت وحاول مرة أخرى."
                    )
                    return
                }
                "ERROR_NO_ARTICLES" -> {
                    delay(1200)
                    _uiState.value = _uiState.value.copy(
                        stage = ExtractionStage.ERROR_NO_ARTICLES,
                        scannedPagesCount = 14,
                        errorMessage = "لم يتم العثور على مقالات قابلة للاستخراج في هذه الصفحة."
                    )
                    return
                }
            }

            // Phase 1: الاتصال بالموقع (Connecting)
            delay(700)
            _uiState.value = _uiState.value.copy(
                stepConnectionOk = true,
                progressPercent = 15
            )

            // Phase 2: تحليل الصفحة (Analyzing page structure)
            delay(800)
            _uiState.value = _uiState.value.copy(
                stepAnalysisOk = true,
                progressPercent = 30
            )

            // Phase 3: اكتشاف المقالات (Discovering articles)
            delay(900)
            val articlesFoundCount = if (simulateScenario == "STORAGE_LOW") 18 else (8 + Random.nextInt(7))
            _uiState.value = _uiState.value.copy(
                stepDiscoveryActive = false,
                stepDiscoveryDone = true,
                totalArticlesCount = articlesFoundCount,
                progressPercent = 45
            )

            // Brief pause to show "تم العثور على {عدد المقالات} مقالاً"
            delay(1000)

            // Check if scenario is low storage before extracting
            if (simulateScenario == "STORAGE_LOW") {
                _uiState.value = _uiState.value.copy(
                    stage = ExtractionStage.STORAGE_LOW,
                    availableStorageMb = 38,
                    requiredStorageMb = 210,
                    pendingArticlesInStorageAlert = articlesFoundCount
                )
                return
            }

            // Phase 4 & 5: استخراج المقالات وتنزيل الصور
            _uiState.value = _uiState.value.copy(
                stage = ExtractionStage.EXTRACTING,
                stepContentDone = true,
                stepImagesDone = true
            )

            val sampleTitles = getDomainArticles(domain)
            val collectedArticles = mutableListOf<ExtractedArticle>()
            var totalDataSize: Long = 0
            var totalExtractedImages = 0
            var successImages = 0

            for (i in 1..articlesFoundCount) {
                while (isPaused) {
                    delay(300)
                }

                val titleIndex = (i - 1) % sampleTitles.size
                val currentTitle = sampleTitles[titleIndex]
                val articleImages = 2 + Random.nextInt(4)
                totalExtractedImages += articleImages

                // Check for simulate internet lost at 40%
                if (simulateScenario == "INTERNET_LOST" && i == (articlesFoundCount / 2)) {
                    _uiState.value = _uiState.value.copy(
                        stage = ExtractionStage.INTERNET_LOST,
                        savedItemsSoFar = collectedArticles.size,
                        errorMessage = "انقطع الاتصال بالإنترنت"
                    )
                    return
                }

                // Simulate one article failure if scenario or randomly for realism
                val isArticleFailed = (simulateScenario == "ARTICLE_FAIL" && i == 2) || (i == 4 && articlesFoundCount > 6 && simulateScenario == null)
                val articleSize = (140 + Random.nextInt(280)).toLong()
                totalDataSize += articleSize

                val elapsedSec = ((System.currentTimeMillis() - startTimeMillis) / 1000).coerceAtLeast(1)
                val speed = ((i * 60) / elapsedSec).toInt().coerceAtLeast(12)
                val remainingItems = articlesFoundCount - i
                val remainingSec = if (speed > 0) (remainingItems * 60) / speed else 20
                val remainingText = formatRemainingTime(remainingSec)

                val articleStatus = if (isArticleFailed) ArticleStatus.FAILED else ArticleStatus.SUCCESS
                val articleError = if (isArticleFailed) "تعذر استخراج المحتوى: حماية الدفع أو تشفير البنية" else null
                val successfulImgs = if (isArticleFailed) 0 else articleImages
                successImages += successfulImgs

                val article = ExtractedArticle(
                    id = System.currentTimeMillis() + i,
                    title = currentTitle,
                    summary = "مستخلص إخباري شامل تم استخراجه وتحليله من المصدر $domain مع معالجة الصور والنصوص.",
                    content = generateFullArticleBody(currentTitle, domain),
                    sourceName = domain,
                    sourceUrl = url,
                    publishedAt = "اليوم، ${10 + (i % 12)}:${(i * 7) % 60}",
                    category = getCategoryForIndex(i),
                    imageUrls = generateImageUrls(i),
                    status = articleStatus,
                    errorMessage = articleError,
                    retryCount = if (isArticleFailed) 1 else 0,
                    dataSizeKb = articleSize,
                    successfulImagesCount = successfulImgs,
                    totalImagesCount = articleImages
                )
                collectedArticles.add(article)

                val progress = 45 + ((i.toFloat() / articlesFoundCount) * 55).toInt()

                _uiState.value = _uiState.value.copy(
                    currentArticleIndex = i,
                    currentArticleTitle = currentTitle,
                    currentArticleImagesCount = articleImages,
                    articlesPerMinute = speed,
                    remainingTimeText = remainingText,
                    totalDataSizeKb = totalDataSize,
                    progressPercent = progress.coerceAtMost(100),
                    successCount = collectedArticles.count { it.status == ArticleStatus.SUCCESS },
                    failedCount = collectedArticles.count { it.status == ArticleStatus.FAILED },
                    totalExtractedImages = totalExtractedImages,
                    successfulImagesCount = successImages,
                    currentSessionArticles = collectedArticles.toList()
                )

                delay(950)
            }

            // Completed!
            _uiState.value = _uiState.value.copy(
                stage = ExtractionStage.SUCCESS,
                progressPercent = 100,
                remainingTimeText = "اكتمل بنجاح"
            )

        } catch (e: CancellationException) {
            // Cancelled
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                stage = ExtractionStage.ERROR_CONNECTION,
                errorMessage = "تعذر استخراج المحتوى: ${e.localizedMessage ?: "خطأ غير متوقع"}"
            )
        }
    }

    fun pauseExtraction() {
        if (_uiState.value.stage == ExtractionStage.EXTRACTING) {
            isPaused = true
            _uiState.value = _uiState.value.copy(stage = ExtractionStage.PAUSED)
        }
    }

    fun resumeExtraction() {
        if (_uiState.value.stage == ExtractionStage.PAUSED) {
            isPaused = false
            _uiState.value = _uiState.value.copy(stage = ExtractionStage.EXTRACTING)
        } else if (_uiState.value.stage == ExtractionStage.INTERNET_LOST) {
            // Reconnected!
            val progress = _uiState.value.progressPercent
            _uiState.value = _uiState.value.copy(
                stage = ExtractionStage.EXTRACTING,
                connectionRestoredMessage = "تم استعادة الاتصال. تمت استعادة العملية عند $progress%."
            )
            // Continue workflow
            isPaused = false
            extractionJob = externalScope.launch(Dispatchers.Default) {
                continueWorkflowAfterReconnect()
            }
        }
    }

    private suspend fun continueWorkflowAfterReconnect() {
        val current = _uiState.value
        val remaining = (current.totalArticlesCount - current.currentArticleIndex).coerceAtLeast(1)
        val collected = current.currentSessionArticles.toMutableList()
        var currentIdx = current.currentArticleIndex
        var dataSize = current.totalDataSizeKb
        var totalImgs = current.totalExtractedImages
        var succImgs = current.successfulImagesCount

        for (i in 1..remaining) {
            currentIdx++
            delay(900)
            val title = "مقال مسترجع: تحديث استراتيجي مستمر في الشرق الأوسط والعالم"
            val imgs = 3
            val size = 180L
            dataSize += size
            totalImgs += imgs
            succImgs += imgs

            collected.add(
                ExtractedArticle(
                    id = System.currentTimeMillis() + currentIdx,
                    title = title,
                    summary = "ملخص مقال مسترجع بعد استعادة الاتصال بنجاح وتوثيق المحتوى.",
                    content = generateFullArticleBody(title, current.siteName),
                    sourceName = current.siteName,
                    sourceUrl = current.targetUrl,
                    publishedAt = "الآن",
                    category = "عام",
                    imageUrls = generateImageUrls(currentIdx),
                    status = ArticleStatus.SUCCESS,
                    dataSizeKb = size,
                    successfulImagesCount = imgs,
                    totalImagesCount = imgs
                )
            )

            val progress = 50 + ((currentIdx.toFloat() / current.totalArticlesCount) * 50).toInt()
            _uiState.value = _uiState.value.copy(
                currentArticleIndex = currentIdx,
                currentArticleTitle = title,
                progressPercent = progress.coerceAtMost(100),
                totalDataSizeKb = dataSize,
                totalExtractedImages = totalImgs,
                successfulImagesCount = succImgs,
                successCount = collected.count { it.status == ArticleStatus.SUCCESS },
                failedCount = collected.count { it.status == ArticleStatus.FAILED },
                currentSessionArticles = collected.toList()
            )
        }

        _uiState.value = _uiState.value.copy(
            stage = ExtractionStage.SUCCESS,
            progressPercent = 100,
            remainingTimeText = "اكتمل بنجاح"
        )
    }

    fun continueWithoutImages() {
        // From Low Storage alert
        _uiState.value = _uiState.value.copy(
            skipImagesDueToStorage = true,
            stage = ExtractionStage.EXTRACTING
        )
        extractionJob = externalScope.launch(Dispatchers.Default) {
            continueWorkflowAfterReconnect()
        }
    }

    fun cancelExtraction() {
        extractionJob?.cancel()
        _uiState.value = _uiState.value.copy(
            stage = ExtractionStage.IDLE,
            progressPercent = 0
        )
    }

    fun retryFailedArticles() {
        val current = _uiState.value
        val updated = current.currentSessionArticles.map { article ->
            if (article.status == ArticleStatus.FAILED) {
                article.copy(
                    status = ArticleStatus.SUCCESS,
                    errorMessage = null,
                    retryCount = article.retryCount + 1,
                    successfulImagesCount = article.totalImagesCount
                )
            } else article
        }

        val succImgs = updated.sumOf { it.successfulImagesCount }
        _uiState.value = current.copy(
            currentSessionArticles = updated,
            successCount = updated.count { it.status == ArticleStatus.SUCCESS },
            failedCount = updated.count { it.status == ArticleStatus.FAILED },
            successfulImagesCount = succImgs
        )
    }

    fun retrySingleArticle(articleId: Long) {
        val current = _uiState.value
        val updated = current.currentSessionArticles.map { article ->
            if (article.id == articleId) {
                article.copy(
                    status = ArticleStatus.SUCCESS,
                    errorMessage = null,
                    retryCount = article.retryCount + 1,
                    successfulImagesCount = article.totalImagesCount
                )
            } else article
        }
        val succImgs = updated.sumOf { it.successfulImagesCount }
        _uiState.value = current.copy(
            currentSessionArticles = updated,
            successCount = updated.count { it.status == ArticleStatus.SUCCESS },
            failedCount = updated.count { it.status == ArticleStatus.FAILED },
            successfulImagesCount = succImgs
        )
    }

    fun resetToIdle() {
        extractionJob?.cancel()
        _uiState.value = LiveExtractionUiState()
    }

    private fun extractDomain(url: String): String {
        return try {
            val clean = url.replace("https://", "").replace("http://", "").split("/")[0]
            if (clean.isNotBlank()) clean else "aljazeera.net"
        } catch (e: Exception) {
            "news-source.com"
        }
    }

    private fun formatRemainingTime(seconds: Int): String {
        return when {
            seconds <= 0 -> "لحظات معدودة"
            seconds < 60 -> "$seconds ثانية"
            seconds == 60 -> "دقيقة واحدة"
            else -> {
                val mins = seconds / 60
                val secs = seconds % 60
                "$mins دقيقة و $secs ثانية"
            }
        }
    }

    private fun getDomainArticles(domain: String): List<String> {
        return listOf(
            "قمة الذكاء الاصطناعي الدولية تطلق معايير جديدة لسلامة النماذج التوليدية",
            "تحولات الطاقة المتجددة: استثمارات عالمية قياسية في مشروعات الطاقة الشمسية",
            "استكشاف الفضاء: مسبار استكشافي يرسل أحدث خرائط جيولوجية لسطح المريخ",
            "تطورات الأسواق المالية ومؤشرات التضخم العالمية للربع السنوي الحالي",
            "انطلاق مؤتمر التكنولوجيا العربي بمشاركة 500 شركة ناشئة ومبتكرة",
            "دراسة علمية حديثة تكشف آليات تعزيز الذاكرة والتركيز الذهني بالرياضة",
            "ثورة في الحوسبة الكمية: حاسوب خارق يحل معادلات معقدة في دقائق",
            "مشاريع البنية التحتية الذكية في المدن العربية تسارع خطط التحول الرقمي",
            "تقرير المناخ السنوي يدعو لتكثيف تدابير الاستدامة وحماية التنوع البيئي",
            "الابتكار في الطب الرقمي: جراحات روبوتية متطورة تحقق نسب نجاح غير مسبوقة"
        )
    }

    private fun getCategoryForIndex(idx: Int): String {
        val categories = listOf("تكنولوجيا", "اقتصاد", "علوم", "سياسة", "صحة", "ثقافة", "رياضة")
        return categories[idx % categories.size]
    }

    private fun generateImageUrls(seed: Int): List<String> {
        // High quality curated unsplash image URLs with news & tech themes
        val sampleImages = listOf(
            "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&q=80",
            "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=800&q=80",
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&q=80",
            "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&q=80",
            "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&q=80",
            "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?w=800&q=80"
        )
        return sampleImages.shuffled().take(2 + (seed % 3))
    }

    private fun generateFullArticleBody(title: String, domain: String): String {
        return """
            في تقرير تفصيلي ورد من $domain، تم تناول أبعاد "$title" بعمق وتحليل منهجي.
            
            أكد الخبراء والمتخصصون أن هذه التطورات تشكل نقطة تحول استراتيجية في المشهد الإقليمي والدولي. وقد أسفرت الدراسات الميدانية عن رصد نمو ملحوظ في معدلات التفاعل والاهتمام العام بالموضوع.
            
            وأشار المتحدثون إلى أهمية تضافر الجهود لضمان الاستفادة القصوى من الفرص المتاحة، مع ضرورة مواجهة التحديات التنظيمية والتقنية بحلول عملية ومبتكرة.
            
            ختاماً، تبقى التوقعات إيجابية للمرحلة المقبلة في ظل الخطوات الإيجابية المتسارعة التي تشهدها القطاعات ذات الصلة.
        """.trimIndent()
    }
}
