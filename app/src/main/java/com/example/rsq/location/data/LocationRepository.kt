package com.example.rsq.location.data

import android.annotation.SuppressLint
import com.example.rsq.location.model.LocationState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val _locationState = MutableStateFlow(LocationState())
    val locationState = _locationState.asStateFlow()

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation() {
        _locationState.value = _locationState.value.copy(isLoading = true, error = null)
        try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()

            if (location != null) {
                _locationState.value = LocationState(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    isLoading = false
                )
            } else {
                _locationState.value = _locationState.value.copy(
                    isLoading = false,
                    error = "Location is null. GPS might be disabled."
                )
            }
        } catch (e: Exception) {
            _locationState.value = _locationState.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to retrieve location"
            )
        }
    }
}
