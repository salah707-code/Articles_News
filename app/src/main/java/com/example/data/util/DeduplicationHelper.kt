package com.example.data.util

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import kotlin.math.abs

object DeduplicationHelper {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "fbclid", "gclid", "ref", "ref_src", "source", "feature",
        "cid", "ncid", "ocid", "cmp", "campaign_id", "ad_id", "session_id",
        "trk", "src", "spm"
    )

    /**
     * Normalizes a URL:
     * - Protocol canonicalization (http/https)
     * - Lowercases scheme and host
     * - Strips default ports (:80, :443)
     * - Removes trailing slashes (e.g. /article/ -> /article)
     * - Removes URL fragments (#section)
     * - Strips analytics & marketing tracking query parameters
     * - Sorts remaining query parameters alphabetically
     */
    fun normalizeUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return try {
            val trimmed = rawUrl.trim()
            val uri = URI(if (!trimmed.contains("://")) "https://$trimmed" else trimmed)

            val scheme = (uri.scheme ?: "https").lowercase()
            val host = (uri.host ?: "").lowercase().removePrefix("www.")
            if (host.isEmpty()) return trimmed.lowercase()

            var path = uri.path ?: ""
            if (path.length > 1 && path.endsWith("/")) {
                path = path.dropLast(1)
            }

            // Filter and sort query params
            val rawQuery = uri.query
            val cleanQuery = if (!rawQuery.isNullOrBlank()) {
                rawQuery.split("&")
                    .mapNotNull { pair ->
                        val parts = pair.split("=", limit = 2)
                        val key = URLDecoder.decode(parts[0], "UTF-8").lowercase()
                        if (key in TRACKING_PARAMS) null
                        else {
                            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                            key to value
                        }
                    }
                    .sortedBy { it.first }
                    .joinToString("&") { (k, v) -> if (v.isEmpty()) k else "$k=$v" }
            } else ""

            val builder = StringBuilder("$scheme://$host$path")
            if (cleanQuery.isNotEmpty()) {
                builder.append("?$cleanQuery")
            }
            builder.toString()
        } catch (_: Exception) {
            rawUrl.trim().lowercase().removeSuffix("/")
        }
    }

    /**
     * Normalizes an Arabic or English title:
     * - Strips Arabic Tashkeel / Harakat (Fatha, Damma, Kasra, Sukun, Tanwin, Shadda)
     * - Normalizes Alef forms (أ, إ, آ -> ا)
     * - Normalizes Taa Marbuta (ة -> ه)
     * - Normalizes Alef Maksura (ى -> ي)
     * - Removes punctuation and special symbols
     * - Collapses multiple spaces
     */
    fun normalizeTitle(title: String): String {
        if (title.isBlank()) return ""

        var t = title.trim().lowercase()

        // Strip Arabic Tashkeel
        t = t.replace(Regex("[\u064B-\u0652\u0670]"), "")

        // Normalize Alef
        t = t.replace(Regex("[أإآٱ]"), "ا")

        // Normalize Taa Marbuta
        t = t.replace('ة', 'ه')

        // Normalize Alef Maksura
        t = t.replace('ى', 'ي')

        // Strip punctuation and symbols (keep Arabic & Latin letters and digits)
        t = t.replace(Regex("[^a-z0-9\\u0600-\\u06FF\\s]"), " ")

        // Collapse multiple whitespace
        t = t.replace(Regex("\\s+"), " ").trim()

        return t
    }

    /**
     * Generates a SHA-256 hash of content or text.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a stable, canonical deduplication key for an article:
     * 1. If normalized URL exists and is substantive: SHA-256 of source + normalizedUrl
     * 2. Otherwise: SHA-256 of source + normalizedTitle
     */
    fun generateDeduplicationKey(
        sourceName: String,
        canonicalUrl: String,
        title: String
    ): String {
        val cleanSource = sourceName.trim().lowercase()
        val normUrl = normalizeUrl(canonicalUrl)

        return if (normUrl.isNotBlank() && normUrl.length > 10) {
            sha256("url|$cleanSource|$normUrl")
        } else {
            val normTitle = normalizeTitle(title)
            sha256("title|$cleanSource|$normTitle")
        }
    }

    /**
     * Computes a content hash to detect if article body changed over time.
     */
    fun generateContentHash(title: String, content: String): String {
        val cleanTitle = normalizeTitle(title)
        val cleanContent = content.trim().take(400)
        return sha256("$cleanTitle\n$cleanContent")
    }

    /**
     * Generates a deterministic, positive 63-bit Long ID from a deduplication key.
     * Guarantees the same article always receives the same primary ID across app sessions.
     */
    fun generateStableId(deduplicationKey: String): Long {
        if (deduplicationKey.isBlank()) return 0L
        val hash = sha256(deduplicationKey)
        // Take first 15 hex characters to fit safely in positive Long (15 hex digits < 2^60)
        val hexSub = hash.take(15)
        return abs(hexSub.toLongOrNull(16) ?: abs(deduplicationKey.hashCode().toLong()))
    }
}
