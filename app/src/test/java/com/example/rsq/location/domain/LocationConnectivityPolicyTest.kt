package com.example.rsq.location.domain

import com.example.rsq.location.model.LocationReadiness
import com.example.rsq.location.model.LocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationConnectivityPolicyTest {

    private val policy = LocationConnectivityPolicy()

    @Test
    fun `when online, location state is returned unchanged`() {
        val state = LocationState(latitude = 10.0, longitude = 20.0, readiness = LocationReadiness.ACQUIRING)
        val result = policy.getEffectiveLocationState(state, isOnline = true)

        assertEquals(LocationReadiness.ACQUIRING, result.readiness)
    }

    @Test
    fun `when offline with cached location, returns READY`() {
        val state = LocationState(latitude = 10.0, longitude = 20.0, readiness = LocationReadiness.ACQUIRING)
        val result = policy.getEffectiveLocationState(state, isOnline = false)

        assertEquals(LocationReadiness.READY, result.readiness)
        assertNull(result.error)
    }

    @Test
    fun `when offline without cached location, returns ERROR`() {
        val state = LocationState(latitude = null, longitude = null, readiness = LocationReadiness.ACQUIRING)
        val result = policy.getEffectiveLocationState(state, isOnline = false)

        assertEquals(LocationReadiness.ERROR, result.readiness)
        assertEquals("Location required. No offline cache available.", result.error)
    }
}