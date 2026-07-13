package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.repository.AssignmentRepository
import com.example.rsq.data.repository.AssignmentRepositoryImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AssignmentViewModel(
    private val repository: AssignmentRepository = AssignmentRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Assignment>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Assignment>>> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(1500)
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
        println("Accepted assignment $id")
    }

    fun rejectAssignment(id: String) {
        println("Rejected assignment $id")
    }

    fun completeAssignment(id: String) {
        println("Completed assignment $id")
    }
}
