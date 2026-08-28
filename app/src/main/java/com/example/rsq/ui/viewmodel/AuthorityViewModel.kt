package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.*
import com.example.rsq.data.repository.*
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.model.ReportStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class AuthorityViewModel(
    private val firebaseUid: String,
    private val assignmentRepository: AssignmentRepository,
    private val volunteerRepository: VolunteerRepository,
    private val notificationRepository: NotificationRepository,
    private val localReportRepository: LocalReportRepository?,
    private val reportRepository: ReportRepository?,
    private val authorityRepository: AuthorityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AuthorityData>>(UiState.Loading)
    val uiState: StateFlow<UiState<AuthorityData>> = _uiState.asStateFlow()

    data class AuthorityData(
        val stats: AuthorityDashboardStats,
        val reports: List<RecentReport>,
        val assignments: List<Assignment>,
        val volunteers: List<Volunteer>,
        val unreadNotifications: Int
    )

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            combine(
                authorityRepository.getDashboardStats(),
                authorityRepository.getRecentReports(),
                assignmentRepository.getAssignments(),
                volunteerRepository.getAllVolunteers(),
                notificationRepository.getUnreadCount(firebaseUid)
            ) { stats, reports, assignments, volunteers, unread ->
                AuthorityData(stats, reports, assignments, volunteers, unread)
            }.collect { data ->
                _uiState.value = UiState.Success(data)
            }
        }
    }

    fun assignVolunteer(report: RecentReport, volunteer: Volunteer) {
        viewModelScope.launch {
            // Use createAssignment if we want to provide full details from the RecentReport
            assignmentRepository.createAssignment(
                Assignment(
                    id = "ASGN-${report.id}",
                    reportId = report.id,
                    volunteerId = volunteer.id,
                    volunteerName = volunteer.name,
                    victimName = report.reporterName,
                    disasterType = report.type,
                    location = report.location,
                    status = AssignmentStatus.ASSIGNED,
                    priority = report.priority,
                    assignedTime = "Just now",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            
            // Sync report status to local and cloud
            localReportRepository?.updateReportStatus(report.id, ReportStatus.ASSIGNED)
            try {
                reportRepository?.updateReportStatus(report.id, ReportStatus.OPEN, ReportStatus.ASSIGNED)
            } catch (e: Exception) {
                // Ignore cloud failures
            }

            // Create notification for the volunteer
            val notification = Notification(
                id = "NT-${UUID.randomUUID()}",
                recipientId = volunteer.id,
                title = "New Mission Assigned",
                message = "You have been assigned to ${report.type} at ${report.location}.",
                timestamp = "Just now",
                type = NotificationType.ASSIGNMENT_RECEIVED,
                isRead = false
            )
            notificationRepository.addNotification(notification)
        }
    }
}
