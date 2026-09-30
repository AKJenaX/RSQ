package com.example.rsq.reporting.data

import android.util.Log
import com.example.rsq.reporting.data.local.ReportDao
import com.example.rsq.reporting.data.local.ReportEntity
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class LocalReportRepository(private val reportDao: ReportDao) {
    private val TAG = "LocalReportRepository"
    private val DIAG_LOG = "RSQ_DIAGNOSTIC"

    open suspend fun saveReport(report: Report, localPaths: List<String>, syncStatus: SyncStatus) {
        val currentAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (t: Throwable) { "" }
        val effectiveOriginUid = if (report.originUserId.isNotBlank()) report.originUserId else report.userId
        val isRelayed = report.receivedViaRelay || (effectiveOriginUid.isNotBlank() && currentAuthUid.isNotBlank() && effectiveOriginUid != currentAuthUid)
        val effectiveRelayUid = if (report.relayUserId.isNotBlank()) report.relayUserId else if (isRelayed) currentAuthUid else ""

        Log.i(DIAG_LOG, "BOUNDARY_3_BEFORE_SAVE_REPORT: " +
            "reportId=${report.id}, " +
            "originNodeId=${report.relayDeviceId}, " +
            "originUserId=$effectiveOriginUid, " +
            "relayDeviceId=${report.relayDeviceId}, " +
            "relayUserId=$effectiveRelayUid, " +
            "receivedViaRelay=$isRelayed"
        )

        val existing = reportDao.getReportById(report.id)
        if (existing != null) {
            // Idempotently merge paths and relay metadata if record already exists
            val mergedPaths = (existing.localImagePaths + localPaths).distinct()
            val finalOriginUid = if (existing.originUserId.isNotBlank()) existing.originUserId else effectiveOriginUid
            val finalRelayUid = if (existing.relayUserId.isNotBlank()) existing.relayUserId else effectiveRelayUid
            val finalIsRelayed = existing.receivedViaRelay || isRelayed

            val hasUnuploadedMedia = existing.imageUrls.size < mergedPaths.size
            val newSyncStatus = if (hasUnuploadedMedia && existing.syncStatus == SyncStatus.SYNCED) {
                Log.i("RSQ_SYNC", "RSQ_SYNC: MEDIA_PENDING_AFTER_REPORT_SYNC reportId=${report.id} localPathsCount=${mergedPaths.size} uploadedUrlsCount=${existing.imageUrls.size}")
                SyncStatus.LOCAL_ONLY
            } else {
                if (syncStatus != SyncStatus.SYNCED) syncStatus else existing.syncStatus
            }

            val updatedEntity = existing.copy(
                localImagePaths = mergedPaths,
                localImagePath = mergedPaths.firstOrNull() ?: existing.localImagePath,
                originUserId = finalOriginUid,
                originUserName = if (existing.originUserName.isNotBlank()) existing.originUserName else report.effectiveOriginUserName,
                relayUserId = finalRelayUid,
                receivedViaRelay = finalIsRelayed,
                syncStatus = newSyncStatus
            )
            reportDao.insertReport(updatedEntity)
            Log.i(DIAG_LOG, "BOUNDARY_4_REPORT_ENTITY_MERGED: " +
                "reportId=${updatedEntity.id}, " +
                "originNodeId=${updatedEntity.relayDeviceId}, " +
                "originUserId=${updatedEntity.originUserId}, " +
                "relayDeviceId=${updatedEntity.relayDeviceId}, " +
                "relayUserId=${updatedEntity.relayUserId}, " +
                "receivedViaRelay=${updatedEntity.receivedViaRelay}"
            )
            return
        }

        val entity = ReportEntity(
            id = report.id,
            userId = effectiveOriginUid,
            userName = report.userName,
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
            recommendedResources = report.recommendedResources,
            expirationTimestamp = report.expirationTimestamp,
            originUserId = effectiveOriginUid,
            originUserName = report.effectiveOriginUserName,
            originCreatedAt = if (report.originCreatedAt > 0) report.originCreatedAt else report.timestamp,
            relayDeviceId = report.relayDeviceId,
            relayUserId = effectiveRelayUid,
            receivedViaRelay = isRelayed
        )
        reportDao.insertReport(entity)
        Log.i(DIAG_LOG, "BOUNDARY_4_REPORT_ENTITY_CREATED: " +
            "reportId=${entity.id}, " +
            "originNodeId=${entity.relayDeviceId}, " +
            "originUserId=${entity.originUserId}, " +
            "relayDeviceId=${entity.relayDeviceId}, " +
            "relayUserId=${entity.relayUserId}, " +
            "receivedViaRelay=${entity.receivedViaRelay}"
        )
        Log.i(TAG, "LOCAL_REPORT_SAVED: ID=${report.id}, SyncStatus=$syncStatus, originUserId=${entity.originUserId}, receivedViaRelay=${entity.receivedViaRelay}")
    }

    open suspend fun addReceivedMedia(reportId: String, mediaPath: String) {
        val entity = reportDao.getReportById(reportId)
        if (entity != null) {
            val updatedPaths = (entity.localImagePaths + mediaPath).distinct()
            val hasUnuploadedMedia = entity.imageUrls.size < updatedPaths.size

            val newSyncStatus = if (hasUnuploadedMedia && entity.syncStatus == SyncStatus.SYNCED) {
                Log.i("RSQ_SYNC", "RSQ_SYNC: MEDIA_PENDING_AFTER_REPORT_SYNC reportId=$reportId localPathsCount=${updatedPaths.size} uploadedUrlsCount=${entity.imageUrls.size}")
                SyncStatus.LOCAL_ONLY
            } else {
                entity.syncStatus
            }

            val updatedEntity = entity.copy(
                localImagePaths = updatedPaths,
                localImagePath = updatedPaths.firstOrNull() ?: entity.localImagePath,
                syncStatus = newSyncStatus
            )
            reportDao.insertReport(updatedEntity)
            Log.i(TAG, "RECEIVED_MEDIA_ADDED: reportId=$reportId, path=$mediaPath, totalLocalPaths=${updatedPaths.size}, syncStatus=$newSyncStatus")
        } else {
            Log.w(TAG, "Cannot add received media for unknown reportId=$reportId")
        }
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
                imageUrls = imageUrls.distinct(),
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
            entities.map { entity -> entity.toDomain() }
        }
    }

    private fun ReportEntity.toDomain(): Report {
        val effectiveImageUrls = if (imageUrls.isNotEmpty()) imageUrls else localImagePaths
        val effectiveImageUrl = imageUrl ?: localImagePath
        val domainReport = Report(
            id = id,
            userId = userId,
            userName = userName,
            title = title,
            description = description,
            severity = severity,
            status = ReportStatus.fromString(status),
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            imageUrl = effectiveImageUrl,
            imageUrls = effectiveImageUrls,
            isOffline = isOffline,
            aiScore = aiScore,
            detectedHazards = detectedHazards,
            recommendedResources = recommendedResources,
            expirationTimestamp = expirationTimestamp,
            originUserId = originUserId,
            originUserName = originUserName,
            originCreatedAt = originCreatedAt,
            relayDeviceId = relayDeviceId,
            relayUserId = relayUserId,
            receivedViaRelay = receivedViaRelay
        )

        Log.i(DIAG_LOG, "BOUNDARY_6_REPORT_ENTITY_TO_DOMAIN: " +
            "reportId=${domainReport.id}, " +
            "originNodeId=${domainReport.relayDeviceId}, " +
            "originUserId=${domainReport.originUserId}, " +
            "relayDeviceId=${domainReport.relayDeviceId}, " +
            "relayUserId=${domainReport.relayUserId}, " +
            "receivedViaRelay=${domainReport.receivedViaRelay}"
        )
        return domainReport
    }
}
