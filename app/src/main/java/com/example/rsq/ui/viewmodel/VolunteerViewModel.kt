package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.VolunteerAssignment
import com.example.rsq.data.repository.VolunteerRepository
import com.example.rsq.data.repository.VolunteerRepositoryImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VolunteerViewModel(
    private val repository: VolunteerRepository = VolunteerRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Pair<Volunteer, List<VolunteerAssignment>>>>(UiState.Loading)
    val uiState: StateFlow<UiState<Pair<Volunteer, List<VolunteerAssignment>>>> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(1500) // Simulate network delay
            
            combine(
                repository.getVolunteerData(),
                repository.getAssignments()
            ) { volunteer, assignments ->
                volunteer to assignments
            }.collect { (volunteer, assignments) ->
                if (assignments.isEmpty()) {
                    _uiState.value = UiState.Empty
                } else {
                    _uiState.value = UiState.Success(volunteer to assignments)
                }
            }
        }
    }

    fun acceptAssignment(reportId: String) {
        println("Accepted assignment: $reportId")
    }
}
