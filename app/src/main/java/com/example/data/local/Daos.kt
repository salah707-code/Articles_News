package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY published_at_epoch DESC, created_at DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE status = :status ORDER BY published_at_epoch DESC, created_at DESC")
    fun getArticlesByStatus(status: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    fun getArticleById(id: Long): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE deduplication_key = :key LIMIT 1")
    suspend fun getArticleByDeduplicationKey(key: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE normalized_url = :normalizedUrl LIMIT 1")
    suspend fun getArticleByNormalizedUrl(normalizedUrl: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE source_link = :sourceLink LIMIT 1")
    suspend fun getArticleBySourceLink(sourceLink: String): ArticleEntity?

    @Query("""
        SELECT * FROM articles 
        WHERE title LIKE '%' || :query || '%' 
           OR summary LIKE '%' || :query || '%' 
           OR source_name LIKE '%' || :query || '%'
        ORDER BY published_at_epoch DESC, created_at DESC
    """)
    fun searchArticles(query: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE is_new = 1 ORDER BY published_at_epoch DESC")
    fun getNewArticles(): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity): Long

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    @Delete
    suspend fun deleteArticle(article: ArticleEntity)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteArticleById(id: Long)

    @Query("DELETE FROM articles")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM articles")
    fun getArticlesCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT source_name) FROM articles")
    fun getSourcesCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(data_size_kb), 0) FROM articles")
    fun getTotalStorageKb(): Flow<Long>

    @Query("SELECT COALESCE(SUM(total_images_count), 0) FROM articles")
    fun getTotalImagesCount(): Flow<Int>
}

@Dao
interface RecentExtractionDao {
    @Query("SELECT * FROM recent_extractions ORDER BY timestamp DESC LIMIT 20")
    fun getRecentExtractions(): Flow<List<RecentExtractionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtraction(extraction: RecentExtractionEntity): Long

    @Query("DELETE FROM recent_extractions")
    suspend fun clearAll()
}
