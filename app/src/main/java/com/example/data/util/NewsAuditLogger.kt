package com.example.data.util

import android.util.Log

object NewsAuditLogger {

    private const val TAG = "NewsAudit"

    enum class StorageAction {
        INSERT_NEW,
        UPDATE_EXISTING,
        SKIP_DUPLICATE,
        DELETE,
        QUERY
    }

    /**
     * Emits a structured log of article processing:
     * source | articleId | url | publishedAt | fetchedAt | deduplicationKey | isDuplicate | isNew | storageAction
     *
     * Sanitizes URLs and avoids exposing secrets or user credentials.
     */
    fun logArticle(
        source: String,
        articleId: Long,
        url: String,
        publishedAtEpoch: Long,
        fetchedAtEpoch: Long,
        deduplicationKey: String,
        isDuplicate: Boolean,
        isNew: Boolean,
        storageAction: StorageAction
    ) {
        val sanitizedUrl = sanitizeUrl(url)
        val shortKey = if (deduplicationKey.length > 12) deduplicationKey.take(12) + "..." else deduplicationKey

        val message = "source=$source | articleId=$articleId | url=$sanitizedUrl | " +
                "publishedAt=$publishedAtEpoch | fetchedAt=$fetchedAtEpoch | " +
                "deduplicationKey=$shortKey | isDuplicate=$isDuplicate | isNew=$isNew | " +
                "storageAction=$storageAction"

        Log.i(TAG, message)
    }

    private fun sanitizeUrl(url: String): String {
        return try {
            val qIdx = url.indexOf('?')
            if (qIdx == -1) url else url.substring(0, qIdx)
        } catch (_: Exception) {
            "sanitized_url"
        }
    }
}
