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
}
