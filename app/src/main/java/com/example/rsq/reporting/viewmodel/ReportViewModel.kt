package com.example.rsq.reporting.viewmodel

import android.app.Application
import android.net.Uri
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
import com.example.rsq.ai.data.SeverityEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ReportViewModel(
    application: Application,
    private val repository: ReportRepository,
    private val localRepository: LocalReportRepository,
    private val relayEngine: MeshRelayEngine? = null,
    private val identityProvider: NodeIdentityProvider? = null
) : AndroidViewModel(application) {

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

    fun submitReport(report: Report, imageUri: Uri? = null) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            
            try {
                // 1. Generate stable ID
                val reportId = if (report.id.isBlank()) UUID.randomUUID().toString() else report.id
                
                // 2. Handle Image: Copy to internal storage for offline durability
                var localPath: String? = null
                if (imageUri != null) {
                    localPath = ImageStorageManager.copyToInternalStorage(getApplication(), imageUri)
                }

                // 3. Multimodal AI Analysis
                // Use the stable local file path for analysis to ensure it's accessible
                val aiAnalysisUri = if (localPath != null) Uri.fromFile(java.io.File(localPath)) else null
                
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

                // 4. Persist locally FIRST (Durable Offline-First)
                localRepository.saveReport(finalReport, localPath, SyncStatus.LOCAL_ONLY)

                // 4. Mesh broadcast (Resilient offline fallback)
                // Fire and forget to avoid blocking UI transition
                viewModelScope.launch {
                    broadcastViaMesh(finalReport)
                }

                // 5. Trigger/Schedule Background Sync
                // Scheduling failure should not fail the submission if Room save succeeded
                try {
                    SyncScheduler.scheduleSync(getApplication())
                } catch (e: Exception) {
                    // Scheduling failure must not prevent local report success.
                }

                // 6. Final UI State: Decoupled from cloud result
                _reportState.value = ReportState.Success("Report saved locally. Cloud sync pending.")
                
            } catch (e: Exception) {
                _reportState.value = ReportState.Error(e.message ?: "Failed to save report locally")
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
                        // Phase 11: Persist mesh-delivered reports to local Room DB
                        localRepository.saveReport(report, null, SyncStatus.LOCAL_ONLY)
                        updateMeshReports(report)
                    }
            }
        }
    }

    private fun updateMeshReports(report: Report) {
        val current = _meshReports.value.toMutableList()
        // Prevent duplicates based on ID (though RelayEngine handles most of this)
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
            ttl = 3 // Standard default for RSQ mesh
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
            _reportState.value = ReportState.Loading
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
