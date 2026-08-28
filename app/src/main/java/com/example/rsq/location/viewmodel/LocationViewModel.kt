package com.example.rsq.location.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.location.data.LocationRepository
import com.example.rsq.location.model.LocationReadiness
import com.example.rsq.location.model.LocationState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocationViewModel(
    private val repository: LocationRepository
) : ViewModel() {

    val locationState: StateFlow<LocationState> = repository.locationState

    val locationReadiness: StateFlow<LocationState> = repository.observeLocationReadiness()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocationState(readiness = LocationReadiness.NOT_DETERMINED)
        )

    fun fetchLocation() {
        viewModelScope.launch {
            repository.fetchCurrentLocation()
        }
    }

    fun updateReadiness(readiness: LocationReadiness) {
        repository.updateReadiness(readiness)
    }
}
