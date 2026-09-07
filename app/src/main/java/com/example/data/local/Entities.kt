package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val summary: String,
    val content: String,
    val sourceName: String,
    val sourceUrl: String,
    val publishedAt: String,
    val category: String,
    val imageUrlsJson: String, // comma or semicolon separated
    val status: String,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val dataSizeKb: Long = 0,
    val successfulImagesCount: Int = 0,
    val totalImagesCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_extractions")
data class RecentExtractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val sourceName: String,
    val articlesCount: Int,
    val imagesCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val storageKb: Long,
    val status: String,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis()
)
