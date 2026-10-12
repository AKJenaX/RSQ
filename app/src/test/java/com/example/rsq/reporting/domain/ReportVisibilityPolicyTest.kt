package com.example.rsq.reporting.domain

import com.example.rsq.location.model.LocationState
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportVisibilityPolicyTest {

    private val policy = ReportVisibilityPolicy()

    @Test
    fun `expired report is not visible`() {
        val currentTime = 1000L
        val report = createReport(expirationTime = 900L) // Expired
        val locationState = LocationState(latitude = 10.0, longitude = 20.0)

        val isVisible = policy.isVisible(report, locationState, 50, currentTime)
        assertFalse(isVisible)
    }

    @Test
    fun `report without expiration is visible`() {
        val currentTime = 1000L
        val report = createReport(expirationTime = 0L)
        val locationState = LocationState(latitude = null, longitude = null) // Missing location

        val isVisible = policy.isVisible(report, locationState, 50, currentTime)
        assertTrue(isVisible)
    }

    @Test
    fun `when location missing, returns true (fail open)`() {
        val currentTime = 100L
        val report = createReport(expirationTime = 1000L, lat = 10.0, lon = 20.0)
        val locationState = LocationState(latitude = null, longitude = null)

        val isVisible = policy.isVisible(report, locationState, 50, currentTime)
        assertTrue(isVisible)
    }

    private fun createReport(expirationTime: Long, lat: Double? = null, lon: Double? = null): Report {
        return Report(
            userId = "123",
            title = "Title",
            description = "Desc",
            severity = "HIGH",
            status = ReportStatus.OPEN,
            timestamp = 0L,
            latitude = lat,
            longitude = lon,
            expirationTimestamp = expirationTime
        )
    }
}