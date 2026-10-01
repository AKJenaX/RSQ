package com.example.rsq.reporting.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.SyncStatus
import com.example.rsq.reporting.model.OfflineSyncStatus
import com.example.rsq.storage.data.StorageRepository
import com.example.rsq.data.repository.NotificationRepository
import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SyncPermanentException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SyncTransientException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class OfflineUploadResult(val index: Int, val url: String)

data class OfflineSyncProgressState(
    val reportId: String,
    val stage: OfflineSyncStatus,
    val uploadedMediaCount: Int = 0,
    val totalMediaCount: Int = 0,
    val message: String = ""
)

object OfflineSyncCoordinator {
    private const val TAG = "RSQ_OFFLINE_SYNC"
    private val activeSyncMap = ConcurrentHashMap<String, String>() // reportId -> syncInstanceId
    private val wakeChannels = ConcurrentHashMap<String, Channel<Unit>>() // reportId -> Channel
    private val progressFlow = MutableSharedFlow<OfflineSyncProgressState>(extraBufferCapacity = 64)

    fun observeProgress(): Flow<OfflineSyncProgressState> = progressFlow.asSharedFlow()

    fun emitProgress(state: OfflineSyncProgressState) {
        progressFlow.tryEmit(state)
        Log.i(TAG, "RSQ_OFFLINE_SYNC: UI_PROGRESS_EMITTED reportId=${state.reportId} stage=${state.stage} uploaded=${state.uploadedMediaCount}/${state.totalMediaCount} msg=${state.message}")
    }

    fun tryAcquire(reportId: String, syncInstanceId: String): Boolean {
        val acquired = activeSyncMap.putIfAbsent(reportId, syncInstanceId) == null
        if (acquired) {
            wakeChannels.putIfAbsent(reportId, Channel(Channel.UNLIMITED))
        }
        return acquired
    }

    fun getActiveInstanceId(reportId: String): String? {
        return activeSyncMap[reportId]
    }

    fun getWakeChannel(reportId: String): Channel<Unit>? {
        return wakeChannels[reportId]
    }

    fun wakeSync(reportId: String) {
        val channel = wakeChannels[reportId]
        if (channel != null) {
            channel.trySend(Unit)
            Log.i(TAG, "RSQ_OFFLINE_SYNC: WAKE_SIGNAL_SENT reportId=$reportId")
        }
    }

    fun triggerSync(
        context: Context,
        reportId: String,
        scope: CoroutineScope,
        localReportRepo: LocalReportRepository
    ) {
        val activeInstanceId = getActiveInstanceId(reportId)
        if (activeInstanceId != null) {
            Log.i(TAG, "RSQ_OFFLINE_SYNC: SYNC_ALREADY_RUNNING reportId=$reportId activeSyncInstanceId=$activeInstanceId")
            wakeSync(reportId)
            return
        }

        // Schedule WorkManager as durable recovery
        SyncScheduler.scheduleSync(context)

        // Launch in-memory sync
        scope.launch(Dispatchers.IO) {
            try {
                val storageRepo = StorageRepository()
                val cloudRepo = ReportRepository()
                val syncManager = ReportSyncManager(context, localReportRepo, cloudRepo, storageRepo)
                syncManager.syncReport(reportId)
            } catch (t: Throwable) {
                Log.w(TAG, "Triggered sync exception for $reportId: ${t.message}")
            }
        }
    }

    fun release(reportId: String, syncInstanceId: String) {
        if (activeSyncMap.remove(reportId, syncInstanceId)) {
            val channel = wakeChannels.remove(reportId)
            channel?.close()
            Log.i(TAG, "RSQ_OFFLINE_SYNC: SYNC_RELEASED reportId=$reportId syncInstanceId=$syncInstanceId")
        }
    }
}

