package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ArticleEntity::class,
        RecentExtractionEntity::class,
        CustomNewsSourceEntity::class,
        UserSettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun recentExtractionDao(): RecentExtractionDao
    abstract fun customNewsSourceDao(): CustomNewsSourceDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN deduplication_key TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN canonical_url TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN normalized_url TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN normalized_title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN published_at_epoch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN fetched_at_epoch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN date_source TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE articles ADD COLUMN is_new INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''")

                // Update deduplication_key for existing records to prevent unique constraint conflict
                db.execSQL("UPDATE articles SET deduplication_key = 'legacy_' || id WHERE deduplication_key = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_articles_deduplication_key ON articles(deduplication_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_articles_published_at_epoch ON articles(published_at_epoch)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN is_saved_offline INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        url TEXT NOT NULL,
                        category TEXT NOT NULL,
                        is_enabled INTEGER NOT NULL,
                        is_custom INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custom_sources_url ON custom_sources(url)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_settings (
                        id INTEGER PRIMARY KEY NOT NULL,
                        preferred_categories TEXT NOT NULL,
                        notifications_enabled INTEGER NOT NULL,
                        notification_frequency_minutes INTEGER NOT NULL,
                        auto_sync_enabled INTEGER NOT NULL,
                        auto_save_offline INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT OR IGNORE INTO user_settings (id, preferred_categories, notifications_enabled, notification_frequency_minutes, auto_sync_enabled, auto_save_offline) VALUES (1, 'تكنولوجيا,سياسة,اقتصاد,رياضة,صحة,ثقافة', 1, 60, 1, 0)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "news_extractor_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
