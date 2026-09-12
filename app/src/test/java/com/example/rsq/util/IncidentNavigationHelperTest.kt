package com.example.rsq.util

import org.junit.Assert.*
import org.junit.Test

class IncidentNavigationHelperTest {

    @Test
    fun `valid coordinates should be accepted`() {
        assertTrue(IncidentNavigationHelper.isValidCoordinate(12.9716, 77.5946))
        assertTrue(IncidentNavigationHelper.isValidCoordinate(0.0, 0.0))
        assertTrue(IncidentNavigationHelper.isValidCoordinate(-45.0, 120.0))
        assertTrue(IncidentNavigationHelper.isValidCoordinate(90.0, 180.0))
        assertTrue(IncidentNavigationHelper.isValidCoordinate(-90.0, -180.0))
    }

    @Test
    fun `missing null coordinates should be rejected`() {
        assertFalse(IncidentNavigationHelper.isValidCoordinate(null, 77.5946))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(12.9716, null))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(null, null))
    }

    @Test
    fun `invalid or non-finite latitude should be rejected`() {
        assertFalse(IncidentNavigationHelper.isValidCoordinate(90.1, 77.5946))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(-90.1, 77.5946))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(Double.NaN, 77.5946))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(Double.POSITIVE_INFINITY, 77.5946))
    }

    @Test
    fun `invalid or non-finite longitude should be rejected`() {
        assertFalse(IncidentNavigationHelper.isValidCoordinate(12.9716, 180.1))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(12.9716, -180.1))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(12.9716, Double.NaN))
        assertFalse(IncidentNavigationHelper.isValidCoordinate(12.9716, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `parsing coordinates from location string should extract valid double pair`() {
        val result1 = IncidentNavigationHelper.parseCoordinatesFromLocationString("Lat: 12.9716, Lon: 77.5946")
        assertNotNull(result1)
        assertEquals(12.9716, result1!!.first, 0.0001)
        assertEquals(77.5946, result1.second, 0.0001)

        val result2 = IncidentNavigationHelper.parseCoordinatesFromLocationString("Lat: -33.8688, Lng: 151.2093")
        assertNotNull(result2)
        assertEquals(-33.8688, result2!!.first, 0.0001)
        assertEquals(151.2093, result2.second, 0.0001)

        val result3 = IncidentNavigationHelper.parseCoordinatesFromLocationString("12.9716, 77.5946")
        assertNotNull(result3)
        assertEquals(12.9716, result3!!.first, 0.0001)
        assertEquals(77.5946, result3.second, 0.0001)
    }

    @Test
    fun `parsing coordinates from location string should return null for location unknown`() {
        assertNull(IncidentNavigationHelper.parseCoordinatesFromLocationString("Location Unknown"))
        assertNull(IncidentNavigationHelper.parseCoordinatesFromLocationString("Unknown"))
        assertNull(IncidentNavigationHelper.parseCoordinatesFromLocationString(""))
        assertNull(IncidentNavigationHelper.parseCoordinatesFromLocationString(null))
    }
}
