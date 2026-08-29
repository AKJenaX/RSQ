package com.example.rsq.reporting.model

sealed class ReportState {
    object Idle : ReportState()
    object Submitting : ReportState()
    object UploadingEvidence : ReportState()
    object CreatingCloudReport : ReportState()
    data class Success(val message: String) : ReportState()

    /**
     * Used when immediate cloud sync fails but the report is safe locally.
     */
    data class PendingSync(val reason: String) : ReportState()

    data class Error(val message: String) : ReportState()

    @Deprecated("Use more specific states", replaceWith = ReplaceWith("Submitting"))
    object Loading : ReportState()
}
