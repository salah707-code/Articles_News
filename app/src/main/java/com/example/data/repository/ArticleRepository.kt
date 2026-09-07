package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.RecentExtractionEntity
import com.example.data.model.ArticleStatus
import com.example.data.model.ExtractedArticle
import com.example.data.model.ExtractionStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ArticleRepository(private val database: AppDatabase) {
    private val articleDao = database.articleDao()
    private val recentExtractionDao = database.recentExtractionDao()

    val allArticles: Flow<List<ExtractedArticle>> = articleDao.getAllArticles().map { entities ->
        entities.map { it.toModel() }
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

    suspend fun insertArticles(articles: List<ExtractedArticle>) {
        val entities = articles.map { it.toEntity() }
        articleDao.insertArticles(entities)
    }

    suspend fun insertArticle(article: ExtractedArticle): Long {
        return articleDao.insertArticle(article.toEntity())
    }

    suspend fun updateArticle(article: ExtractedArticle) {
        articleDao.updateArticle(article.toEntity())
    }

    suspend fun deleteArticle(id: Long) {
        articleDao.deleteArticleById(id)
    }

    suspend fun getArticleBySourceLink(sourceLink: String): ExtractedArticle? {
        return articleDao.getArticleBySourceLink(sourceLink)?.toModel()
    }

    fun searchArticles(query: String): Flow<List<ExtractedArticle>> {
        return articleDao.searchArticles(query).map { list -> list.map { it.toModel() } }
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
        } catch (e: Exception) {
            ArticleStatus.SUCCESS
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
            totalImagesCount = totalImagesCount
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
            totalImagesCount = totalImagesCount
        )
    }
}
