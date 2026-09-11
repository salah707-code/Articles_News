package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.RecentExtractionEntity
import com.example.data.model.ArticleFreshness
import com.example.data.model.ArticleStatus
import com.example.data.model.DateSource
import com.example.data.model.ExtractedArticle
import com.example.data.model.ExtractedMediaImage
import com.example.data.model.ExtractionStage
import com.example.data.model.ExtractionStats
import com.example.data.repository.ArticleRepository
import com.example.data.util.DateParserAndValidator
import com.example.data.util.DeduplicationHelper
import com.example.engine.LiveExtractionUiState
import com.example.engine.NewsExtractionEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppNavTab {
    HOME,
    EXTRACTION,
    LIBRARY,
    SOURCES_SETTINGS,
    GALLERY
}

data class FilterState(
    val searchQuery: String = "",
    val selectedSource: String? = null,
    val selectedCategory: String? = null,
    val selectedStatus: ArticleStatus? = null,
    val selectedTimeRange: String = "ALL", // ALL, TODAY, WEEK
    val onlyOfflineSaved: Boolean = false
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ArticleRepository
    val extractionEngine: NewsExtractionEngine

    val extractionState: StateFlow<LiveExtractionUiState>

    private val _currentTab = MutableStateFlow(AppNavTab.HOME)
    val currentTab: StateFlow<AppNavTab> = _currentTab.asStateFlow()

    private val _isDarkTheme = MutableStateFlow<Boolean?>(null) // null = system default
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _selectedArticle = MutableStateFlow<ExtractedArticle?>(null)
    val selectedArticle: StateFlow<ExtractedArticle?> = _selectedArticle.asStateFlow()

    private val _selectedImage = MutableStateFlow<ExtractedMediaImage?>(null)
    val selectedImage: StateFlow<ExtractedMediaImage?> = _selectedImage.asStateFlow()

    private val _manualFreshness = MutableStateFlow<ArticleFreshness?>(null)

    val freshnessState: StateFlow<ArticleFreshness>

    val allArticles: StateFlow<List<ExtractedArticle>>
    val offlineArticles: StateFlow<List<ExtractedArticle>>
    val customSources: StateFlow<List<com.example.data.model.CustomNewsSource>>
    val userSettings: StateFlow<com.example.data.model.UserSettings>
    val stats: StateFlow<ExtractionStats>
    val recentExtractions: StateFlow<List<RecentExtractionEntity>>

    val filteredArticles: StateFlow<List<ExtractedArticle>>
    val allGalleryImages: StateFlow<List<ExtractedMediaImage>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ArticleRepository(db)
        extractionEngine = NewsExtractionEngine(viewModelScope)
        extractionState = extractionEngine.uiState

        freshnessState = combine(
            extractionState,
            _currentTab,
            _manualFreshness
        ) { extraction, tab, manual ->
            when {
                manual != null -> manual
                extraction.stage == ExtractionStage.INTERNET_LOST -> ArticleFreshness.OFFLINE
                extraction.stage == ExtractionStage.EXTRACTING -> ArticleFreshness.LIVE
                extraction.stage == ExtractionStage.SUCCESS && tab == AppNavTab.EXTRACTION -> ArticleFreshness.LIVE
                else -> ArticleFreshness.CACHED
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArticleFreshness.CACHED)

        allArticles = repository.allArticles.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        offlineArticles = repository.offlineArticles.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        customSources = repository.customSources.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        userSettings = repository.userSettings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.example.data.model.UserSettings()
        )

        stats = repository.stats.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ExtractionStats()
        )

        recentExtractions = repository.recentExtractions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        filteredArticles = combine(allArticles, _filterState) { articles, filter ->
            articles.filter { article ->
                val matchesQuery = filter.searchQuery.isBlank() ||
                        article.title.contains(filter.searchQuery, ignoreCase = true) ||
                        article.summary.contains(filter.searchQuery, ignoreCase = true) ||
                        article.sourceName.contains(filter.searchQuery, ignoreCase = true)

                val matchesSource = filter.selectedSource == null || article.sourceName == filter.selectedSource
                val matchesCategory = filter.selectedCategory == null || article.category == filter.selectedCategory
                val matchesStatus = filter.selectedStatus == null || article.status == filter.selectedStatus
                val matchesOffline = !filter.onlyOfflineSaved || article.isSavedOffline

                matchesQuery && matchesSource && matchesCategory && matchesStatus && matchesOffline
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allGalleryImages = allArticles.map { articles ->
            articles.flatMap { article ->
                article.imageUrls.mapIndexed { idx, url ->
                    ExtractedMediaImage(
                        id = "${article.id}_$idx",
                        imageUrl = url,
                        articleId = article.id,
                        articleTitle = article.title,
                        sourceName = article.sourceName,
                        sizeKb = 120 + (idx * 45L),
                        isDownloaded = article.status == ArticleStatus.SUCCESS
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        seedInitialData()
    }

    fun setTab(tab: AppNavTab) {
        _currentTab.value = tab
    }

    fun toggleTheme() {
        _isDarkTheme.value = !(_isDarkTheme.value ?: false)
    }

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun updateSourceFilter(source: String?) {
        _filterState.value = _filterState.value.copy(selectedSource = source)
    }

    fun updateCategoryFilter(category: String?) {
        _filterState.value = _filterState.value.copy(selectedCategory = category)
    }

    fun updateStatusFilter(status: ArticleStatus?) {
        _filterState.value = _filterState.value.copy(selectedStatus = status)
    }

    fun toggleOfflineFilter(onlySaved: Boolean) {
        _filterState.value = _filterState.value.copy(onlyOfflineSaved = onlySaved)
    }

    fun clearFilters() {
        _filterState.value = FilterState()
    }

    fun toggleArticleOffline(article: ExtractedArticle) {
        viewModelScope.launch {
            val newStatus = !article.isSavedOffline
            repository.toggleArticleOfflineSaved(article.id, newStatus)
            if (_selectedArticle.value?.id == article.id) {
                _selectedArticle.value = _selectedArticle.value?.copy(isSavedOffline = newStatus)
            }
        }
    }

    fun toggleOfflineStatus(articleId: Long, isSaved: Boolean) {
        viewModelScope.launch {
            repository.toggleArticleOfflineSaved(articleId, isSaved)
            if (_selectedArticle.value?.id == articleId) {
                _selectedArticle.value = _selectedArticle.value?.copy(isSavedOffline = isSaved)
            }
        }
    }

    fun addCustomSource(name: String, url: String, category: String) {
        viewModelScope.launch {
            repository.addCustomSource(name, url, category)
        }
    }

    fun toggleSource(source: com.example.data.model.CustomNewsSource, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleSourceEnabled(source, isEnabled)
        }
    }

    fun toggleSourceEnabled(source: com.example.data.model.CustomNewsSource, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleSourceEnabled(source, isEnabled)
        }
    }

    fun deleteSource(sourceId: Long) {
        viewModelScope.launch {
            repository.deleteCustomSource(sourceId)
        }
    }

    fun deleteCustomSource(sourceId: Long) {
        deleteSource(sourceId)
    }

    fun updateUserSettings(newSettings: com.example.data.model.UserSettings) {
        viewModelScope.launch {
            repository.saveUserSettings(newSettings)
            if (newSettings.notificationsEnabled) {
                com.example.worker.NewsSyncWorker.schedulePeriodicSync(
                    getApplication(),
                    newSettings.notificationFrequencyMinutes.toLong()
                )
            }
        }
    }

    fun triggerImmediateWorkerSync() {
        com.example.worker.NewsSyncWorker.triggerImmediateSync(getApplication())
    }

    fun selectArticle(article: ExtractedArticle?) {
        _selectedArticle.value = article
    }

    fun selectImage(image: ExtractedMediaImage?) {
        _selectedImage.value = image
    }

    fun startExtraction(url: String, simulateScenario: String? = null) {
        _currentTab.value = AppNavTab.EXTRACTION
        extractionEngine.startExtraction(url, simulateScenario)
    }

    fun pauseExtraction() = extractionEngine.pauseExtraction()
    fun resumeExtraction() = extractionEngine.resumeExtraction()
    fun cancelExtraction() = extractionEngine.cancelExtraction()
    fun continueWithoutImages() = extractionEngine.continueWithoutImages()
    fun retryFailedArticles() = extractionEngine.retryFailedArticles()
    fun retrySingleArticle(articleId: Long) = extractionEngine.retrySingleArticle(articleId)

    fun saveCompletedSessionToLibrary() {
        val state = extractionState.value
        val articles = state.currentSessionArticles
        if (articles.isNotEmpty()) {
            viewModelScope.launch {
                repository.insertArticles(articles)
                repository.saveRecentExtraction(
                    RecentExtractionEntity(
                        url = state.targetUrl,
                        sourceName = state.siteName,
                        articlesCount = articles.size,
                        imagesCount = state.totalExtractedImages,
                        successCount = state.successCount,
                        failedCount = state.failedCount,
                        storageKb = state.totalDataSizeKb,
                        status = "ناجح",
                        durationSeconds = 42
                    )
                )
                // Switch to Library
                _currentTab.value = AppNavTab.LIBRARY
            }
        }
    }

    fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            repository.deleteArticle(articleId)
            if (_selectedArticle.value?.id == articleId) {
                _selectedArticle.value = null
            }
        }
    }

    fun retryArticleFromLibrary(article: ExtractedArticle) {
        viewModelScope.launch {
            val updated = article.copy(
                status = ArticleStatus.SUCCESS,
                errorMessage = null,
                retryCount = article.retryCount + 1,
                successfulImagesCount = article.totalImagesCount
            )
            repository.updateArticle(updated)
            if (_selectedArticle.value?.id == article.id) {
                _selectedArticle.value = updated
            }
        }
    }

    /**
     * Executes safe, non-destructive refresh of the news library:
     * Validates ages -> preserves stored records -> marks only genuinely fresh articles as new -> updates UI
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            val current = allArticles.first()
            val now = System.currentTimeMillis()
            for (art in current) {
                val isStillNew = DateParserAndValidator.isGenuinelyNew(
                    publishedAtEpoch = art.publishedAtEpoch,
                    dateSource = art.dateSource,
                    isDuplicate = false,
                    referenceTimeMillis = now
                )
                if (art.isNew != isStillNew) {
                    repository.updateArticle(art.copy(isNew = isStillNew))
                }
            }
        }
    }

    private fun seedInitialData() {
        viewModelScope.launch {
            repository.seedDefaultSourcesIfNeeded()
            com.example.worker.NewsSyncWorker.schedulePeriodicSync(getApplication(), 60L)
            val currentArticles = allArticles.first()
            if (currentArticles.isEmpty()) {
                val now = System.currentTimeMillis()
                val sampleArticles = listOf(
                    ExtractedArticle(
                        id = 1L,
                        title = "الذكاء الاصطناعي التوليدي يحدث ثورة في غرف الأخبار والصحافة الرقمية",
                        summary = "دراسة موسعة حول تأثير أدوات التلخيص والاستخراج الآلي في تسريع تغطية الأحداث العاجلة مع الحفاظ على الدقة التحريرية.",
                        content = "شهدت الصحافة الرقمية في الآونة الأخيرة تحولاً جذرياً بفضل تقنيات معالجة اللغة الطبيعية واستخراج البيانات الذكي من صفحات الويب دون الحاجة إلى خلاصات RSS تقليدية.\n\nتتيح هذه الأنظمة للمؤسسات الصحفية والباحثين متابعة التطورات اللحظية من مئات المصادر الإخبارية المستقلة وتحليل نصوصها واستخلاص الصور والوسائط المرفقة بأعلى جودة.\n\nويؤكد الخبراء أن الجمع بين الدقة التحريرية وسرعة الخوارزميات يمنح المؤسسات ميزة تنافسية كبرى.",
                        sourceName = "aljazeera.net",
                        sourceUrl = "https://www.aljazeera.net/tech/ai-newsroom",
                        publishedAt = "منذ ساعتين",
                        category = "تكنولوجيا",
                        imageUrls = listOf(
                            "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&q=80",
                            "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&q=80"
                        ),
                        status = ArticleStatus.SUCCESS,
                        dataSizeKb = 340,
                        successfulImagesCount = 2,
                        totalImagesCount = 2,
                        deduplicationKey = DeduplicationHelper.generateDeduplicationKey("aljazeera.net", "https://www.aljazeera.net/tech/ai-newsroom", "الذكاء الاصطناعي التوليدي"),
                        canonicalUrl = "https://www.aljazeera.net/tech/ai-newsroom",
                        normalizedUrl = "https://aljazeera.net/tech/ai-newsroom",
                        publishedAtEpoch = now - (2 * 3600 * 1000L),
                        fetchedAtEpoch = now,
                        createdAtEpoch = now,
                        updatedAtEpoch = now,
                        dateSource = DateSource.HTML,
                        isNew = true,
                        contentHash = DeduplicationHelper.generateContentHash("الذكاء الاصطناعي التوليدي", "شهدت الصحافة الرقمية")
                    ),
                    ExtractedArticle(
                        id = 2L,
                        title = "اقتصادات الطاقة المتجددة: تدفقات استثمارية غير مسبوقة في مشروعات الطاقة الشمسية",
                        summary = "تقرير اقتصادي يسلط الضوء على تضاعف مشاريع الطاقة النظيفة في المنطقة العربية خلال العام الجاري.",
                        content = "أظهرت بيانات استقصائية جديدة تسارع وتيرة التحول الطاقوي في بلدان الشرق الأوسط وشمال أفريقيا، مدعومة بمشاريع عملاقة للطاقة الكهروضوئية وطاقة الرياح.\n\nوتشير التقديرات إلى انخفاض تكلفة إنتاج الكيلوواط الساعي بنسبة تجاوزت 35% مقارنة بالعقد الماضي، مما يجعل المشاريع النظيفة خياراً اقتصادياً واستراتيجياً لا غنى عنه.",
                        sourceName = "skynewsarabia.com",
                        sourceUrl = "https://www.skynewsarabia.com/business/energy-investments",
                        publishedAt = "اليوم، 11:30",
                        category = "اقتصاد",
                        imageUrls = listOf(
                            "https://images.unsplash.com/photo-1509391365360-2e959784a276?w=800&q=80",
                            "https://images.unsplash.com/photo-1497435334941-8c899ee9e8e9?w=800&q=80"
                        ),
                        status = ArticleStatus.SUCCESS,
                        dataSizeKb = 410,
                        successfulImagesCount = 2,
                        totalImagesCount = 2,
                        deduplicationKey = DeduplicationHelper.generateDeduplicationKey("skynewsarabia.com", "https://www.skynewsarabia.com/business/energy-investments", "اقتصادات الطاقة"),
                        canonicalUrl = "https://www.skynewsarabia.com/business/energy-investments",
                        normalizedUrl = "https://skynewsarabia.com/business/energy-investments",
                        publishedAtEpoch = now - (5 * 3600 * 1000L),
                        fetchedAtEpoch = now,
                        createdAtEpoch = now,
                        updatedAtEpoch = now,
                        dateSource = DateSource.API,
                        isNew = true,
                        contentHash = DeduplicationHelper.generateContentHash("اقتصادات الطاقة", "أظهرت بيانات استقصائية")
                    ),
                    ExtractedArticle(
                        id = 3L,
                        title = "تلسكوب جيمس ويب يلتقط ملامح مجرات وليدة على حافة الكون المرئي",
                        summary = "اكتشافات فلكية جديدة تعيد صياغة النظريات السائدة حول نشأة النجوم الأولى بعد الانفجار العظيم.",
                        content = "تمكن المرصد الفضائي جيمس ويب من رصد أقدم التكتلات المجرية المعروفة حتى الآن، كاشفاً عن تشكيلات نجمية غنية بالغازات البدائية.\n\nتفتح هذه الصور عالية الاستبانة آفاقاً رحبة أمام علماء الفيزياء الفلكية لدراسة التركيب الكيميائي للمادة الأولى وتاريخ التطور الكوني.",
                        sourceName = "bbc.com/arabic",
                        sourceUrl = "https://www.bbc.com/arabic/science-space",
                        publishedAt = "أمس، 18:45",
                        category = "علوم",
                        imageUrls = listOf(
                            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&q=80"
                        ),
                        status = ArticleStatus.SUCCESS,
                        dataSizeKb = 290,
                        successfulImagesCount = 1,
                        totalImagesCount = 1,
                        deduplicationKey = DeduplicationHelper.generateDeduplicationKey("bbc.com/arabic", "https://www.bbc.com/arabic/science-space", "تلسكوب جيمس ويب"),
                        canonicalUrl = "https://www.bbc.com/arabic/science-space",
                        normalizedUrl = "https://bbc.com/arabic/science-space",
                        publishedAtEpoch = now - (28 * 3600 * 1000L),
                        fetchedAtEpoch = now,
                        createdAtEpoch = now,
                        updatedAtEpoch = now,
                        dateSource = DateSource.RSS,
                        isNew = false, // Older than 24h: MUST NOT be marked as new
                        contentHash = DeduplicationHelper.generateContentHash("تلسكوب جيمس ويب", "تمكن المرصد الفضائي")
                    ),
                    ExtractedArticle(
                        id = 4L,
                        title = "تقرير استقصائي حول سلاسل الإمداد العالمية وموانئ الشحن البحري",
                        summary = "فشل في استخراج جزء من المقال بسبب قيود الوصول لجدران الدفع بالصفحة المصدرية.",
                        content = "تعذر استخراج المحتوى الكامل لهذا المقال لوجود اشتراك مسبق بالموقع المصدر.",
                        sourceName = "bloomberg-arabic.net",
                        sourceUrl = "https://bloomberg.com/news/shipping",
                        publishedAt = "منذ يومين",
                        category = "اقتصاد",
                        imageUrls = emptyList(),
                        status = ArticleStatus.FAILED,
                        errorMessage = "تعذر استخراج المحتوى: جدار اشتراك مدفوع في الموقع",
                        retryCount = 1,
                        dataSizeKb = 45,
                        successfulImagesCount = 0,
                        totalImagesCount = 2,
                        deduplicationKey = DeduplicationHelper.generateDeduplicationKey("bloomberg-arabic.net", "https://bloomberg.com/news/shipping", "تقرير استقصائي حول سلاسل"),
                        canonicalUrl = "https://bloomberg.com/news/shipping",
                        normalizedUrl = "https://bloomberg.com/news/shipping",
                        publishedAtEpoch = now - (52 * 3600 * 1000L),
                        fetchedAtEpoch = now,
                        createdAtEpoch = now,
                        updatedAtEpoch = now,
                        dateSource = DateSource.METADATA,
                        isNew = false, // Old article: MUST NOT be marked as new
                        contentHash = DeduplicationHelper.generateContentHash("تقرير استقصائي", "تعذر استخراج")
                    )
                )

                repository.insertArticles(sampleArticles)

                // Seed recent extractions
                repository.saveRecentExtraction(
                    RecentExtractionEntity(
                        url = "https://www.aljazeera.net",
                        sourceName = "aljazeera.net",
                        articlesCount = 14,
                        imagesCount = 38,
                        successCount = 13,
                        failedCount = 1,
                        storageKb = 4850,
                        status = "ناجح",
                        durationSeconds = 48,
                        timestamp = System.currentTimeMillis() - 7200000
                    )
                )
                repository.saveRecentExtraction(
                    RecentExtractionEntity(
                        url = "https://www.skynewsarabia.com",
                        sourceName = "skynewsarabia.com",
                        articlesCount = 9,
                        imagesCount = 22,
                        successCount = 9,
                        failedCount = 0,
                        storageKb = 3200,
                        status = "ناجح",
                        durationSeconds = 35,
                        timestamp = System.currentTimeMillis() - 86400000
                    )
                )
            }
        }
    }
}

fun formatStorageSize(kb: Long): String {
    return when {
        kb < 1024 -> "$kb كيلوبايت"
        kb < 1024 * 1024 -> String.format("%.1f ميجابايت", kb / 1024.0)
        else -> String.format("%.2f جيجابايت", kb / (1024.0 * 1024.0))
    }
}
