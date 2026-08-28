package com.example.rsq.location.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import com.example.rsq.location.model.LocationReadiness
import com.example.rsq.location.model.LocationState
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val _locationState = MutableStateFlow(LocationState())
    val locationState = _locationState.asStateFlow()

    private val MIN_ACCURACY_METERS = 50f // Acceptable accuracy threshold

    fun isLocationServicesEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun observeLocationReadiness(): Flow<LocationState> = callbackFlow {
        if (!isLocationServicesEnabled()) {
            _locationState.value = _locationState.value.copy(readiness = LocationReadiness.SERVICES_DISABLED)
            trySend(_locationState.value)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    val isReady = location.accuracy <= MIN_ACCURACY_METERS
                    val readiness = if (isReady) LocationReadiness.READY else LocationReadiness.LOW_ACCURACY
                    
                    _locationState.value = LocationState(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = location.time,
                        readiness = readiness,
                        isLoading = false
                    )
                } else {
                    _locationState.value = _locationState.value.copy(
                        readiness = LocationReadiness.ACQUIRING,
                        isLoading = true
                    )
                }
                trySend(_locationState.value)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    if (!isLocationServicesEnabled()) {
                        _locationState.value = _locationState.value.copy(readiness = LocationReadiness.SERVICES_DISABLED)
                    } else {
                        _locationState.value = _locationState.value.copy(readiness = LocationReadiness.ACQUIRING)
                    }
                    trySend(_locationState.value)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener { e ->
            _locationState.value = _locationState.value.copy(
                readiness = LocationReadiness.ERROR,
                error = e.message
            )
            trySend(_locationState.value)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation() {
        if (!isLocationServicesEnabled()) {
            _locationState.value = _locationState.value.copy(
                readiness = LocationReadiness.SERVICES_DISABLED,
                error = "Location services are disabled"
            )
            return
        }

        _locationState.value = _locationState.value.copy(isLoading = true, error = null)
        try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()

            if (location != null) {
                val isReady = location.accuracy <= MIN_ACCURACY_METERS
                val readiness = if (isReady) LocationReadiness.READY else LocationReadiness.LOW_ACCURACY

                _locationState.value = LocationState(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = location.time,
                    readiness = readiness,
                    isLoading = false
                )
            } else {
                _locationState.value = _locationState.value.copy(
                    isLoading = false,
                    readiness = LocationReadiness.ACQUIRING,
                    error = "Location is null. GPS might be warming up."
                )
            }
        } catch (e: Exception) {
            _locationState.value = _locationState.value.copy(
                isLoading = false,
                readiness = LocationReadiness.ERROR,
                error = e.message ?: "Failed to retrieve location"
            )
        }
    }

    fun updateReadiness(readiness: LocationReadiness) {
        _locationState.value = _locationState.value.copy(readiness = readiness)
    }
}
