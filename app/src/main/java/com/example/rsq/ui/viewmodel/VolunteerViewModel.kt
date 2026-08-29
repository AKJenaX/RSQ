package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VolunteerViewModel(
    private val firebaseUid: String,
    private val volunteerRepository: VolunteerRepository,
    private val assignmentRepository: AssignmentRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<VolunteerData>>(UiState.Loading)
    val uiState: StateFlow<UiState<VolunteerData>> = _uiState.asStateFlow()

    data class VolunteerData(
        val volunteer: Volunteer,
        val assignments: List<Assignment>,
        val unreadNotifications: Int
    )

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // Try to resolve or create profile first
            volunteerRepository.getVolunteerData(firebaseUid).collect { volunteer ->
                if (volunteer == null) {
                    // This case should be handled by AuthViewModel,
                    // but we'll try a fallback here for robustness
                    _uiState.value = UiState.Error("Profile not resolved. Please restart app.")
                    return@collect
                }

                combine(
                    assignmentRepository.getAssignmentsForVolunteer(volunteer.id),
                    notificationRepository.getUnreadCount(volunteer.id)
                ) { assignments, unread ->
                    VolunteerData(volunteer, assignments, unread)
                }.collect { data ->
                    _uiState.value = UiState.Success(data)
                }
            }
        }
    }

    fun acceptAssignment(id: String) {
        viewModelScope.launch {
            assignmentRepository.updateAssignmentStatus(id, AssignmentStatus.ASSIGNED)
        }
    }
}
