package com.example.rsq.reporting.model

sealed class ReportState {
    object Idle : ReportState()
    object Loading : ReportState()
    data class Success(val message: String) : ReportState()
    data class Error(val message: String) : ReportState()
}
