package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY createdAt DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE status = :status ORDER BY createdAt DESC")
    fun getArticlesByStatus(status: String): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity): Long

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteArticleById(id: Long)

    @Query("DELETE FROM articles")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM articles")
    fun getArticlesCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT sourceName) FROM articles")
    fun getSourcesCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(dataSizeKb), 0) FROM articles")
    fun getTotalStorageKb(): Flow<Long>

    @Query("SELECT COALESCE(SUM(totalImagesCount), 0) FROM articles")
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
