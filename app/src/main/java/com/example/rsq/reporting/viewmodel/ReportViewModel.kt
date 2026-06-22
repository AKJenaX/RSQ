package com.example.rsq.reporting.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportViewModel(
    private val repository: ReportRepository = ReportRepository()
) : ViewModel() {

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()

    fun submitReport(report: Report) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            val result = repository.submitReport(report)
            if (result.isSuccess) {
                _reportState.value = ReportState.Success("Report submitted successfully")
            } else {
                _reportState.value = ReportState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to submit report"
                )
            }
        }
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
