package com.example.rsq.reporting.data

import android.util.Log
import com.example.rsq.reporting.data.local.ReportDao
import com.example.rsq.reporting.data.local.ReportEntity
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class LocalReportRepository(private val reportDao: ReportDao) {
    private val TAG = "LocalReportRepository"

    open suspend fun saveReport(report: Report, localPaths: List<String>, syncStatus: SyncStatus) {
        if (reportDao.getReportById(report.id) != null) return

        val entity = ReportEntity(
            id = report.id,
            userId = report.userId,
            title = report.title,
            description = report.description,
            severity = report.severity,
            status = report.status.name,
            timestamp = report.timestamp,
            latitude = report.latitude,
            longitude = report.longitude,
            imageUrl = report.imageUrl,
            imageUrls = report.imageUrls,
            localImagePath = localPaths.firstOrNull(),
            localImagePaths = localPaths,
            isOffline = report.isOffline,
            syncStatus = syncStatus,
            aiScore = report.aiScore,
            detectedHazards = report.detectedHazards,
            recommendedResources = report.recommendedResources
        )
        reportDao.insertReport(entity)
        Log.i(TAG, "LOCAL_REPORT_SAVED: ID=${report.id}, SyncStatus=$syncStatus")
    }

    open suspend fun getPendingReports(): List<ReportEntity> {
        return reportDao.getReportsBySyncStatus(SyncStatus.LOCAL_ONLY) +
               reportDao.getReportsBySyncStatus(SyncStatus.FAILED) +
               reportDao.getReportsBySyncStatus(SyncStatus.SYNCING)
    }

    open suspend fun getReportById(id: String): ReportEntity? {
        return reportDao.getReportById(id)
    }

    open suspend fun updateSyncStatus(id: String, status: SyncStatus) {
        reportDao.updateSyncStatus(id, status)
    }

    open suspend fun updateImageUrls(id: String, imageUrls: List<String>, status: SyncStatus) {
        val entity = reportDao.getReportById(id)
        if (entity != null) {
            val updated = entity.copy(
                imageUrls = imageUrls,
                imageUrl = imageUrls.firstOrNull() ?: entity.imageUrl,
                syncStatus = status
            )
            reportDao.insertReport(updated)
        }
    }

    open suspend fun updateReportStatus(id: String, status: ReportStatus) {
        reportDao.updateReportStatus(id, status.name, SyncStatus.LOCAL_ONLY)
    }

    open fun observeAllReports(): Flow<List<Report>> {
        return reportDao.getAllReports().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun ReportEntity.toDomain(): Report {
        return Report(
            id = id,
            userId = userId,
            title = title,
            description = description,
            severity = severity,
            status = ReportStatus.fromString(status),
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUrl,
            imageUrls = imageUrls,
            isOffline = isOffline,
            aiScore = aiScore,
            detectedHazards = detectedHazards,
            recommendedResources = recommendedResources
        )
    }
}
