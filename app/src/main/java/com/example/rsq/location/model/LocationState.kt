package com.example.rsq.location.model

data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long? = null,
    val readiness: LocationReadiness = LocationReadiness.NOT_DETERMINED,
    val isLoading: Boolean = false,
    val error: String? = null
)
