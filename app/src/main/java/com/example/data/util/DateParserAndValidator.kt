package com.example.data.util

import com.example.data.model.DateSource
import com.example.data.model.ParsedDateResult
import com.example.data.model.ifUnknown
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object DateParserAndValidator {

    // 24 hours in milliseconds for "New" article classification window
    const val NEW_ARTICLE_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    // Clock skew tolerance: 30 minutes in the future allowed for server time differences
    private const val CLOCK_SKEW_TOLERANCE_MILLIS = 30 * 60 * 1000L

    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    private val rfcFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss z"
    )

    /**
     * Parses a raw date string without guessing.
     * If the date cannot be validated, it is marked as UNKNOWN with epoch 0.
     */
    fun parseAndValidate(
        rawDate: String?,
        sourceType: DateSource = DateSource.UNKNOWN,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): ParsedDateResult {
        if (rawDate.isNullOrBlank()) {
            return ParsedDateResult(
                epochMillis = 0L,
                formattedArabic = "تاريخ غير محدد",
                dateSource = DateSource.UNKNOWN,
                isValid = false
            )
        }

        val trimmed = rawDate.trim()

        // 1. Try ISO formats
        for (pattern in isoFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                val date = sdf.parse(trimmed)
                if (date != null) {
                    val epoch = date.time
                    return validateEpoch(epoch, trimmed, sourceType.ifUnknown(DateSource.API), referenceTimeMillis)
                }
            } catch (_: Exception) {}
        }

        // 2. Try RFC / standard English HTTP formats
        for (pattern in rfcFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                val date = sdf.parse(trimmed)
                if (date != null) {
                    val epoch = date.time
                    return validateEpoch(epoch, trimmed, sourceType.ifUnknown(DateSource.METADATA), referenceTimeMillis)
                }
            } catch (_: Exception) {}
        }

        // 3. Try Arabic relative date formats (e.g. "منذ ساعتين", "اليوم، 14:30", "أمس، 18:00")
        val arabicRelative = parseArabicRelativeDate(trimmed, referenceTimeMillis)
        if (arabicRelative != null) {
            return validateEpoch(arabicRelative, trimmed, sourceType.ifUnknown(DateSource.HTML), referenceTimeMillis)
        }

        // 4. Try epoch number in string
        val epochLong = trimmed.toLongOrNull()
        if (epochLong != null && epochLong > 1000000000L) {
            val epoch = if (epochLong < 100000000000L) epochLong * 1000L else epochLong
            return validateEpoch(epoch, formatEpochToArabic(epoch), sourceType.ifUnknown(DateSource.API), referenceTimeMillis)
        }

        // Strict fallback: Never guess a missing/invalid date as "now"
        return ParsedDateResult(
            epochMillis = 0L,
            formattedArabic = "تاريخ غير موثق ($trimmed)",
            dateSource = DateSource.UNKNOWN,
            isValid = false
        )
    }

    private fun validateEpoch(
        epochMillis: Long,
        rawDisplay: String,
        determinedSource: DateSource,
        referenceTimeMillis: Long
    ): ParsedDateResult {
        // Clock skew check: dates far in the future are invalid
        if (epochMillis > referenceTimeMillis + CLOCK_SKEW_TOLERANCE_MILLIS) {
            return ParsedDateResult(
                epochMillis = 0L,
                formattedArabic = "تاريخ مستقبلي غير صالح",
                dateSource = DateSource.UNKNOWN,
                isValid = false
            )
        }

        // Extremely old dates (e.g. before year 2000) or negative
        if (epochMillis < 946684800000L) { // 2000-01-01
            return ParsedDateResult(
                epochMillis = epochMillis,
                formattedArabic = formatEpochToArabic(epochMillis),
                dateSource = determinedSource,
                isValid = false
            )
        }

        val formatted = formatEpochToArabic(epochMillis)
        return ParsedDateResult(
            epochMillis = epochMillis,
            formattedArabic = formatted,
            dateSource = determinedSource,
            isValid = true
        )
    }

    private fun parseArabicRelativeDate(text: String, referenceTime: Long): Long? {
        val clean = text.trim()
        val calendar = Calendar.getInstance().apply { timeInMillis = referenceTime }

        // "منذ X دقائق" / "منذ دقيقة" / "منذ دقيقتين"
        val minMatcher = Pattern.compile("منذ\\s+(\\d+)\\s+دقيق").matcher(clean)
        if (minMatcher.find()) {
            val mins = minMatcher.group(1)?.toIntOrNull() ?: 1
            return referenceTime - (mins * 60 * 1000L)
        }
        if (clean.contains("منذ دقيقة")) return referenceTime - (60 * 1000L)
        if (clean.contains("منذ دقيقتين")) return referenceTime - (2 * 60 * 1000L)

        // "منذ X ساعات" / "منذ ساعة" / "منذ ساعتين"
        val hourMatcher = Pattern.compile("منذ\\s+(\\d+)\\s+ساع").matcher(clean)
        if (hourMatcher.find()) {
            val hours = hourMatcher.group(1)?.toIntOrNull() ?: 1
            return referenceTime - (hours * 3600 * 1000L)
        }
        if (clean.contains("منذ ساعة")) return referenceTime - (3600 * 1000L)
        if (clean.contains("منذ ساعتين")) return referenceTime - (2 * 3600 * 1000L)

        // "منذ X أيام" / "منذ يوم" / "منذ يومين"
        val dayMatcher = Pattern.compile("منذ\\s+(\\d+)\\s+يوم").matcher(clean)
        if (dayMatcher.find()) {
            val days = dayMatcher.group(1)?.toIntOrNull() ?: 1
            return referenceTime - (days * 86400 * 1000L)
        }
        if (clean.contains("منذ يومين")) return referenceTime - (2 * 86400 * 1000L)
        if (clean.contains("منذ يوم")) return referenceTime - (86400 * 1000L)

        // "اليوم، HH:mm"
        val todayMatcher = Pattern.compile("اليوم[،,\\s]+(\\d{1,2}):(\\d{2})").matcher(clean)
        if (todayMatcher.find()) {
            val hour = todayMatcher.group(1)?.toIntOrNull() ?: 0
            val minute = todayMatcher.group(2)?.toIntOrNull() ?: 0
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            return calendar.timeInMillis
        }

        // "أمس، HH:mm"
        val yestMatcher = Pattern.compile("أمس[،,\\s]+(\\d{1,2}):(\\d{2})").matcher(clean)
        if (yestMatcher.find()) {
            val hour = yestMatcher.group(1)?.toIntOrNull() ?: 0
            val minute = yestMatcher.group(2)?.toIntOrNull() ?: 0
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            return calendar.timeInMillis
        }

        return null
    }

    fun formatEpochToArabic(epochMillis: Long): String {
        if (epochMillis <= 0) return "تاريخ غير محدد"
        val now = System.currentTimeMillis()
        val diff = now - epochMillis

        return when {
            diff < 0 -> "الآن"
            diff < 60 * 1000L -> "الآن"
            diff < 60 * 60 * 1000L -> {
                val mins = (diff / (60 * 1000L)).toInt()
                if (mins == 1) "منذ دقيقة" else if (mins == 2) "منذ دقيقتين" else "منذ $mins دقائق"
            }
            diff < 24 * 60 * 60 * 1000L -> {
                val hours = (diff / (3600 * 1000L)).toInt()
                if (hours == 1) "منذ ساعة" else if (hours == 2) "منذ ساعتين" else "منذ $hours ساعات"
            }
            diff < 48 * 60 * 60 * 1000L -> "أمس"
            diff < 7 * 24 * 60 * 60 * 1000L -> {
                val days = (diff / (86400 * 1000L)).toInt()
                "منذ $days أيام"
            }
            else -> {
                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
                sdf.format(Date(epochMillis))
            }
        }
    }

    /**
     * An article is genuinely "NEW" only if:
     * 1. It has a verified publication date (not UNKNOWN)
     * 2. Published within NEW_ARTICLE_WINDOW_MILLIS (e.g. 24 hours)
     * 3. Not marked as a duplicate
     */
    fun isGenuinelyNew(
        publishedAtEpoch: Long,
        dateSource: DateSource,
        isDuplicate: Boolean,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (isDuplicate) return false
        if (dateSource == DateSource.UNKNOWN) return false
        if (publishedAtEpoch <= 0L) return false

        val age = referenceTimeMillis - publishedAtEpoch
        return age in 0..NEW_ARTICLE_WINDOW_MILLIS
    }

    private fun DateSource.ifUnknown(fallback: DateSource): DateSource {
        return if (this == DateSource.UNKNOWN) fallback else this
    }

    private fun String?.isNullPrematureOrBlank(): Boolean {
        return this == null || this.trim().isEmpty() || this.trim().lowercase() == "null"
    }
}
