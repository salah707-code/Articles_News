package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database schema entity storing extracted article metadata:
 * - title: Article headline / title
 * - summary: Concise summary / abstract
 * - imageUrl: Direct primary image URL
 * - sourceLink: Original website URL / source link
 * as well as extraction metadata and status tracking.
 */
@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["source_link"]),
        Index(value = ["status"]),
        Index(value = ["created_at"])
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String = "",

    @ColumnInfo(name = "source_link")
    val sourceLink: String = "",

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "source_name")
    val sourceName: String = "",

    @ColumnInfo(name = "published_at")
    val publishedAt: String = "",

    @ColumnInfo(name = "category")
    val category: String = "عام",

    @ColumnInfo(name = "image_urls_json")
    val imageUrlsJson: String = "",

    @ColumnInfo(name = "status")
    val status: String = "SUCCESS",

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "data_size_kb")
    val dataSizeKb: Long = 0,

    @ColumnInfo(name = "successful_images_count")
    val successfulImagesCount: Int = 0,

    @ColumnInfo(name = "total_images_count")
    val totalImagesCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    // Convenience alias for existing callers
    val sourceUrl: String get() = sourceLink
}

@Entity(
    tableName = "recent_extractions",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class RecentExtractionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "source_name")
    val sourceName: String,

    @ColumnInfo(name = "articles_count")
    val articlesCount: Int,

    @ColumnInfo(name = "images_count")
    val imagesCount: Int,

    @ColumnInfo(name = "success_count")
    val successCount: Int,

    @ColumnInfo(name = "failed_count")
    val failedCount: Int,

    @ColumnInfo(name = "storage_kb")
    val storageKb: Long,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
