package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.repository.AssignmentRepository
import com.example.rsq.data.repository.AssignmentRepositoryImpl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AssignmentViewModel(
    private val repository: AssignmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Assignment>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Assignment>>> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getAssignments().collect { list ->
                if (list.isEmpty()) {
                    _uiState.value = UiState.Empty
                } else {
                    _uiState.value = UiState.Success(list)
                }
            }
        }
    }

    fun acceptAssignment(id: String) {
        viewModelScope.launch {
            repository.updateAssignmentStatus(id, AssignmentStatus.ASSIGNED)
        }
    }

    fun startResponse(id: String) {
        viewModelScope.launch {
            repository.updateAssignmentStatus(id, AssignmentStatus.IN_PROGRESS)
        }
    }

    fun completeAssignment(id: String) {
        viewModelScope.launch {
            repository.updateAssignmentStatus(id, AssignmentStatus.RESOLVED)
        }
    }

    fun rejectAssignment(id: String) {
        viewModelScope.launch {
            repository.updateAssignmentStatus(id, AssignmentStatus.AVAILABLE)
        }
    }
}
