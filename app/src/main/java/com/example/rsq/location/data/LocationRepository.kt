package com.example.rsq.location.data

import com.example.rsq.location.model.LocationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationRepository {
    private val _locationState = MutableStateFlow(LocationState())
    val locationState = _locationState.asStateFlow()

    // Placeholder for future GPS implementation
    fun requestLocationUpdates() {
        // Implementation will be added in future commits
    }
}
