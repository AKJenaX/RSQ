package com.example.rsq.location.domain

import com.example.rsq.location.model.LocationState
import com.example.rsq.location.model.LocationReadiness

class LocationConnectivityPolicy {
    fun getEffectiveLocationState(
        currentLocationState: LocationState,
        isOnline: Boolean
    ): LocationState {
        // CASE A & D - ONLINE: behave normally
        if (isOnline) {
            return currentLocationState
        }

        // CASE B & C - OFFLINE
        val hasCachedLocation = currentLocationState.latitude != null && currentLocationState.longitude != null

        return if (hasCachedLocation) {
            // CASE B: Previously captured valid location remains usable
            currentLocationState.copy(
                readiness = LocationReadiness.READY,
                error = null
            )
        } else {
            // CASE C: No valid location -> stay blocked
            currentLocationState.copy(
                readiness = LocationReadiness.ERROR,
                error = "Location required. No offline cache available."
            )
        }
    }
}