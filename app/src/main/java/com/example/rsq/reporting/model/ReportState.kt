package com.example.rsq.reporting.model

enum class OfflineSyncStatus {
    QUEUED,
    MESH_RELAYING,
    RECEIVED_BY_RELAY,
    FIREBASE_SYNCING,
    MEDIA_UPLOADING,
    SYNCED,
    FAILED
}

sealed class ReportState {
    object Idle : ReportState()
    object Submitting : ReportState()
    object UploadingEvidence : ReportState()
    object CreatingCloudReport : ReportState()
    data class Success(val message: String) : ReportState()

    /**
     * Used when report is submitted offline or pending cloud sync.
     */
    data class PendingSync(
        val reason: String,
        val statusStage: OfflineSyncStatus = OfflineSyncStatus.MESH_RELAYING,
        val uploadedMediaCount: Int = 0,
        val totalMediaCount: Int = 0,
        val reportId: String = ""
    ) : ReportState()

    data class Error(val message: String) : ReportState()

    @Deprecated("Use more specific states", replaceWith = ReplaceWith("Submitting"))
    object Loading : ReportState()
}
