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
import com.example.rsq.data.repository.NotificationRepository
import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import java.io.File

class SyncPermanentException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SyncTransientException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ReportSyncManager(
    private val context: Context,
    private val localRepository: LocalReportRepository,
    private val cloudRepository: ReportRepository,
    private val storageRepository: StorageRepository,
    private val notificationRepository: NotificationRepository? = null
) {
    private val TAG = "RSQ_SYNC"
    private val DIAG_LOG = "RSQ_DIAGNOSTIC"

    fun isPermanentError(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is SyncPermanentException) return true
        if (e is SyncTransientException) return false

        return when (e) {
            is IllegalArgumentException,
            is IllegalStateException,
            is SecurityException -> true
            else -> {
                val msg = e.message ?: ""
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                msg.contains("UNAUTHENTICATED", ignoreCase = true) ||
                msg.contains("Local record missing", ignoreCase = true) ||
                msg.contains("Local file missing", ignoreCase = true) ||
                msg.contains("Not authenticated", ignoreCase = true)
            }
        }
    }

    suspend fun syncPendingReports(): Result<Unit> {
        val pending = localRepository.getPendingReports()
        if (pending.isEmpty()) {
            return Result.success(Unit)
        }

        Log.i(TAG, "RSQ_SYNC: PENDING_REPORTS_DISCOVERED count=${pending.size}")
        var hasTransientFailure = false
        var lastTransientError: Throwable? = null
        var permanentFailureCount = 0

        for (entity in pending) {
            val result = syncReport(entity.id)
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                if (isPermanentError(err)) {
                    permanentFailureCount++
                    Log.e(TAG, "RSQ_SYNC: SYNC_PERMANENT_FAILURE reportId=${entity.id} reason=${err?.message}")
                } else {
                    hasTransientFailure = true
                    lastTransientError = err
                    Log.w(TAG, "RSQ_SYNC: SYNC_RETRY reportId=${entity.id} reason=${err?.message}")
                }
            }
        }

        return if (!hasTransientFailure) {
            if (permanentFailureCount > 0 && pending.size == permanentFailureCount) {
                Result.failure(SyncPermanentException("All pending reports failed permanently"))
            } else {
                Result.success(Unit)
            }
        } else {
            Result.failure(lastTransientError ?: SyncTransientException("Transient synchronization failure occurred"))
        }
    }

    suspend fun syncReport(reportId: String, onProgress: ((SyncProgress) -> Unit)? = null): Result<Unit> {
        Log.i(TAG, "RSQ_SYNC: REPORT_SYNC_START reportId=$reportId")

        val currentAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (t: Throwable) { "" }
        val entity = localRepository.getReportById(reportId)
            ?: run {
                Log.e(TAG, "RSQ_SYNC: SYNC_PERMANENT_FAILURE reportId=$reportId reason=Local record missing")
                return Result.failure(SyncPermanentException("Local record missing for $reportId"))
            }

        Log.i(DIAG_LOG, "BOUNDARY_5_ROOM_ENTITY_READ_BACK: " +
            "reportId=${entity.id}, " +
            "originNodeId=${entity.relayDeviceId}, " +
            "originUserId=${entity.originUserId}, " +
            "relayDeviceId=${entity.relayDeviceId}, " +
            "relayUserId=${entity.relayUserId}, " +
            "receivedViaRelay=${entity.receivedViaRelay}"
        )

        val originUid = if (entity.originUserId.isNotBlank()) entity.originUserId else entity.userId
        val originName = if (entity.originUserName.isNotBlank()) entity.originUserName else entity.userName
        val originTime = if (entity.originCreatedAt > 0) entity.originCreatedAt else entity.timestamp
        val isRelayed = entity.receivedViaRelay || (originUid.isNotBlank() && currentAuthUid.isNotBlank() && originUid != currentAuthUid)
        val relayUid = if (entity.relayUserId.isNotBlank()) entity.relayUserId else if (isRelayed) currentAuthUid else ""

        Log.i(DIAG_LOG, "BOUNDARY_7_START_SYNC_REPORT: " +
            "reportId=$reportId, " +
            "originNodeId=${entity.relayDeviceId}, " +
            "originUserId=$originUid, " +
            "relayDeviceId=${entity.relayDeviceId}, " +
            "relayUserId=$relayUid, " +
            "receivedViaRelay=$isRelayed"
        )

        val authUid = if (originUid.isNotBlank()) originUid else currentAuthUid
        if (authUid.isBlank()) {
            Log.e(TAG, "RSQ_SYNC: SYNC_PERMANENT_FAILURE reportId=$reportId reason=No Authenticated User")
            return Result.failure(SyncPermanentException("Not authenticated"))
        }

        localRepository.updateSyncStatus(reportId, SyncStatus.SYNCING)

        try {
            val currentImageUrls = entity.imageUrls.toMutableList()

            // Step 1: Upload Images if local paths exist and haven't been uploaded yet
            if (currentImageUrls.size < entity.localImagePaths.size) {
                onProgress?.invoke(SyncProgress.UPLOADING_EVIDENCE)
                Log.i(TAG, "RSQ_SYNC: MEDIA_UPLOAD_START reportId=$reportId localPathsCount=${entity.localImagePaths.size} existingUrlsCount=${currentImageUrls.size}")

                for (index in currentImageUrls.size until entity.localImagePaths.size) {
                    val path = entity.localImagePaths[index]
                    val file = File(path)
                    if (!file.exists()) {
                        Log.e(TAG, "RSQ_SYNC: SYNC_PERMANENT_FAILURE reportId=$reportId mediaIndex=$index reason=Local file missing at $path")
                        localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                        return Result.failure(SyncPermanentException("Local file missing at $path"))
                    }

                    val uploadResult = storageRepository.uploadImage(Uri.fromFile(file), reportId, index)
                    if (uploadResult.isSuccess) {
                        val downloadUrl = uploadResult.getOrThrow()
                        currentImageUrls.add(downloadUrl)
                        Log.i(TAG, "RSQ_SYNC: MEDIA_UPLOAD_SUCCESS reportId=$reportId index=$index url=$downloadUrl")
                        // Update Room immediately after each successful image upload (Idempotency Resume Guard)
                        localRepository.updateImageUrls(reportId, currentImageUrls, SyncStatus.SYNCING)
                    } else {
                        val e = uploadResult.exceptionOrNull()
                        Log.e(TAG, "RSQ_SYNC: MEDIA_UPLOAD_FAILED reportId=$reportId index=$index reason=${e?.message}")
                        localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                        val exceptionToReturn = if (isPermanentError(e)) {
                            e ?: SyncPermanentException("Storage upload failed permanently")
                        } else {
                            SyncTransientException("Storage upload failed transiently: ${e?.message}", e)
                        }
                        return Result.failure(exceptionToReturn)
                    }
                }
            }

            // Step 2: Cloud Firestore Submission
            onProgress?.invoke(SyncProgress.CREATING_CLOUD_REPORT)
            val domainReport = Report(
                id = entity.id,
                userId = originUid,
                userName = originName,
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
                recommendedResources = entity.recommendedResources,
                expirationTimestamp = entity.expirationTimestamp,
                originUserId = originUid,
                originUserName = originName,
                originCreatedAt = originTime,
                relayDeviceId = entity.relayDeviceId,
                relayUserId = relayUid,
                receivedViaRelay = isRelayed
            )

            Log.i(DIAG_LOG, "BOUNDARY_8_BEFORE_SUBMIT_REPORT: " +
                "reportId=${domainReport.id}, " +
                "originNodeId=${domainReport.relayDeviceId}, " +
                "originUserId=${domainReport.originUserId}, " +
                "relayDeviceId=${domainReport.relayDeviceId}, " +
                "relayUserId=${domainReport.relayUserId}, " +
                "receivedViaRelay=${domainReport.receivedViaRelay}"
            )

            Log.i(TAG, "RSQ_SYNC: FIRESTORE_WRITE_START reportId=$reportId imageUrlsCount=${currentImageUrls.size}")
            val cloudResult = cloudRepository.submitReport(domainReport)

            if (cloudResult.isSuccess) {
                Log.i(TAG, "RSQ_SYNC: FIRESTORE_WRITE_SUCCESS reportId=$reportId")
                localRepository.updateSyncStatus(reportId, SyncStatus.SYNCED)
                Log.i(TAG, "RSQ_SYNC: REPORT_MARKED_SYNCED reportId=$reportId")
                Log.i(TAG, "RSQ_SYNC: SYNC_SUCCESS reportId=$reportId")

                // Cleanup local temporary image files only after Firestore write succeeds
                for (path in entity.localImagePaths) {
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) Log.d(TAG, "RSQ_SYNC: Cleanup deleted local file $path")
                    }
                }

                // Notification for offline sync
                val uid = entity.userId
                if (uid.isNotBlank() && entity.isOffline) {
                    notificationRepository?.addNotification(
                        Notification(
                            id = UUID.randomUUID().toString(),
                            recipientId = uid,
                            title = "Report Synced",
                            message = "Offline report ${entity.title} successfully synced to cloud.",
                            timestamp = "Just now",
                            type = NotificationType.SOS_ALERT,
                            isRead = false,
                            associatedReportId = reportId
                        )
                    )
                }

                return Result.success(Unit)
            } else {
                val error = cloudResult.exceptionOrNull()
                Log.e(TAG, "RSQ_SYNC: FIRESTORE_WRITE_FAILED reportId=$reportId reason=${error?.message}")
                localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                val exceptionToReturn = if (isPermanentError(error)) {
                    error ?: SyncPermanentException("Firestore submission failed permanently")
                } else {
                    SyncTransientException("Firestore submission failed transiently: ${error?.message}", error)
                }
                return Result.failure(exceptionToReturn)
            }

        } catch (e: Exception) {
            Log.e(TAG, "RSQ_SYNC: SYNC_FAILED_UNEXPECTED reportId=$reportId error=${e.message}")
            localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
            val exceptionToReturn = if (isPermanentError(e)) e else SyncTransientException("Unexpected transient failure: ${e.message}", e)
            return Result.failure(exceptionToReturn)
        }
    }

    enum class SyncProgress {
        UPLOADING_EVIDENCE,
        CREATING_CLOUD_REPORT
    }
}
