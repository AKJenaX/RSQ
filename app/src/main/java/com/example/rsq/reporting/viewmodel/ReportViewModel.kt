package com.example.rsq.reporting.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
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
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ReportViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ReportRepository,
    private val localRepository: LocalReportRepository,
    private val relayEngine: MeshRelayEngine? = null,
    private val identityProvider: NodeIdentityProvider? = null
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

    private val _meshReports = MutableStateFlow<List<Report>>(emptyList())
    val meshReports: StateFlow<List<Report>> = _meshReports.asStateFlow()

    init {
        observeMeshTraffic()
        observeLocalReports()
    }

    fun submitReport(report: Report, imageUris: List<Uri> = emptyList()) {
        viewModelScope.launch {
            _reportState.value = ReportState.Submitting
            val reportId = if (report.id.isBlank()) UUID.randomUUID().toString() else report.id
            Log.i(TAG, "REPORT_SUBMIT_START: reportId=$reportId, userId=${report.userId}, imageCount=${imageUris.size}, title=${report.title}")

            try {
                // 1. Copy images to internal storage IMMEDIATELY to prevent URI permission loss
                val localPaths = mutableListOf<String>()
                imageUris.forEachIndexed { index, uri ->
                    Log.i(TAG, "IMAGE_LOCAL_COPY_START: index=$index, URI=$uri")
                    val path = ImageStorageManager.copyToInternalStorage(getApplication(), uri, index)
                    if (path != null) {
                        localPaths.add(path)
                    } else {
                        Log.e(TAG, "IMAGE_LOCAL_COPY_FAILED: index=$index - Aborting immediate cloud sync")
                    }
                }

                // 2. Multimodal AI Analysis (using first image if available)
                val aiAnalysisUri = if (localPaths.isNotEmpty()) Uri.fromFile(java.io.File(localPaths[0])) else null

                val aiResult = SeverityEngine.analyzeMultimodal(
                    title = report.title,
                    description = report.description,
                    imageUri = aiAnalysisUri
                )

                val finalReport = report.copy(
                    id = reportId,
                    severity = aiResult.severity,
                    aiScore = aiResult.finalScore,
                    detectedHazards = aiResult.detectedHazards.map { it.name },
                    recommendedResources = aiResult.recommendedResources
                )

                // 3. Persist locally FIRST
                localRepository.saveReport(finalReport, localPaths, SyncStatus.LOCAL_ONLY)
                Log.i(TAG, "LOCAL_REPORT_SAVED: ID=$reportId")

                // 4. Mesh broadcast
                viewModelScope.launch {
                    broadcastViaMesh(finalReport)
                }

                // 5. Immediate Cloud Sync attempt
                val syncManager = ReportSyncManager(
                    getApplication(),
                    localRepository,
                    repository,
                    StorageRepository()
                )

                Log.i(TAG, "CLOUD_SYNC_STARTED: Attempting immediate sync for $reportId")

                val syncResult = syncManager.syncReport(reportId) { progress ->
                    when (progress) {
                        ReportSyncManager.SyncProgress.UPLOADING_EVIDENCE ->
                            _reportState.value = ReportState.UploadingEvidence
                        ReportSyncManager.SyncProgress.CREATING_CLOUD_REPORT ->
                            _reportState.value = ReportState.CreatingCloudReport
                    }
                }

                if (syncResult.isSuccess) {
                    Log.i(TAG, "REPORT_SYNC_SUCCESS: $reportId")
                    _reportState.value = ReportState.Success("Report submitted successfully.")
                    clearForm()
                } else {
                    val error = syncResult.exceptionOrNull()
                    val reason = when {
                        error?.message?.contains("upload", true) == true -> "Image upload failed"
                        error?.message?.contains("Firestore", true) == true -> "Cloud write failed"
                        else -> "Device is offline"
                    }
                    Log.w(TAG, "REPORT_SYNC_FAILED: $reportId, reason=$reason, technical=${error?.message}")
                    SyncScheduler.scheduleSync(getApplication())
                    _reportState.value = ReportState.PendingSync("Saved locally. $reason. Syncing will continue in background.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "REPORT_SUBMIT_FAILED_UNEXPECTED: ${e.message}", e)
                _reportState.value = ReportState.Error(e.message ?: "Failed to process report")
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

    private suspend fun broadcastViaMesh(report: Report): Result<Unit>? {
        val meshMessage = convertToMeshMessage(report)
        return relayEngine?.broadcastMessage(meshMessage)
    }

    private fun observeMeshTraffic() {
        relayEngine?.let { engine ->
            viewModelScope.launch {
                engine.processedMessages
                    .filter { it.messageType == MeshMessageType.REPORT_RELAY || it.messageType == MeshMessageType.SOS }
                    .collect { meshMsg ->
                        val report = convertFromMeshMessage(meshMsg)
                        localRepository.saveReport(report, emptyList(), SyncStatus.LOCAL_ONLY)
                        updateMeshReports(report)
                    }
            }
        }
    }

    private fun updateMeshReports(report: Report) {
        val current = _meshReports.value.toMutableList()
        if (current.none { it.id == report.id }) {
            current.add(0, report)
            _meshReports.value = current.take(50) // Keep latest 50
        }
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
            originNodeId = report.userId,
            messageType = MeshMessageType.REPORT_RELAY,
            timestamp = report.timestamp,
            latitude = report.latitude,
            longitude = report.longitude,
            priority = priority,
            payload = "${report.title}: ${report.description}",
            ttl = 3
        )
    }

    private fun convertFromMeshMessage(msg: MeshMessage): Report {
        return Report(
            id = msg.id,
            userId = msg.originNodeId,
            title = msg.payload.substringBefore(": "),
            description = msg.payload.substringAfter(": "),
            severity = when (msg.priority) {
                Priority.HIGH -> "HIGH"
                Priority.MEDIUM -> "MEDIUM"
                Priority.LOW -> "LOW"
            },
            status = ReportStatus.OPEN,
            timestamp = msg.timestamp,
            latitude = msg.latitude,
            longitude = msg.longitude,
            isOffline = true
        )
    }

    fun loadReports(userId: String) {
        viewModelScope.launch {
            val result = repository.getReports(userId)
            if (result.isSuccess) {
                _reports.value = result.getOrNull() ?: emptyList()
                _reportState.value = ReportState.Idle
            } else {
                _reportState.value = ReportState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load reports"
                )
            }
        }
    }

    fun resetState() {
        _reportState.value = ReportState.Idle
    }
}
