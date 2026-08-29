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
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class ReportSyncManager(
    private val context: Context,
    private val localRepository: LocalReportRepository,
    private val cloudRepository: ReportRepository,
    private val storageRepository: StorageRepository
) {
    private val TAG = "RSQ_IMAGE_SYNC"

    suspend fun syncPendingReports(): Result<Unit> {
        val pending = localRepository.getPendingReports()
        if (pending.isEmpty()) {
            return Result.success(Unit)
        }

        Log.i(TAG, "CLOUD_SYNC_STARTED_BATCH: Discovered ${pending.size} reports requiring sync")
        var overallSuccess = true

        for (entity in pending) {
            val result = syncReport(entity.id)
            if (result.isFailure) {
                overallSuccess = false
                Log.w(TAG, "SYNC_RETRY_SCHEDULED: Report ${entity.id} failed Batch Sync attempt")
            }
        }

        return if (overallSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Some reports failed to sync"))
        }
    }

    suspend fun syncReport(reportId: String, onProgress: ((SyncProgress) -> Unit)? = null): Result<Unit> {
        Log.i(TAG, "REPORT_SYNC_START: reportId=$reportId")

        val authUid = FirebaseAuth.getInstance().currentUser?.uid
        if (authUid == null) {
            Log.e(TAG, "REPORT_SYNC_FAILED: reportId=$reportId, reason=No Authenticated User")
            return Result.failure(Exception("Not authenticated"))
        }

        val entity = localRepository.getReportById(reportId)
            ?: run {
                Log.e(TAG, "REPORT_SYNC_FAILED: reportId=$reportId, reason=Local record missing")
                return Result.failure(Exception("Report not found"))
            }

        Log.d(TAG, "REPORT_SYNC_PROCESSING: ID=$reportId, LocalPathsCount=${entity.localImagePaths.size}")
        localRepository.updateSyncStatus(reportId, SyncStatus.SYNCING)

        try {
            val currentImageUrls = entity.imageUrls.toMutableList()

            // Step 1: Upload Images if we have local paths but fewer URLs than paths
            if (currentImageUrls.size < entity.localImagePaths.size) {
                onProgress?.invoke(SyncProgress.UPLOADING_EVIDENCE)
                Log.i(TAG, "STORAGE_STAGE_START: reportId=$reportId, paths=${entity.localImagePaths.size}, existingUrls=${currentImageUrls.size}")

                var allUploaded = true
                // Start from the first missing image
                for (index in currentImageUrls.size until entity.localImagePaths.size) {
                    val path = entity.localImagePaths[index]
                    val file = File(path)
                    if (file.exists()) {
                        val uploadResult = storageRepository.uploadImage(Uri.fromFile(file), reportId, index)
                        if (uploadResult.isSuccess) {
                            val downloadUrl = uploadResult.getOrThrow()
                            currentImageUrls.add(downloadUrl)
                            Log.i(TAG, "STORAGE_UPLOAD_SUCCESS_MANAGER: index=$index, URL=$downloadUrl")
                            // Update local URLs immediately after each successful upload to allow resuming
                            localRepository.updateImageUrls(reportId, currentImageUrls, SyncStatus.SYNCING)
                        } else {
                            val e = uploadResult.exceptionOrNull()
                            Log.e(TAG, "STORAGE_STAGE_FAILED: index=$index, reason=${e?.message}")
                            allUploaded = false
                            break
                        }
                    } else {
                        Log.e(TAG, "STORAGE_STAGE_FAILED: index=$index, reason=Local file missing at $path")
                        allUploaded = false
                        break
                    }
                }

                if (!allUploaded) {
                    localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                    Log.e(TAG, "REPORT_SYNC_FAILED_STORAGE: $reportId")
                    return Result.failure(Exception("Image upload failed"))
                }
            }

            // Step 2: Firestore Write
            onProgress?.invoke(SyncProgress.CREATING_CLOUD_REPORT)
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
                imageUrl = currentImageUrls.firstOrNull(),
                imageUrls = currentImageUrls,
                isOffline = entity.isOffline,
                aiScore = entity.aiScore,
                detectedHazards = entity.detectedHazards,
                recommendedResources = entity.recommendedResources
            )

            Log.i(TAG, "FIRESTORE_WRITE_START: $reportId, imageUrlsCount=${currentImageUrls.size}")
            val cloudResult = cloudRepository.submitReport(domainReport)

            if (cloudResult.isSuccess) {
                Log.i(TAG, "FIRESTORE_WRITE_SUCCESS: $reportId")
                localRepository.updateSyncStatus(reportId, SyncStatus.SYNCED)
                Log.i(TAG, "SYNC_STATUS_UPDATED_TO_SYNCED: $reportId")
                Log.i(TAG, "REPORT_SYNC_SUCCESS: $reportId")

                // Cleanup local files only after full Firestore success
                for (path in entity.localImagePaths) {
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) Log.d(TAG, "Cleanup: Deleted $path")
                    }
                }
                return Result.success(Unit)
            } else {
                val error = cloudResult.exceptionOrNull()
                Log.e(TAG, "FIRESTORE_WRITE_FAILED: $reportId, type=${error?.javaClass?.simpleName}, message=${error?.message}")
                localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                Log.e(TAG, "REPORT_SYNC_FAILED_FIRESTORE: $reportId")
                return Result.failure(error ?: Exception("Firestore submission failed"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "REPORT_SYNC_FAILED_UNEXPECTED: $reportId, type=${e.javaClass.simpleName}, message=${e.message}")
            Log.e(TAG, "REPORT_SYNC_FAILED_UNEXPECTED: stackTrace=${Log.getStackTraceString(e)}")
            localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
            return Result.failure(e)
        }
    }

    enum class SyncProgress {
        UPLOADING_EVIDENCE,
        CREATING_CLOUD_REPORT
    }
}
