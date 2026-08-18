package com.example.rsq.reporting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReportEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(AiConverters::class)
abstract class LocalReportDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: LocalReportDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reports ADD COLUMN aiScore REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE reports ADD COLUMN detectedHazards TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE reports ADD COLUMN recommendedResources TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): LocalReportDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalReportDatabase::class.java,
                    "report_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
