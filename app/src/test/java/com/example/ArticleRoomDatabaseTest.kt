package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleDao
import com.example.data.local.ArticleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArticleRoomDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var articleDao: ArticleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        articleDao = database.articleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve article metadata including title, summary, imageUrl, and sourceLink`() = runBlocking {
        val article = ArticleEntity(
            title = "انطلاق قمة الذكاء الاصطناعي العالمية",
            summary = "ملخص شامل لأبرز فعاليات قمة الذكاء الاصطناعي ونتائجها.",
            imageUrl = "https://images.unsplash.com/photo-ai-summit.jpg",
            sourceLink = "https://www.aljazeera.net/tech/ai-summit-2026",
            content = "نص المحتوى الكامل للتقرير الإخباري...",
            sourceName = "الجزيرة نت",
            publishedAt = "منذ ساعة",
            category = "تكنولوجيا"
        )

        val generatedId = articleDao.insertArticle(article)

        val retrievedList = articleDao.getAllArticles().first()
        assertEquals(1, retrievedList.size)

        val saved = retrievedList[0]
        assertEquals("انطلاق قمة الذكاء الاصطناعي العالمية", saved.title)
        assertEquals("ملخص شامل لأبرز فعاليات قمة الذكاء الاصطناعي ونتائجها.", saved.summary)
        assertEquals("https://images.unsplash.com/photo-ai-summit.jpg", saved.imageUrl)
        assertEquals("https://www.aljazeera.net/tech/ai-summit-2026", saved.sourceLink)

        // Test querying by source link
        val byLink = articleDao.getArticleBySourceLink("https://www.aljazeera.net/tech/ai-summit-2026")
        assertNotNull(byLink)
        assertEquals(saved.title, byLink?.title)
    }

    @Test
    fun `articles ordered by published_at_epoch ensure true chronological sorting`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldArticle = ArticleEntity(
            title = "خبر قديم منذ أسبوع",
            summary = "ملخص أرشيفي",
            sourceLink = "https://news.com/old",
            publishedAt = "2026-03-01",
            publishedAtEpoch = now - (7 * 86_400_000L),
            deduplicationKey = "hash_old",
            isNew = false
        )
        val freshArticle = ArticleEntity(
            title = "خبر عاجل الآن",
            summary = "ملخص خبر حديث",
            sourceLink = "https://news.com/fresh",
            publishedAt = "منذ 10 دقائق",
            publishedAtEpoch = now - (600_000L),
            deduplicationKey = "hash_fresh",
            isNew = true
        )

        // Insert in reverse order (old first, fresh second)
        articleDao.insertArticle(oldArticle)
        articleDao.insertArticle(freshArticle)

        val articles = articleDao.getAllArticles().first()
        assertEquals(2, articles.size)
        // First article in list must be the newest by publication date, NOT old article
        assertEquals("خبر عاجل الآن", articles[0].title)
        assertEquals(true, articles[0].isNew)
        assertEquals("خبر قديم منذ أسبوع", articles[1].title)
        assertEquals(false, articles[1].isNew)
    }

    @Test
    fun `deduplication query finds existing article by deduplication key`() = runBlocking {
        val article = ArticleEntity(
            title = "بيان وزارة المالية",
            summary = "ملخص",
            sourceLink = "https://finance.gov/statement?ref=twitter",
            canonicalUrl = "https://finance.gov/statement",
            deduplicationKey = "finance_stmt_key_123",
            publishedAtEpoch = 1700000000000L
        )
        articleDao.insertArticle(article)

        val found = articleDao.getArticleByDeduplicationKey("finance_stmt_key_123")
        assertNotNull(found)
        assertEquals("بيان وزارة المالية", found?.title)
    }

    @Test
    fun `safe refresh updates existing article without deleting database`() = runBlocking {
        val article = ArticleEntity(
            id = 1L,
            title = "مقال تجريبي",
            summary = "ملخص قديم",
            sourceLink = "https://test.com/1",
            deduplicationKey = "key_test_1",
            publishedAtEpoch = 1700000000000L,
            isNew = true
        )
        articleDao.insertArticle(article)

        // Simulate refresh: re-evaluating isNew to false because publication date is old
        val updated = article.copy(isNew = false, summary = "ملخص محدث")
        articleDao.updateArticle(updated)

        val retrieved = articleDao.getAllArticles().first()
        assertEquals(1, retrieved.size)
        assertEquals("ملخص محدث", retrieved[0].summary)
        assertEquals(false, retrieved[0].isNew)
    }
}
