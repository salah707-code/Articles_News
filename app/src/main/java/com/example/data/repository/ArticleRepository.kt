package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.RecentExtractionEntity
import com.example.data.model.ArticleFreshness
import com.example.data.model.ArticleStatus
import com.example.data.model.DateSource
import com.example.data.model.ExtractedArticle
import com.example.data.model.ExtractionStats
import com.example.data.model.ParsedDateResult
import com.example.data.util.DateParserAndValidator
import com.example.data.util.DeduplicationHelper
import com.example.data.util.NewsAuditLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class UpsertAuditResult(
    val articleId: Long,
    val isNewRecord: Boolean,
    val isGenuinelyNew: Boolean
)

class ArticleRepository(private val database: AppDatabase) {
    private val articleDao = database.articleDao()
    private val recentExtractionDao = database.recentExtractionDao()
    private val customNewsSourceDao = database.customNewsSourceDao()
    private val userSettingsDao = database.userSettingsDao()

    val allArticles: Flow<List<ExtractedArticle>> = articleDao.getAllArticles().map { entities ->
        entities.map { it.toModel() }
    }

    val newArticles: Flow<List<ExtractedArticle>> = articleDao.getNewArticles().map { entities ->
        entities.map { it.toModel() }
    }

    val offlineArticles: Flow<List<ExtractedArticle>> = articleDao.getOfflineSavedArticles().map { entities ->
        entities.map { it.toModel() }
    }

    val customSources: Flow<List<com.example.data.model.CustomNewsSource>> = customNewsSourceDao.getAllSources().map { entities ->
        entities.map { entity ->
            com.example.data.model.CustomNewsSource(
                id = entity.id,
                name = entity.name,
                url = entity.url,
                category = entity.category,
                isEnabled = entity.isEnabled,
                isCustom = entity.isCustom,
                createdAt = entity.createdAt
            )
        }
    }

    val userSettings: Flow<com.example.data.model.UserSettings> = userSettingsDao.getUserSettings().map { entity ->
        if (entity != null) {
            com.example.data.model.UserSettings(
                id = entity.id,
                preferredCategories = entity.preferredCategories.split(",").map { it.trim() }.filter { it.isNotBlank() },
                notificationsEnabled = entity.notificationsEnabled,
                notificationFrequencyMinutes = entity.notificationFrequencyMinutes,
                autoSyncEnabled = entity.autoSyncEnabled,
                autoSaveOffline = entity.autoSaveOffline
            )
        } else {
            com.example.data.model.UserSettings()
        }
    }

    val stats: Flow<ExtractionStats> = combine(
        articleDao.getSourcesCount(),
        articleDao.getArticlesCount(),
        articleDao.getTotalImagesCount(),
        articleDao.getTotalStorageKb()
    ) { sources, articles, images, storage ->
        ExtractionStats(
            totalSources = sources,
            totalArticles = articles,
            totalImages = images,
            totalStorageKb = storage
        )
    }

    val recentExtractions: Flow<List<RecentExtractionEntity>> =
        recentExtractionDao.getRecentExtractions()

    /**
     * Executes the required pipeline for each article:
     * Fetch -> Normalize -> Validate publishedAt -> Generate stable ID
     * -> Check duplicate -> Check age -> Insert/Update -> Mark as new
     */
    suspend fun insertArticles(articles: List<ExtractedArticle>) {
        for (article in articles) {
            upsertArticleWithAudit(article)
        }
    }

    suspend fun insertArticle(article: ExtractedArticle): Long {
        return upsertArticleWithAudit(article).articleId
    }

