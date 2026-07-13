package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.AuthorityDashboardStats
import com.example.rsq.data.model.RecentReport
import com.example.rsq.data.repository.AuthorityRepository
import com.example.rsq.data.repository.AuthorityRepositoryImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AuthorityViewModel(
    private val repository: AuthorityRepository = AuthorityRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Pair<AuthorityDashboardStats, List<RecentReport>>>>(UiState.Loading)
    val uiState: StateFlow<UiState<Pair<AuthorityDashboardStats, List<RecentReport>>>> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(1500)
            
            combine(
                repository.getDashboardStats(),
                repository.getRecentReports()
            ) { stats, reports ->
                stats to reports
            }.collect { (stats, reports) ->
                _uiState.value = UiState.Success(stats to reports)
            }
        }
    }
}
