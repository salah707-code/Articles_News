package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.CustomNewsSourceEntity
import com.example.data.model.ExtractedArticle
import com.example.data.remote.RealNewsWebFetcher
import com.example.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class NewsSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "NewsSyncWorker started background fetch")
            val database = AppDatabase.getDatabase(appContext)
            val settingsDao = database.userSettingsDao()
            val sourcesDao = database.customNewsSourceDao()
            val repository = ArticleRepository(database)

            val settings = settingsDao.getUserSettingsSync()
            if (settings != null && !settings.notificationsEnabled) {
                Log.d(TAG, "Notifications disabled in settings, skipping sync")
                return@withContext Result.success()
            }

            val preferredCategories = settings?.preferredCategories
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: listOf("تكنولوجيا", "سياسة", "اقتصاد", "رياضة")

            // Ensure we have active news sources
            var activeSources = sourcesDao.getEnabledSources()
            if (activeSources.isEmpty()) {
                val defaultSources = getDefaultNewsSources()
                sourcesDao.insertDefaultSources(defaultSources)
                activeSources = sourcesDao.getEnabledSources()
            }

            val newlyFoundArticles = mutableListOf<ExtractedArticle>()

            for (source in activeSources.take(4)) {
                try {
                    val articles = RealNewsWebFetcher.fetchRealNewsFromUrl(
                        url = source.url,
                        sourceName = source.name,
                        fallbackCategory = source.category
                    )

                    if (articles != null) {
                        for (art in articles) {
                            val upsertResult = repository.upsertArticleWithAudit(art)
                            val isMatchCategory = preferredCategories.any { pref ->
                                art.category.contains(pref, ignoreCase = true) ||
                                        pref.contains(art.category, ignoreCase = true)
                            }
                            if (upsertResult.isGenuinelyNew && isMatchCategory) {
                                newlyFoundArticles.add(art)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching source: ${source.name}", e)
                }
            }

            if (newlyFoundArticles.isNotEmpty()) {
                val primary = newlyFoundArticles.first()
                sendArticleNotification(
                    context = appContext,
                    article = primary,
                    totalNewCount = newlyFoundArticles.size
                )
            } else {
                Log.d(TAG, "No new articles found matching preferred categories: $preferredCategories")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "NewsSyncWorker failed", e)
            Result.retry()
        }
    }

    private fun sendArticleNotification(
        context: Context,
        article: ExtractedArticle,
        totalNewCount: Int
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنبيهات الأخبار العاجلة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات فورية عند جلب مقالات وأخبار جديدة من الفئات المفضلة"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_ARTICLE_ID", article.id)
            putExtra("EXTRA_NAV_TAB", "LIBRARY")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            article.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (totalNewCount > 1) {
            "🔥 ${totalNewCount} أخبار جديدة في ${article.category}"
        } else {
            "📰 خبر جديد: ${article.category}"
        }

        val body = "${article.title} — ${article.sourceName}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${article.title}\n\n${article.summary}\nالمصدر: ${article.sourceName}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val TAG = "NewsSyncWorker"
        const val WORK_NAME_PERIODIC = "news_periodic_sync_work"
        const val WORK_NAME_IMMEDIATE = "news_immediate_sync_work"
        const val CHANNEL_ID = "news_alerts_channel"
        const val NOTIFICATION_ID = 1001

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 60L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val actualInterval = intervalMinutes.coerceAtLeast(15L) // WorkManager min interval is 15 minutes
            val workRequest = PeriodicWorkRequestBuilder<NewsSyncWorker>(actualInterval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(TAG, "Scheduled periodic sync every $actualInterval minutes")
        }

        fun triggerImmediateSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<NewsSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Triggered immediate sync work")
        }

        fun getDefaultNewsSources(): List<CustomNewsSourceEntity> {
            return listOf(
                CustomNewsSourceEntity(
                    id = 1,
                    name = "الجزيرة نت",
                    url = "https://www.aljazeera.net/rss",
                    category = "سياسة",
                    isEnabled = true,
                    isCustom = false
                ),
                CustomNewsSourceEntity(
                    id = 2,
                    name = "بي بي سي عربي",
                    url = "https://feeds.bbci.co.uk/arabic/rss.xml",
                    category = "عام",
                    isEnabled = true,
                    isCustom = false
                ),
                CustomNewsSourceEntity(
                    id = 3,
                    name = "سكاي نيوز عربية",
                    url = "https://www.skynewsarabia.com/rss.xml",
                    category = "اقتصاد",
                    isEnabled = true,
                    isCustom = false
                ),
                CustomNewsSourceEntity(
                    id = 4,
                    name = "عالم التقنية / تكنولوجيا",
                    url = "https://techcrunch.com/feed/",
                    category = "تكنولوجيا",
                    isEnabled = true,
                    isCustom = false
                ),
                CustomNewsSourceEntity(
                    id = 5,
                    name = "العربية نت",
                    url = "https://www.alarabiya.net/rss",
                    category = "سياسة",
                    isEnabled = true,
                    isCustom = false
                )
            )
        }
    }
}
