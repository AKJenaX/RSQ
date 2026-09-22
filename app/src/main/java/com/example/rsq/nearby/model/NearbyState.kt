package com.example.rsq.nearby.model

data class NearbyState(
    val readiness: NearbyReadiness = NearbyReadiness.NOT_DETERMINED,
    val isDetecting: Boolean = false,
    val connectedPeers: Int = 0,
    val error: String? = null
)
