package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.AuthorityDashboardStats
import com.example.rsq.data.model.RecentReport
import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AuthorityViewModel(
    private val assignmentRepository: AssignmentRepository = AssignmentRepositoryImpl(),
    private val volunteerRepository: VolunteerRepository = VolunteerRepositoryImpl(assignmentRepository),
    private val donationRepository: DonationRepository = DonationRepositoryImpl(),
    private val notificationRepository: NotificationRepository = NotificationRepositoryImpl(),
    private val authorityRepository: AuthorityRepository = AuthorityRepositoryImpl(assignmentRepository, donationRepository)
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
                notificationRepository.getUnreadCount()
            ) { stats, reports, assignments, volunteers, unread ->
                AuthorityData(stats, reports, assignments, volunteers, unread)
            }.collect { data ->
                _uiState.value = UiState.Success(data)
            }
        }
    }

    fun assignVolunteer(assignmentId: String, volunteer: Volunteer) {
        viewModelScope.launch {
            assignmentRepository.assignVolunteer(assignmentId, volunteer.id, volunteer.name)
        }
    }
}