    suspend fun upsertArticleWithAudit(article: ExtractedArticle): UpsertAuditResult {
        val normUrl = DeduplicationHelper.normalizeUrl(article.sourceUrl)
        val normTitle = DeduplicationHelper.normalizeTitle(article.title)
        val dedupKey = if (article.deduplicationKey.isNotBlank()) {
            article.deduplicationKey
        } else {
            DeduplicationHelper.generateDeduplicationKey(article.sourceName, normUrl, normTitle)
        }

        val stableId = if (article.id > 0) article.id else DeduplicationHelper.generateStableId(dedupKey)

        // Parse and validate published date
        val parsedDate = if (article.publishedAtEpoch > 0) {
            ParsedDateResult(
                epochMillis = article.publishedAtEpoch,
                formattedArabic = article.publishedAt.ifBlank { DateParserAndValidator.formatEpochToArabic(article.publishedAtEpoch) },
                dateSource = article.dateSource,
                isValid = true
            )
        } else {
            DateParserAndValidator.parseAndValidate(article.publishedAt, article.dateSource)
        }

        val contentHash = DeduplicationHelper.generateContentHash(article.title, article.content)
        val now = System.currentTimeMillis()

        // Check for existing duplicate by deduplication key or normalized URL
        val existing = articleDao.getArticleByDeduplicationKey(dedupKey)
            ?: if (normUrl.isNotBlank()) articleDao.getArticleByNormalizedUrl(normUrl) else null

        return if (existing != null) {
            // DUPLICATE DETECTED: Update existing record, preserve original creation and publication date
            val updatedEntity = existing.copy(
                title = article.title.ifBlank { existing.title },
                summary = article.summary.ifBlank { existing.summary },
                content = article.content.ifBlank { existing.content },
                imageUrl = (article.imageUrls.firstOrNull() ?: existing.imageUrl),
                imageUrlsJson = if (article.imageUrls.isNotEmpty()) article.imageUrls.joinToString(";") else existing.imageUrlsJson,
                status = article.status.name,
                errorMessage = article.errorMessage,
                retryCount = article.retryCount,
                dataSizeKb = if (article.dataSizeKb > 0) article.dataSizeKb else existing.dataSizeKb,
                successfulImagesCount = article.successfulImagesCount,
                totalImagesCount = article.totalImagesCount,
                updatedAt = now,
                fetchedAtEpoch = now,
                // Do NOT mark as new!
                isNew = false,
                contentHash = contentHash
            )

            articleDao.updateArticle(updatedEntity)

            NewsAuditLogger.logArticle(
                source = updatedEntity.sourceName,
                articleId = updatedEntity.id,
                url = updatedEntity.sourceLink,
                publishedAtEpoch = updatedEntity.publishedAtEpoch,
                fetchedAtEpoch = now,
                deduplicationKey = dedupKey,
                isDuplicate = true,
                isNew = false,
                storageAction = NewsAuditLogger.StorageAction.UPDATE_EXISTING
            )

            UpsertAuditResult(
                articleId = existing.id,
                isNewRecord = false,
                isGenuinelyNew = false
            )
        } else {
            // NEW RECORD: Check if genuinely published recently
            val isGenuinelyNew = DateParserAndValidator.isGenuinelyNew(
                publishedAtEpoch = parsedDate.epochMillis,
                dateSource = parsedDate.dateSource,
                isDuplicate = false,
                referenceTimeMillis = now
            )

            val newEntity = ArticleEntity(
                id = stableId,
                title = article.title,
                summary = article.summary,
                imageUrl = article.imageUrls.firstOrNull() ?: "",
                sourceLink = article.sourceUrl,
                content = article.content,
                sourceName = article.sourceName,
                publishedAt = parsedDate.formattedArabic,
                category = article.category,
                imageUrlsJson = article.imageUrls.joinToString(";"),
                status = article.status.name,
                errorMessage = article.errorMessage,
                retryCount = article.retryCount,
                dataSizeKb = article.dataSizeKb,
                successfulImagesCount = article.successfulImagesCount,
                totalImagesCount = article.totalImagesCount,
                createdAt = now,
                deduplicationKey = dedupKey,
                canonicalUrl = article.sourceUrl,
                normalizedUrl = normUrl,
                normalizedTitle = normTitle,
                publishedAtEpoch = parsedDate.epochMillis,
                fetchedAtEpoch = now,
                updatedAt = now,
                dateSource = parsedDate.dateSource.name,
                isNew = isGenuinelyNew,
                contentHash = contentHash
            )

            articleDao.insertArticle(newEntity)

            NewsAuditLogger.logArticle(
                source = newEntity.sourceName,
                articleId = newEntity.id,
                url = newEntity.sourceLink,
                publishedAtEpoch = newEntity.publishedAtEpoch,
                fetchedAtEpoch = now,
                deduplicationKey = dedupKey,
                isDuplicate = false,
                isNew = isGenuinelyNew,
                storageAction = NewsAuditLogger.StorageAction.INSERT_NEW
            )

            UpsertAuditResult(
                articleId = newEntity.id,
                isNewRecord = true,
                isGenuinelyNew = isGenuinelyNew
            )
        }
    }

    suspend fun updateArticle(article: ExtractedArticle) {
        articleDao.updateArticle(article.toEntity())
    }

    suspend fun deleteArticle(id: Long) {
        articleDao.deleteArticleById(id)
    }

    suspend fun getArticleBySourceLink(sourceLink: String): ExtractedArticle? {
        val norm = DeduplicationHelper.normalizeUrl(sourceLink)
        val entity = articleDao.getArticleByNormalizedUrl(norm)
            ?: articleDao.getArticleBySourceLink(sourceLink)
        return entity?.toModel()
    }

    suspend fun getArticleByDeduplicationKey(key: String): ExtractedArticle? {
        return articleDao.getArticleByDeduplicationKey(key)?.toModel()
    }

    fun searchArticles(query: String): Flow<List<ExtractedArticle>> {
        return articleDao.searchArticles(query).map { list -> list.map { it.toModel() } }
    }

