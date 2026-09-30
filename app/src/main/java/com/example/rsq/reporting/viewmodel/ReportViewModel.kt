package com.example.rsq.reporting.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.domain.MeshRelayEngine
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.ReportState
import com.example.rsq.reporting.model.SyncStatus
import com.example.rsq.reporting.sync.ImageStorageManager
import com.example.rsq.reporting.sync.SyncScheduler
import com.example.rsq.reporting.sync.ReportSyncManager
import com.example.rsq.storage.data.StorageRepository
import com.example.rsq.ai.data.SeverityEngine
import com.example.rsq.util.ConnectivityObserver
import androidx.lifecycle.SavedStateHandle
import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import com.example.rsq.data.repository.NotificationRepository
import com.example.rsq.location.model.LocationState
import com.example.rsq.data.repository.SettingsRepository
import com.example.rsq.util.EmergencyNotificationManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ReportViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ReportRepository,
    private val localRepository: LocalReportRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val locationStateFlow: StateFlow<LocationState>? = null,
    private val relayEngine: MeshRelayEngine? = null,
    private val identityProvider: NodeIdentityProvider? = null,
    private val notificationRepository: NotificationRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val storageRepository: StorageRepository = StorageRepository()
) : AndroidViewModel(application) {

    private val TAG = "RSQ_IMAGE_SYNC"

    // Form State for preservation across configuration changes/camera flow/process death
    val title = savedStateHandle.getStateFlow("report_title", "")
    val description = savedStateHandle.getStateFlow("report_description", "")
    val selectedImageUris = savedStateHandle.getStateFlow<List<Uri>>("report_image_uris", emptyList())
    val tempCameraUri = savedStateHandle.getStateFlow<Uri?>("report_temp_camera_uri", null)

    fun updateTitle(value: String) { savedStateHandle["report_title"] = value }
    fun updateDescription(value: String) { savedStateHandle["report_description"] = value }

    fun addImageUris(uris: List<Uri>) {
        val current = selectedImageUris.value.toMutableList()
        val remaining = 5 - current.size
        if (remaining > 0) {
            current.addAll(uris.take(remaining))
            savedStateHandle["report_image_uris"] = current
            Log.i(TAG, "IMAGE_URI_RECEIVED: count=${uris.size}, total=${current.size}")
        } else {
            Log.w(TAG, "PHOTO_LIMIT_REACHED: 5 photos maximum")
        }
    }

    fun removeImageUri(uri: Uri) {
        val current = selectedImageUris.value.filter { it != uri }
        savedStateHandle["report_image_uris"] = current
    }

    fun updateTempCameraUri(uri: Uri?) {
        savedStateHandle["report_temp_camera_uri"] = uri
    }

    fun clearForm() {
        updateTitle("")
        updateDescription("")
        savedStateHandle["report_image_uris"] = emptyList<Uri>()
        updateTempCameraUri(null)
    }

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()

    // Combined reports for the responder hub
    val allEmergencyReports: StateFlow<List<Report>> = combine(_reports, locationStateFlow ?: MutableStateFlow(
        LocationState()
    ), settingsRepository?.visibilityRadiusKm ?: MutableStateFlow(50)) { cloudAndMesh, locState, radiusKm ->
        val reportMap = mutableMapOf<String, Report>()
        val currentTime = System.currentTimeMillis()
        val visibilityRadiusMeters = radiusKm * 1000.0

        cloudAndMesh.forEach { reportMap[it.id] = it }

        reportMap.values
            .filter { report -> 
                report.expirationTimestamp == 0L || report.expirationTimestamp > currentTime 
            }
            .filter { report -> // Feature 3: Distance-based visibility
                val userLat = locState.latitude
                val userLon = locState.longitude
                if (userLat != null && userLon != null && report.latitude != null && report.longitude != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(userLat, userLon, report.latitude, report.longitude, results)
                    results[0] <= visibilityRadiusMeters
                } else {
                    true // If location is missing for either user or report, show it safely
                }
            }
            .sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connectivityStatus = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectivityObserver.Status.Unavailable)
    val connectivityStatus: StateFlow<ConnectivityObserver.Status> = _connectivityStatus

    init {
        observeLocalReports()
        observeCloudReports()
        
        // Trigger automatic synchronization when connectivity is restored
        viewModelScope.launch {
            connectivityStatus.collect { status ->
                if (status == ConnectivityObserver.Status.Available) {
                    SyncScheduler.scheduleSync(getApplication())
                }
            }
        }
    }

    fun submitReport(report: Report, imageUris: List<Uri> = emptyList()) {
        if (_reportState.value is ReportState.Submitting || 
            _reportState.value is ReportState.UploadingEvidence || 
            _reportState.value is ReportState.CreatingCloudReport) {
            return
        }
        viewModelScope.launch {
            _reportState.value = ReportState.Submitting
            val reportId = if (report.id.isBlank()) UUID.randomUUID().toString() else report.id
            val isOnline = _connectivityStatus.value == ConnectivityObserver.Status.Available

            if (isOnline) {
                // =========================================================
                // ROUTE A: DIRECT ONLINE SUBMISSION (ONLINE -> FIREBASE)
                // =========================================================
                Log.i("RSQ_ONLINE", "RSQ_ONLINE: ROUTE_SELECTED=DIRECT_ONLINE")
                Log.i("RSQ_ONLINE", "RSQ_ONLINE: DIRECT_SUBMIT_START reportId=$reportId, userId=${report.userId}")

                try {
                    // 1. Copy image URIs to internal storage files
                    val localPaths = mutableListOf<String>()
                    imageUris.forEachIndexed { index, uri ->
                        val path = ImageStorageManager.copyToInternalStorage(getApplication(), uri, index)
                        if (path != null) {
                            localPaths.add(path)
                        }
                    }

                    // 2. Multimodal AI Analysis
                    val aiAnalysisUri = if (localPaths.isNotEmpty()) Uri.fromFile(File(localPaths[0])) else null
                    val aiResult = SeverityEngine.analyzeMultimodal(
                        title = report.title,
                        description = report.description,
                        imageUri = aiAnalysisUri
                    )

                    // 3. Upload Images directly to Firebase Storage
                    Log.i("RSQ_ONLINE", "RSQ_ONLINE: MEDIA_UPLOAD_START reportId=$reportId count=${localPaths.size}")
                    _reportState.value = ReportState.UploadingEvidence

                    val uploadedUrls = mutableListOf<String>()
                    for (index in localPaths.indices) {
                        val file = File(localPaths[index])
                        if (file.exists()) {
                            val uploadResult = storageRepository.uploadImage(Uri.fromFile(file), reportId, index)
                            if (uploadResult.isSuccess) {
                                val downloadUrl = uploadResult.getOrThrow()
                                uploadedUrls.add(downloadUrl)
                                Log.i("RSQ_ONLINE", "RSQ_ONLINE: MEDIA_UPLOAD_SUCCESS reportId=$reportId index=$index url=$downloadUrl")
                            } else {
                                val e = uploadResult.exceptionOrNull()
                                Log.e("RSQ_ONLINE", "RSQ_ONLINE: MEDIA_UPLOAD_FAILED reportId=$reportId index=$index reason=${e?.message}")
                                _reportState.value = ReportState.Error(e?.message ?: "Image upload failed")
                                return@launch
                            }
                        }
                    }

                    // 4. Submit Report directly to Cloud Firestore
                    _reportState.value = ReportState.CreatingCloudReport
                    val durationHours = settingsRepository?.visibilityDurationHours?.value ?: 24
                    val finalReport = report.copy(
                        id = reportId,
                        severity = aiResult.severity,
                        aiScore = aiResult.finalScore,
                        detectedHazards = aiResult.detectedHazards.map { it.name },
                        recommendedResources = aiResult.recommendedResources,
                        imageUrl = uploadedUrls.firstOrNull(),
                        imageUrls = uploadedUrls,
                        isOffline = false,
                        expirationTimestamp = report.timestamp + durationHours * 3600 * 1000L,
                        originUserId = report.effectiveOriginUserId,
                        originUserName = report.effectiveOriginUserName,
                        originCreatedAt = if (report.originCreatedAt > 0) report.originCreatedAt else report.timestamp,
                        receivedViaRelay = false
                    )

                    Log.i("RSQ_ONLINE", "RSQ_ONLINE: FIRESTORE_WRITE_START reportId=$reportId imageUrlsCount=${uploadedUrls.size}")
                    val cloudResult = repository.submitReport(finalReport)

                    if (cloudResult.isSuccess) {
                        Log.i("RSQ_ONLINE", "RSQ_ONLINE: FIRESTORE_WRITE_SUCCESS reportId=$reportId")
                        // Persist to local Room DB with SYNCED status so local UI lists show it
                        localRepository.saveReport(finalReport, localPaths, SyncStatus.SYNCED)
                        Log.i("RSQ_ONLINE", "RSQ_ONLINE: DIRECT_SUBMIT_SUCCESS reportId=$reportId")

                        // Cleanup temporary internal storage files after full success
                        for (path in localPaths) {
                            val file = File(path)
                            if (file.exists()) file.delete()
                        }

                        _reportState.value = ReportState.Success("Report submitted successfully.")
                        clearForm()
                    } else {
                        val error = cloudResult.exceptionOrNull()
                        Log.e("RSQ_ONLINE", "RSQ_ONLINE: FIRESTORE_WRITE_FAILED reportId=$reportId error=${error?.message}")
                        _reportState.value = ReportState.Error(error?.message ?: "Cloud submission failed")
                    }

                } catch (e: Exception) {
                    Log.e("RSQ_ONLINE", "RSQ_ONLINE: DIRECT_SUBMIT_EXCEPTION reportId=$reportId error=${e.message}", e)
                    _reportState.value = ReportState.Error(e.message ?: "Report submission failed")
                }

            } else {
                // =========================================================
                // ROUTE B: EXISTING OFFLINE MESH ROUTE (OFFLINE -> MESH)
                // =========================================================
                Log.i(TAG, "COMMUNICATION_PATH_SELECTED: OFFLINE (Mesh only)")
                try {
                    // 1. Copy images to internal storage
                    val localPaths = mutableListOf<String>()
                    imageUris.forEachIndexed { index, uri ->
                        val path = ImageStorageManager.copyToInternalStorage(getApplication(), uri, index)
                        if (path != null) {
                            localPaths.add(path)
                        }
                    }

                    // 2. Multimodal AI Analysis
                    val aiAnalysisUri = if (localPaths.isNotEmpty()) Uri.fromFile(File(localPaths[0])) else null
                    val aiResult = SeverityEngine.analyzeMultimodal(
                        title = report.title,
                        description = report.description,
                        imageUri = aiAnalysisUri
                    )

                    val durationHours = settingsRepository?.visibilityDurationHours?.value ?: 24
                    val finalReport = report.copy(
                        id = reportId,
                        severity = aiResult.severity,
                        aiScore = aiResult.finalScore,
                        detectedHazards = aiResult.detectedHazards.map { it.name },
                        recommendedResources = aiResult.recommendedResources,
                        expirationTimestamp = report.timestamp + durationHours * 3600 * 1000L,
                        originUserId = report.effectiveOriginUserId,
                        originUserName = report.effectiveOriginUserName,
                        originCreatedAt = if (report.originCreatedAt > 0) report.originCreatedAt else report.timestamp
                    )

                    // 3. Persist locally FIRST (Durable Offline-First)
                    localRepository.saveReport(finalReport, localPaths, SyncStatus.LOCAL_ONLY)
                    Log.i(TAG, "LOCAL_REPORT_SAVED: ID=$reportId")

                    // 4. Mesh broadcast
                    viewModelScope.launch {
                        val result = broadcastViaMesh(finalReport, localPaths)
                        if (result != null && result.isSuccess) {
                            Log.i(TAG, "MESH_BROADCAST_SUCCESS: reportId=$reportId")
                        } else {
                            val error = result?.exceptionOrNull()?.message ?: "Engine or transport unavailable"
                            Log.w(TAG, "MESH_BROADCAST_FAILED: reportId=$reportId, reason=$error")
                        }
                    }

                    // 5. Notify UI & Schedule background WorkManager sync
                    _reportState.value = ReportState.PendingSync("Offline: Emergency alert sent to nearby devices. Will sync to cloud when internet returns.")
                    SyncScheduler.scheduleSync(getApplication())
                    clearForm()

                } catch (e: Exception) {
                    Log.e(TAG, "REPORT_SUBMIT_FAILED_UNEXPECTED: ${e.message}", e)
                    _reportState.value = ReportState.Error(e.message ?: "Failed to process report")
                }
            }
        }
    }

    private fun observeLocalReports() {
        viewModelScope.launch {
            localRepository.observeAllReports().collect { localReports ->
                _reports.value = localReports
            }
        }
    }

    private fun observeCloudReports() {
        viewModelScope.launch {
            repository.observeAllActiveReports().collect { cloudReports ->
                val currentTime = System.currentTimeMillis()
                val localUserId = identityProvider?.getNodeId() ?: ""
                cloudReports.forEach { report ->
                    val isExpired = report.expirationTimestamp > 0L && report.expirationTimestamp <= currentTime
                    if (!isExpired) {
                        localRepository.saveReport(report, emptyList(), SyncStatus.SYNCED)

                        if (localUserId.isNotBlank() && report.userId != localUserId) {
                            val notif = Notification(
                                id = "NT_SOS_${report.id}",
                                recipientId = localUserId,
                                title = "New Emergency Report",
                                message = "${report.severity}: ${report.title}",
                                timestamp = "Just now",
                                type = NotificationType.SOS_ALERT,
                                isRead = false,
                                associatedReportId = report.id
                            )
                            val isNewNotif = notificationRepository?.addNotificationUnique(notif) ?: false
                            if (isNewNotif) {
                                EmergencyNotificationManager.showSystemNotification(getApplication(), report)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun broadcastViaMesh(report: Report, localImagePaths: List<String> = emptyList()): Result<Unit>? {
        val meshMessage = convertToMeshMessage(report)
        val mediaFiles = localImagePaths.map { File(it) }.filter { it.exists() }
        return relayEngine?.broadcastMessage(meshMessage, mediaFiles)
    }

    private fun convertToMeshMessage(report: Report): MeshMessage {
        val priority = when (report.severity.uppercase()) {
            "CRITICAL", "HIGH" -> Priority.HIGH
            "MEDIUM" -> Priority.MEDIUM
            else -> Priority.LOW
        }

        return MeshMessage(
            id = report.id,
            senderNodeId = identityProvider?.getNodeId() ?: "",
            originNodeId = report.effectiveOriginUserId,
            messageType = MeshMessageType.REPORT_RELAY,
            timestamp = report.timestamp,
            latitude = report.latitude,
            longitude = report.longitude,
            priority = priority,
            payload = "${report.title}: ${report.description}",
            ttl = 3,
            title = report.title,
            description = report.description,
            expirationTimestamp = report.expirationTimestamp,
            originUserId = report.effectiveOriginUserId,
            originUserName = report.effectiveOriginUserName,
            originCreatedAt = if (report.originCreatedAt > 0) report.originCreatedAt else report.timestamp
        )
    }

    companion object {
        fun convertFromMeshMessage(msg: MeshMessage): Report {
            val title = if (msg.title.isNotBlank()) msg.title else msg.payload.substringBefore(": ")
            val description = if (msg.description.isNotBlank()) msg.description else msg.payload.substringAfter(": ")
            val originUid = if (msg.originUserId.isNotBlank()) msg.originUserId else msg.originNodeId
            val originName = if (msg.originUserName.isNotBlank()) msg.originUserName else "User ${originUid.take(6)}"
            val originTime = if (msg.originCreatedAt > 0) msg.originCreatedAt else msg.timestamp

            val report = Report(
                id = msg.id,
                userId = originUid,
                userName = originName,
                title = title,
                description = description,
                severity = when (msg.priority) {
                    Priority.HIGH -> "HIGH"
                    Priority.MEDIUM -> "MEDIUM"
                    Priority.LOW -> "LOW"
                },
                status = ReportStatus.OPEN,
                timestamp = msg.timestamp,
                latitude = msg.latitude,
                longitude = msg.longitude,
                isOffline = true,
                expirationTimestamp = msg.expirationTimestamp,
                originUserId = originUid,
                originUserName = originName,
                originCreatedAt = originTime,
                relayDeviceId = msg.senderNodeId,
                receivedViaRelay = true
            )

            Log.i("RSQ_DIAGNOSTIC", "BOUNDARY_2_CONVERT_FROM_MESH: " +
                "reportId=${report.id}, " +
                "originNodeId=${msg.originNodeId}, " +
                "originUserId=${report.originUserId}, " +
                "relayDeviceId=${report.relayDeviceId}, " +
                "relayUserId=${report.relayUserId}, " +
                "receivedViaRelay=${report.receivedViaRelay}"
            )

            return report
        }
    }

    fun loadReports(userId: String) {
        viewModelScope.launch {
            // Feature 1: Notifications for changes in the status of reports submitted by the user.
            repository.observeReports(userId).collect { cloudReports ->
                val currentLocal = _reports.value
                val newStatusReports = cloudReports.filter { cloudReport ->
                    val local = currentLocal.find { it.id == cloudReport.id }
                    local != null && local.status != cloudReport.status
                }
                
                _reports.value = cloudReports
                _reportState.value = ReportState.Idle
                
                newStatusReports.forEach { updatedReport ->
                    notificationRepository?.addNotification(
                        Notification(
                            id = UUID.randomUUID().toString(),
                            recipientId = userId,
                            title = "Report Status Updated",
                            message = "Your report for ${updatedReport.title} is now ${updatedReport.status.name}.",
                            timestamp = "Just now",
                            type = NotificationType.SOS_ALERT,
                            associatedReportId = updatedReport.id
                        )
                    )
                }
            }
        }
    }

    fun resetState() {
        _reportState.value = ReportState.Idle
    }
}
