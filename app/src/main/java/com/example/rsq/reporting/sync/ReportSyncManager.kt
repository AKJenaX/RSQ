package com.example.rsq.reporting.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.SyncStatus
import com.example.rsq.storage.data.StorageRepository
import java.io.File

class ReportSyncManager(
    private val context: Context,
    private val localRepository: LocalReportRepository,
    private val cloudRepository: ReportRepository,
    private val storageRepository: StorageRepository
) {
    suspend fun syncPendingReports(): Result<Unit> {
        val pending = localRepository.getPendingReports()
        if (pending.isEmpty()) return Result.success(Unit)

        // Log.i(TAG, "Starting sync for ${pending.size} pending reports")
        var overallSuccess = true

        for (entity in pending) {
            val result = syncReport(entity.id)
            if (result.isFailure) {
                overallSuccess = false
                // Log.e(TAG, "Failed to sync report ${entity.id}: ${result.exceptionOrNull()?.message}")
            }
        }

        return if (overallSuccess) Result.success(Unit) else Result.failure(Exception("Some reports failed to sync"))
    }

    private suspend fun syncReport(reportId: String): Result<Unit> {
        // 1. Mark SYNCING
        localRepository.updateSyncStatus(reportId, SyncStatus.SYNCING)

        val entity = localRepository.getPendingReports().find { it.id == reportId } 
            ?: return Result.failure(Exception("Report not found"))

        try {
            // 2. Upload Image if exists locally but not in cloud
            var currentImageUrl = entity.imageUrl
            if (currentImageUrl == null && entity.localImagePath != null) {
                val file = File(entity.localImagePath)
                if (file.exists()) {
                    val uploadResult = storageRepository.uploadImage(Uri.fromFile(file))
                    if (uploadResult.isSuccess) {
                        currentImageUrl = uploadResult.getOrNull()
                        // Update local record with URL immediately
                        localRepository.updateImageUrl(reportId, currentImageUrl!!, SyncStatus.SYNCING)
                    } else {
                        localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                        return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Image upload failed"))
                    }
                }
            }

            // 3. Upload to Firestore (Idempotent)
            val domainReport = Report(
                id = entity.id,
                userId = entity.userId,
                title = entity.title,
                description = entity.description,
                severity = entity.severity,
                status = ReportStatus.fromString(entity.status),
                timestamp = entity.timestamp,
                latitude = entity.latitude,
                longitude = entity.longitude,
                imageUrl = currentImageUrl,
                isOffline = entity.isOffline
            )

            val cloudResult = cloudRepository.submitReport(domainReport)
            if (cloudResult.isSuccess) {
                localRepository.updateSyncStatus(reportId, SyncStatus.SYNCED)
                // Clean up local image if synced
                entity.localImagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                return Result.success(Unit)
            } else {
                localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                return Result.failure(cloudResult.exceptionOrNull() ?: Exception("Cloud submission failed"))
            }

        } catch (e: Exception) {
            localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
            return Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ReportSyncManager"
    }
}
