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
    private val volunteerRepository: VolunteerRepository = VolunteerRepositoryImpl(),
    private val assignmentRepository: AssignmentRepository = AssignmentRepositoryImpl(),
    private val notificationRepository: NotificationRepository = NotificationRepositoryImpl()
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
            
            combine(
                volunteerRepository.getVolunteerData(),
                assignmentRepository.getAssignments(),
                notificationRepository.getUnreadCount()
            ) { volunteer, assignments, unread ->
                VolunteerData(volunteer, assignments, unread)
            }.collect { data ->
                if (data.assignments.isEmpty()) {
                    _uiState.value = UiState.Empty
                } else {
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
