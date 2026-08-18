package com.example.rsq.reporting.data

import com.example.rsq.reporting.data.local.ReportDao
import com.example.rsq.reporting.data.local.ReportEntity
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class LocalReportRepository(private val reportDao: ReportDao) {

    open suspend fun saveReport(report: Report, localPath: String?, syncStatus: SyncStatus) {
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
            localImagePath = localPath,
            isOffline = report.isOffline,
            syncStatus = syncStatus
        )
        reportDao.insertReport(entity)
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

    open suspend fun updateImageUrl(id: String, imageUrl: String, status: SyncStatus) {
        reportDao.updateImageUrl(id, imageUrl, status)
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
            isOffline = isOffline
        )
    }
}
