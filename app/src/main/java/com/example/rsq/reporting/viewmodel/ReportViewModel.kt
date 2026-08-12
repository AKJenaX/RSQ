package com.example.rsq.reporting.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.domain.MeshRelayEngine
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.ReportState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ReportViewModel(
    private val repository: ReportRepository,
    private val relayEngine: MeshRelayEngine? = null,
    private val identityProvider: NodeIdentityProvider? = null
) : ViewModel() {

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()

    private val _meshReports = MutableStateFlow<List<Report>>(emptyList())
    val meshReports: StateFlow<List<Report>> = _meshReports.asStateFlow()

    init {
        observeMeshTraffic()
    }

    fun submitReport(report: Report) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            
            // Ensure ID is generated before starting both paths for consistency
            val finalReport = if (report.id.isBlank()) {
                report.copy(id = UUID.randomUUID().toString())
            } else {
                report
            }

            // 1. Existing Firebase reporting
            val firebaseResult = repository.submitReport(finalReport)
            
            // 2. Mesh broadcast (Resilient offline fallback)
            val meshResult = try {
                broadcastViaMesh(finalReport)
            } catch (e: Exception) {
                Result.failure(e)
            }

            when {
                firebaseResult.isSuccess -> {
                    _reportState.value = ReportState.Success("Report submitted successfully")
                }
                meshResult?.isSuccess == true -> {
                    _reportState.value = ReportState.Success("Offline Mesh broadcast successful. Cloud sync pending.")
                }
                else -> {
                    _reportState.value = ReportState.Error(
                        firebaseResult.exceptionOrNull()?.message ?: "Failed to submit report"
                    )
                }
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
