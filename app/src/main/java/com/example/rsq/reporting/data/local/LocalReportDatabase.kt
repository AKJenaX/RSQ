package com.example.rsq.reporting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.rsq.data.local.*

@Database(
    entities = [
        ReportEntity::class,
        AssignmentEntity::class,
        VolunteerEntity::class,
        NotificationEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(AiConverters::class)
abstract class LocalReportDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun volunteerDao(): VolunteerDao
    abstract fun notificationDao(): NotificationDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `assignments` (`id` TEXT NOT NULL, `reportId` TEXT NOT NULL, `volunteerId` TEXT, `volunteerName` TEXT NOT NULL, `victimName` TEXT NOT NULL, `disasterType` TEXT NOT NULL, `location` TEXT NOT NULL, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `assignedTime` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `volunteers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `totalAssignments` INTEGER NOT NULL, `pendingAssignments` INTEGER NOT NULL, `activeAssignments` INTEGER NOT NULL, `completedAssignments` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `timestamp` TEXT NOT NULL, `type` TEXT NOT NULL, `isRead` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE volunteers ADD COLUMN firebaseUid TEXT")
                db.execSQL("ALTER TABLE notifications ADD COLUMN recipientId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reports ADD COLUMN imageUrls TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE reports ADD COLUMN localImagePaths TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): LocalReportDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalReportDatabase::class.java,
                    "report_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