    suspend fun toggleArticleOfflineSaved(articleId: Long, isSaved: Boolean) {
        articleDao.updateSavedOfflineStatus(articleId, isSaved)
    }

    suspend fun addCustomSource(name: String, url: String, category: String): Long {
        return customNewsSourceDao.insertSource(
            com.example.data.local.CustomNewsSourceEntity(
                name = name.trim(),
                url = url.trim(),
                category = category.trim().ifBlank { "عام" },
                isEnabled = true,
                isCustom = true
            )
        )
    }

    suspend fun toggleSourceEnabled(source: com.example.data.model.CustomNewsSource, isEnabled: Boolean) {
        customNewsSourceDao.updateSource(
            com.example.data.local.CustomNewsSourceEntity(
                id = source.id,
                name = source.name,
                url = source.url,
                category = source.category,
                isEnabled = isEnabled,
                isCustom = source.isCustom,
                createdAt = source.createdAt
            )
        )
    }

    suspend fun deleteCustomSource(sourceId: Long) {
        customNewsSourceDao.deleteSourceById(sourceId)
    }

    suspend fun seedDefaultSourcesIfNeeded() {
        if (customNewsSourceDao.getSourcesCount() == 0) {
            customNewsSourceDao.insertDefaultSources(
                com.example.worker.NewsSyncWorker.getDefaultNewsSources()
            )
        }
    }

    suspend fun saveUserSettings(settings: com.example.data.model.UserSettings) {
        userSettingsDao.saveUserSettings(
            com.example.data.local.UserSettingsEntity(
                id = 1,
                preferredCategories = settings.preferredCategories.joinToString(","),
                notificationsEnabled = settings.notificationsEnabled,
                notificationFrequencyMinutes = settings.notificationFrequencyMinutes,
                autoSyncEnabled = settings.autoSyncEnabled,
                autoSaveOffline = settings.autoSaveOffline
            )
        )
    }

    suspend fun saveRecentExtraction(extraction: RecentExtractionEntity) {
        recentExtractionDao.insertExtraction(extraction)
    }

    private fun ArticleEntity.toModel(): ExtractedArticle {
        val images = when {
            imageUrlsJson.isNotBlank() -> imageUrlsJson.split(";")
            imageUrl.isNotBlank() -> listOf(imageUrl)
            else -> emptyList()
        }
        val stat = try {
            ArticleStatus.valueOf(status)
        } catch (_: Exception) {
            ArticleStatus.SUCCESS
        }
        val dSource = try {
            DateSource.valueOf(dateSource)
        } catch (_: Exception) {
            DateSource.UNKNOWN
        }

        return ExtractedArticle(
            id = id,
            title = title,
            summary = summary,
            content = content,
            sourceName = sourceName,
            sourceUrl = sourceLink,
            publishedAt = publishedAt,
            category = category,
            imageUrls = images,
            status = stat,
            errorMessage = errorMessage,
            retryCount = retryCount,
            dataSizeKb = dataSizeKb,
            successfulImagesCount = successfulImagesCount,
            totalImagesCount = totalImagesCount,
            deduplicationKey = deduplicationKey,
            canonicalUrl = canonicalUrl,
            normalizedUrl = normalizedUrl,
            publishedAtEpoch = publishedAtEpoch,
            fetchedAtEpoch = fetchedAtEpoch,
            createdAtEpoch = createdAt,
            updatedAtEpoch = updatedAt,
            dateSource = dSource,
            isNew = isNew,
            freshness = ArticleFreshness.CACHED,
            contentHash = contentHash,
            isSavedOffline = isSavedOffline
        )
    }

    private fun ExtractedArticle.toEntity(): ArticleEntity {
        val primaryImage = imageUrls.firstOrNull() ?: ""
        return ArticleEntity(
            id = id,
            title = title,
            summary = summary,
            imageUrl = primaryImage,
            sourceLink = sourceUrl,
            content = content,
            sourceName = sourceName,
            publishedAt = publishedAt,
            category = category,
            imageUrlsJson = imageUrls.joinToString(";"),
            status = status.name,
            errorMessage = errorMessage,
            retryCount = retryCount,
            dataSizeKb = dataSizeKb,
            successfulImagesCount = successfulImagesCount,
            totalImagesCount = totalImagesCount,
            createdAt = if (createdAtEpoch > 0) createdAtEpoch else System.currentTimeMillis(),
            deduplicationKey = deduplicationKey,
            canonicalUrl = canonicalUrl,
            normalizedUrl = normalizedUrl,
            normalizedTitle = DeduplicationHelper.normalizeTitle(title),
            publishedAtEpoch = publishedAtEpoch,
            fetchedAtEpoch = if (fetchedAtEpoch > 0) fetchedAtEpoch else System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            dateSource = dateSource.name,
            isNew = isNew,
            contentHash = contentHash,
            isSavedOffline = isSavedOffline
        )
    }
}

