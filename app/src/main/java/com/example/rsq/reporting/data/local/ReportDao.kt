package com.example.rsq.reporting.data.local

import androidx.room.*
import com.example.rsq.reporting.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getReportById(id: String): ReportEntity?

    @Query("SELECT * FROM reports WHERE syncStatus = :status ORDER BY timestamp ASC")
    suspend fun getReportsBySyncStatus(status: SyncStatus): List<ReportEntity>

    @Query("UPDATE reports SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE reports SET imageUrl = :imageUrl, syncStatus = :status WHERE id = :id")
    suspend fun updateImageUrl(id: String, imageUrl: String, status: SyncStatus)

    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<ReportEntity>>

    @Delete
    suspend fun deleteReport(report: ReportEntity)
}
