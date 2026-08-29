package com.example.rsq.location.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import android.location.Location
import android.util.Log
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
    private val TAG = "LocationRepository"
    private val _locationState = MutableStateFlow(LocationState())
    val locationState = _locationState.asStateFlow()

    private val SOS_ACCURACY_THRESHOLD = 100f // 100 meters
    private val STALE_THRESHOLD_MS = 60_000L // 1 minute

    fun isLocationServicesEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (isEnabled) Log.d(TAG, "LOCATION_SERVICES_ON") else Log.w(TAG, "LOCATION_SERVICES_OFF")
        return isEnabled
    }

    private fun isLocationValid(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        val isFresh = ageMs < STALE_THRESHOLD_MS
        val isAccurate = location.accuracy > 0 && location.accuracy <= SOS_ACCURACY_THRESHOLD
        val hasCoords = location.latitude != 0.0 || location.longitude != 0.0
        val isValidRange = location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
        val isMock = location.isFromMockProvider

        val accepted = hasCoords && isValidRange && isFresh && isAccurate

        val logMsg = "lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}m, age=${ageMs}ms, provider=${location.provider}, isMock=$isMock"

        if (accepted) {
            Log.i(TAG, "LOCATION_ACCEPTED: $logMsg")
        } else {
            val reason = when {
                !hasCoords || !isValidRange -> "INVALID_COORDINATES"
                !isFresh -> "STALE"
                !isAccurate -> "LOW_ACCURACY"
                else -> "UNKNOWN"
            }
            Log.w(TAG, "LOCATION_REJECTED: reason=$reason, $logMsg")
        }

        return accepted
    }

    @SuppressLint("MissingPermission")
    fun observeLocationReadiness(): Flow<LocationState> = callbackFlow {
        Log.d(TAG, "LOCATION_ACQUISITION_STARTED")

        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _locationState.value = _locationState.value.copy(readiness = LocationReadiness.PERMISSION_DENIED)
            trySend(_locationState.value)
        }

        if (!isLocationServicesEnabled()) {
            _locationState.value = _locationState.value.copy(readiness = LocationReadiness.SERVICES_DISABLED)
            trySend(_locationState.value)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null && isLocationValid(location)) {
                    val current = _locationState.value
                    // Only update if it's newer than what we have
                    if (current.timestamp == null || location.time > current.timestamp) {
                        Log.d(TAG, "LOCATION_ACQUIRED: Updating state")
                        val newState = LocationState(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            timestamp = location.time,
                            readiness = LocationReadiness.READY,
                            isLoading = false
                        )
                        _locationState.value = newState
                    }
                } else if (location == null && _locationState.value.latitude == null) {
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
                    } else if (_locationState.value.latitude == null) {
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
            Log.e(TAG, "LOCATION_ACQUISITION_FAILED: ${e.message}")
            _locationState.value = _locationState.value.copy(
                readiness = LocationReadiness.ERROR,
                error = e.message
            )
            trySend(_locationState.value)
        }

        awaitClose {
            Log.d(TAG, "LOCATION_ACQUISITION_STOPPED")
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

            if (location != null && isLocationValid(location)) {
                val current = _locationState.value
                if (current.timestamp == null || location.time > current.timestamp) {
                    _locationState.value = LocationState(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = location.time,
                        readiness = LocationReadiness.READY,
                        isLoading = false
                    )
                }
            } else {
                _locationState.value = _locationState.value.copy(
                    isLoading = false,
                    readiness = if (_locationState.value.latitude == null) LocationReadiness.ACQUIRING else _locationState.value.readiness,
                    error = if (location == null) "Location is null" else "Location rejected (stale/inaccurate)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCurrentLocation FAILED: ${e.message}")
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