class ReportSyncManager(
    private val context: Context,
    private val localRepository: LocalReportRepository,
    private val cloudRepository: ReportRepository,
    private val storageRepository: StorageRepository,
    private val notificationRepository: NotificationRepository? = null
) {
    private val TAG = "RSQ_SYNC"
    private val OFFLINE_TAG = "RSQ_OFFLINE_SYNC"
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
        val syncInstanceId = UUID.randomUUID().toString().take(8)

        // SINGLE-FLIGHT CONCURRENCY GATE
        if (!OfflineSyncCoordinator.tryAcquire(reportId, syncInstanceId)) {
            val activeInstanceId = OfflineSyncCoordinator.getActiveInstanceId(reportId)
            Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: SYNC_ALREADY_RUNNING reportId=$reportId activeSyncInstanceId=$activeInstanceId attemptSyncInstanceId=$syncInstanceId")
            OfflineSyncCoordinator.wakeSync(reportId)
            return Result.success(Unit)
        }

        val syncStartMs = System.currentTimeMillis()
        Log.i(TAG, "RSQ_SYNC: REPORT_SYNC_START reportId=$reportId syncInstanceId=$syncInstanceId")
        Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: SYNC_START reportId=$reportId syncInstanceId=$syncInstanceId timestamp=$syncStartMs")

        try {
            val currentAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (t: Throwable) { "" }
            val entity = localRepository.getReportById(reportId)
                ?: run {
                    Log.e(TAG, "RSQ_SYNC: SYNC_PERMANENT_FAILURE reportId=$reportId syncInstanceId=$syncInstanceId reason=Local record missing")
                    OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(reportId, OfflineSyncStatus.FAILED, 0, 0, "Record missing"))
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
                Log.e(TAG, "RSQ_SYNC: SYNC_PERMANENT_FAILURE reportId=$reportId syncInstanceId=$syncInstanceId reason=No Authenticated User")
                OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(reportId, OfflineSyncStatus.FAILED, 0, 0, "Not authenticated"))
                return Result.failure(SyncPermanentException("Not authenticated"))
            }

            localRepository.updateSyncStatus(reportId, SyncStatus.SYNCING)
            OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(reportId, OfflineSyncStatus.FIREBASE_SYNCING, 0, entity.localImagePaths.size, "Syncing with server..."))

            var loopCount = 0
            val maxLoops = 10
            var hasSubmittedFirestoreAtLeastOnce = false

            while (loopCount < maxLoops) {
                loopCount++

                val freshCurrentEntity = localRepository.getReportById(reportId) ?: entity
                val currentImageUrls = freshCurrentEntity.imageUrls.distinct().toMutableList()
                val localPaths = freshCurrentEntity.localImagePaths.distinct()

                val pendingMediaCount = localPaths.size - currentImageUrls.size
                Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: MEDIA_DISCOVERY reportId=$reportId syncInstanceId=$syncInstanceId pendingMediaCount=$pendingMediaCount uploadedMediaCount=${currentImageUrls.size} localPathsCount=${localPaths.size}")

                val mediaStartMs = System.currentTimeMillis()
                var newMediaUploaded = false

                // Step 1: Upload Images in Parallel using CONTROLLED CONCURRENCY (Semaphore(3))
                if (currentImageUrls.size < localPaths.size) {
                    onProgress?.invoke(SyncProgress.UPLOADING_EVIDENCE)
                    val missingIndices = (currentImageUrls.size until localPaths.size).toList()
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: MEDIA_UPLOAD_START reportId=$reportId syncInstanceId=$syncInstanceId count=${missingIndices.size}")

                    OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(
                        reportId,
                        OfflineSyncStatus.MEDIA_UPLOADING,
                        currentImageUrls.size,
                        localPaths.size,
                        "Uploading photos"
                    ))

                    val semaphore = Semaphore(3)
                    val uploadDeferreds = coroutineScope {
                        missingIndices.map { index ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    val rawPath = localPaths[index]
                                    val rawFile = File(rawPath)
                                    if (!rawFile.exists()) {
                                        Log.e(TAG, "RSQ_SYNC: SYNC_PERMANENT_FAILURE reportId=$reportId syncInstanceId=$syncInstanceId mediaIndex=$index reason=Local file missing at $rawPath")
                                        throw SyncPermanentException("Local file missing at $rawPath")
                                    }

                                    val optFile = prepareOptimizedFile(rawFile)
                                    val uploadResult = storageRepository.uploadImage(Uri.fromFile(optFile), reportId, index)
                                    if (uploadResult.isSuccess) {
                                        val url = uploadResult.getOrThrow()
                                        Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: MEDIA_UPLOAD_SUCCESS reportId=$reportId syncInstanceId=$syncInstanceId index=$index url=$url")
                                        OfflineUploadResult(index, url)
                                    } else {
                                        val e = uploadResult.exceptionOrNull()
                                        Log.e(TAG, "RSQ_SYNC: MEDIA_UPLOAD_FAILED reportId=$reportId syncInstanceId=$syncInstanceId index=$index reason=${e?.message}")
                                        throw e ?: Exception("Upload failed at index $index")
                                    }
                                }
                            }
                        }
                    }

                    val newResults: List<OfflineUploadResult> = try {
                        uploadDeferreds.awaitAll()
                    } catch (e: Exception) {
                        localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                        OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(reportId, OfflineSyncStatus.FAILED, currentImageUrls.size, localPaths.size, "Upload pending. Safely stored locally."))
                        val exceptionToReturn = if (isPermanentError(e)) {
                            e as? SyncPermanentException ?: SyncPermanentException("Storage upload failed permanently: ${e.message}", e)
                        } else {
                            SyncTransientException("Storage upload failed transiently: ${e.message}", e)
                        }
                        return Result.failure(exceptionToReturn)
                    }

                    // Append newly uploaded URLs in correct index order
                    newResults.sortedBy { it.index }.forEach { item ->
                        if (!currentImageUrls.contains(item.url)) {
                            currentImageUrls.add(item.url)
                            newMediaUploaded = true
                        }
                    }

                    localRepository.updateImageUrls(reportId, currentImageUrls, SyncStatus.SYNCING)
                    val mediaUploadDurationMs = System.currentTimeMillis() - mediaStartMs
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: MEDIA_UPLOAD_COMPLETED reportId=$reportId syncInstanceId=$syncInstanceId totalImageUrls=${currentImageUrls.size} mediaUploadDurationMs=$mediaUploadDurationMs")

                    OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(
                        reportId,
                        OfflineSyncStatus.MEDIA_UPLOADING,
                        currentImageUrls.size,
                        localPaths.size,
                        "Uploading photos"
                    ))
                }

                // Step 2: Cloud Firestore Submission (Execute ONLY if first run OR if new media was uploaded)
                val shouldWriteFirestore = !hasSubmittedFirestoreAtLeastOnce || newMediaUploaded
                if (shouldWriteFirestore) {
                    onProgress?.invoke(SyncProgress.CREATING_CLOUD_REPORT)
                    val firestoreStartMs = System.currentTimeMillis()
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: FINAL_FIRESTORE_WRITE reportId=$reportId syncInstanceId=$syncInstanceId imageUrlsCount=${currentImageUrls.size}")

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

                    val cloudResult = cloudRepository.submitReport(domainReport)
                    val firestoreDurationMs = System.currentTimeMillis() - firestoreStartMs

                    if (!cloudResult.isSuccess) {
                        val error = cloudResult.exceptionOrNull()
                        Log.e(TAG, "RSQ_SYNC: FIRESTORE_WRITE_FAILED reportId=$reportId syncInstanceId=$syncInstanceId reason=${error?.message}")
                        localRepository.updateSyncStatus(reportId, SyncStatus.FAILED)
                        OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(reportId, OfflineSyncStatus.FAILED, currentImageUrls.size, localPaths.size, "Upload pending. Safely stored locally."))
                        val exceptionToReturn = if (isPermanentError(error)) {
                            error ?: SyncPermanentException("Firestore submission failed permanently")
                        } else {
                            SyncTransientException("Firestore submission failed transiently: ${error?.message}", error)
                        }
                        return Result.failure(exceptionToReturn)
                    }

                    hasSubmittedFirestoreAtLeastOnce = true
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: FIRESTORE_WRITE_SUCCESS reportId=$reportId syncInstanceId=$syncInstanceId firestoreDurationMs=$firestoreDurationMs")
                } else {
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: NO_NEW_MEDIA_SKIP reportId=$reportId syncInstanceId=$syncInstanceId imageUrlsCount=${currentImageUrls.size}")
                }

                // Step 3: MEDIA_SYNC_RECHECK - Re-read Room DB to check if new media arrived DURING sync
                val freshEntity = localRepository.getReportById(reportId)
                val freshLocalPaths = freshEntity?.localImagePaths?.distinct() ?: emptyList()

                if (freshLocalPaths.size > currentImageUrls.size) {
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: MEDIA_SYNC_RECHECK reportId=$reportId syncInstanceId=$syncInstanceId newLocalPathsCount=${freshLocalPaths.size} uploadedUrlsCount=${currentImageUrls.size} - Repeating loop to upload newly arrived media")
                    continue
                }

                // Step 4: If media may still be transferring over mesh, wait on WakeChannel up to 3 seconds for next photo payload
                val wakeChannel = OfflineSyncCoordinator.getWakeChannel(reportId)
                if (wakeChannel != null) {
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: WAITING_FOR_LATE_MEDIA reportId=$reportId syncInstanceId=$syncInstanceId currentImageUrlsCount=${currentImageUrls.size}")
                    val waitTimeoutMs = if (SyncScheduler.isTestMode) 0L else 3000L
                    val wakeEvent = withTimeoutOrNull(waitTimeoutMs) {
                        wakeChannel.receive()
                    }
                    if (wakeEvent != null) {
                        val postWakeEntity = localRepository.getReportById(reportId)
                        val postWakeLocalPaths = postWakeEntity?.localImagePaths?.distinct() ?: emptyList()

                        if (postWakeLocalPaths.size > currentImageUrls.size) {
                            Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: WOKEN_BY_NEW_MEDIA reportId=$reportId syncInstanceId=$syncInstanceId newLocalPathsCount=${postWakeLocalPaths.size}")
                            continue // New unuploaded media present, loop back to upload!
                        } else {
                            Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: NO_NEW_MEDIA_SKIP reportId=$reportId syncInstanceId=$syncInstanceId")
                        }
                    }
                }

                // Step 5: Final verification
                val finalRecheckEntity = localRepository.getReportById(reportId)
                val finalRecheckPaths = finalRecheckEntity?.localImagePaths?.distinct() ?: emptyList()
                val finalPendingCount = finalRecheckPaths.size - currentImageUrls.size

                Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: FINAL_MEDIA_STATE reportId=$reportId syncInstanceId=$syncInstanceId localPathsCount=${finalRecheckPaths.size} uploadedUrlsCount=${currentImageUrls.size}")

                if (finalPendingCount > 0) {
                    Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: MEDIA_SYNC_RECHECK reportId=$reportId syncInstanceId=$syncInstanceId finalPendingCount=$finalPendingCount")
                    continue
                }

                // ALL MEDIA UPLOADED AND VERIFIED!
                Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: FINAL_PENDING_MEDIA_CHECK reportId=$reportId syncInstanceId=$syncInstanceId pendingMediaCount=0 uploadedMediaCount=${currentImageUrls.size}")

                localRepository.updateSyncStatus(reportId, SyncStatus.SYNCED)
                val totalOfflineSyncDurationMs = System.currentTimeMillis() - syncStartMs

                Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: REPORT_MARKED_SYNCED reportId=$reportId syncInstanceId=$syncInstanceId")
                Log.i(OFFLINE_TAG, "RSQ_OFFLINE_SYNC: SYNC_SUCCESS reportId=$reportId syncInstanceId=$syncInstanceId totalOfflineFirebaseSyncDurationMs=$totalOfflineSyncDurationMs pendingMediaCount=0 uploadedMediaCount=${currentImageUrls.size}")
                Log.i(TAG, "RSQ_SYNC: REPORT_MARKED_SYNCED reportId=$reportId")
                Log.i(TAG, "RSQ_SYNC: SYNC_SUCCESS reportId=$reportId")

                OfflineSyncCoordinator.emitProgress(OfflineSyncProgressState(
                    reportId,
                    OfflineSyncStatus.SYNCED,
                    currentImageUrls.size,
                    currentImageUrls.size,
                    "Report uploaded successfully"
                ))

                // Cleanup local temporary image files only after full Firestore & media success
                for (path in finalRecheckPaths) {
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) Log.d(TAG, "RSQ_SYNC: Cleanup deleted local file $path")
                    }
                }

                // Notification for offline sync
                if (originUid.isNotBlank() && entity.isOffline) {
                    notificationRepository?.addNotification(
                        Notification(
                            id = UUID.randomUUID().toString(),
                            recipientId = originUid,
                            title = "Report Synced",
                            message = "Offline report ${entity.title} successfully synced to cloud.",
                            timestamp = "Just now",
                            type = NotificationType.SOS_ALERT,
                            isRead = false,
                            associatedReportId = reportId
                        )
                    )
                }

                break
            }

            return Result.success(Unit)

        } finally {
            OfflineSyncCoordinator.release(reportId, syncInstanceId)
        }
    }

    private fun prepareOptimizedFile(rawFile: File): File {
        if (rawFile.length() < 300 * 1024) return rawFile // Already small enough

        return try {
            val destFile = File(context.filesDir, "opt_${rawFile.name}")
            if (destFile.exists() && destFile.length() > 0) return destFile

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(rawFile.absolutePath, options)

            val maxDimension = 1280
            var sampleSize = 1
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val halfW = options.outWidth / 2
                val halfH = options.outHeight / 2
                while ((halfW / sampleSize) >= maxDimension && (halfH / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(rawFile.absolutePath, decodeOpts) ?: return rawFile

            val scaled = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / Math.max(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }

            destFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            destFile
        } catch (e: Exception) {
            Log.w(TAG, "Offline image optimization fallback for ${rawFile.name}: ${e.message}")
            rawFile
        }
    }

    enum class SyncProgress {
        UPLOADING_EVIDENCE,
        CREATING_CLOUD_REPORT
    }
}
