package com.example.rsq.location.model

data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
