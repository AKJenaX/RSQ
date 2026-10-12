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
        assignmentRepository.startRealtimeSync(firebaseUid)
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // Try to resolve or create profile first
            volunteerRepository.getVolunteerData(firebaseUid).collect { volunteer ->
                if (volunteer == null) {
                    // Fallback: If no profile exists locally, create a default one.
                    // Because getVolunteerData returns a continuous Flow, creating the
                    // profile will automatically trigger a new emission with the created Volunteer.
                    volunteerRepository.createVolunteerProfile(firebaseUid, "Responder")
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

    fun toggleAvailability(isAvailable: Boolean) {
        viewModelScope.launch {
            volunteerRepository.updateAvailability(firebaseUid, isAvailable)
        }
    }

    override fun onCleared() {
        super.onCleared()
        assignmentRepository.stopRealtimeSync()
    }
}
