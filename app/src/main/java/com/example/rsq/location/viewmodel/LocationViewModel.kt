package com.example.rsq.location.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.location.data.LocationRepository
import com.example.rsq.location.model.LocationState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LocationViewModel(
    private val repository: LocationRepository
) : ViewModel() {

    val locationState: StateFlow<LocationState> = repository.locationState

    fun fetchLocation() {
        viewModelScope.launch {
            repository.fetchCurrentLocation()
        }
    }
}
