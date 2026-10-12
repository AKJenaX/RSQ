package com.example.rsq.reporting.domain

import android.location.Location
import com.example.rsq.location.model.LocationState
import com.example.rsq.reporting.model.Report

class ReportVisibilityPolicy {
    fun isVisible(
        report: Report,
        locationState: LocationState,
        visibilityRadiusKm: Int,
        currentTime: Long
    ): Boolean {
        // 1. Check expiration
        val isExpired = report.expirationTimestamp > 0L && report.expirationTimestamp <= currentTime
        if (isExpired) {
            return false
        }

        // 2. Check distance
        val userLat = locationState.latitude
        val userLon = locationState.longitude
        val reportLat = report.latitude
        val reportLon = report.longitude

        if (userLat != null && userLon != null && reportLat != null && reportLon != null) {
            val results = FloatArray(1)
            Location.distanceBetween(userLat, userLon, reportLat, reportLon, results)
            val visibilityRadiusMeters = visibilityRadiusKm * 1000.0
            return results[0] <= visibilityRadiusMeters
        } else {
            // Missing location -> fail-open emergency behavior
            return true
        }
    }
}