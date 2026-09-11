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
        Index(value = ["deduplication_key"], unique = true),
        Index(value = ["source_link"]),
        Index(value = ["published_at_epoch"]),
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
    val createdAt: Long = System.currentTimeMillis(),

    // Forensic Timeline & Deduplication Columns
    @ColumnInfo(name = "deduplication_key")
    val deduplicationKey: String = "",

    @ColumnInfo(name = "canonical_url")
    val canonicalUrl: String = sourceLink,

    @ColumnInfo(name = "normalized_url")
    val normalizedUrl: String = "",

    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String = "",

    @ColumnInfo(name = "published_at_epoch")
    val publishedAtEpoch: Long = 0L,

    @ColumnInfo(name = "fetched_at_epoch")
    val fetchedAtEpoch: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "date_source")
    val dateSource: String = "UNKNOWN",

    @ColumnInfo(name = "is_new")
    val isNew: Boolean = false,

    @ColumnInfo(name = "content_hash")
    val contentHash: String = "",

    @ColumnInfo(name = "is_saved_offline")
    val isSavedOffline: Boolean = false
) {
    // Convenience alias for existing callers
    val sourceUrl: String get() = sourceLink
}

@Entity(
    tableName = "custom_sources",
    indices = [
        Index(value = ["url"], unique = true)
    ]
)
data class CustomNewsSourceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "category")
    val category: String = "عام",

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_settings"
)
data class UserSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "preferred_categories")
    val preferredCategories: String = "تكنولوجيا,سياسة,اقتصاد,رياضة,صحة,ثقافة",

    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,

    @ColumnInfo(name = "notification_frequency_minutes")
    val notificationFrequencyMinutes: Long = 60L,

    @ColumnInfo(name = "auto_sync_enabled")
    val autoSyncEnabled: Boolean = true,

    @ColumnInfo(name = "auto_save_offline")
    val autoSaveOffline: Boolean = false
)

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
