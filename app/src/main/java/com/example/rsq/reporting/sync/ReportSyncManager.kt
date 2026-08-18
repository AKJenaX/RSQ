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
        if (pending.isEmpty()) {
            Log.i(TAG, "No pending reports to synchronize")
            return Result.success(Unit)
        }

        Log.i(TAG, "ReportSyncWorker STARTED: Discovered ${pending.size} reports requiring sync")
        var overallSuccess = true

        for (entity in pending) {
            Log.i(TAG, "Processing report: ${entity.id} (Current status: ${entity.syncStatus})")
            val result = syncReport(entity.id)
            if (result.isFailure) {
                overallSuccess = false
                Log.e(TAG, "Failed to sync report ${entity.id}: ${result.exceptionOrNull()?.javaClass?.simpleName} - ${result.exceptionOrNull()?.message}")
            }
        }

        return if (overallSuccess) {
            Log.i(TAG, "Sync sequence COMPLETED successfully")
            Result.success(Unit)
        } else {
            Log.e(TAG, "Sync sequence completed with errors")
            Result.failure(Exception("Some reports failed to sync"))
        }
    }

    private suspend fun syncReport(reportId: String): Result<Unit> {
        val entity = localRepository.getReportById(reportId) 
            ?: return Result.failure(Exception("Report not found"))

        // 1. Mark SYNCING (State Machine Entry)
        Log.d(TAG, "Transitioning to SYNCING: $reportId")
        localRepository.updateSyncStatus(reportId, SyncStatus.SYNCING)

        try {
            // 2. Upload Image if exists locally but not in cloud
            var currentImageUrl = entity.imageUrl
            if (currentImageUrl == null && entity.localImagePath != null) {
                val file = File(entity.localImagePath)
                if (file.exists()) {
                    Log.i(TAG, "Image upload STARTED: $reportId")
                    val uploadResult = storageRepository.uploadImage(Uri.fromFile(file))
                    if (uploadResult.isSuccess) {
                        currentImageUrl = uploadResult.getOrNull()
                        Log.i(TAG, "Image upload SUCCEEDED: $reportId")
                        // Update local record with URL immediately to avoid re-uploading on retry
                        localRepository.updateImageUrl(reportId, currentImageUrl!!, SyncStatus.SYNCING)
                    } else {
                        val error = uploadResult.exceptionOrNull()
                        Log.e(TAG, "Image upload FAILED for $reportId: ${error?.message}")
                        localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                        return Result.failure(error ?: Exception("Image upload failed"))
                    }
                } else {
                    Log.w(TAG, "Local image file missing for $reportId at path: ${entity.localImagePath}")
                }
            }

            // 3. Upload to Firestore (Idempotent using original ID)
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

            Log.i(TAG, "Firestore upload STARTED: $reportId")
            val cloudResult = cloudRepository.submitReport(domainReport)
            if (cloudResult.isSuccess) {
                Log.i(TAG, "Firestore upload SUCCEEDED: $reportId")
                
                // 4. Mark SYNCED (State Machine Terminal)
                localRepository.updateSyncStatus(reportId, SyncStatus.SYNCED)
                Log.i(TAG, "Transitioned to SYNCED: $reportId")
                
                // Clean up local image if synced
                entity.localImagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(TAG, "Temporary local image deleted: $path")
                        } else {
                            Log.w(TAG, "Failed to delete temporary image: $path")
                        }
                    }
                }
                return Result.success(Unit)
            } else {
                val error = cloudResult.exceptionOrNull()
                Log.e(TAG, "Firestore upload FAILED for $reportId: ${error?.message}")
                localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                return Result.failure(error ?: Exception("Cloud submission failed"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected ERROR syncing $reportId: ${e.message}")
            localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
            return Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ReportSyncManager"
    }
}
