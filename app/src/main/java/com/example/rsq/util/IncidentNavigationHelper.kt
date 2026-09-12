package com.example.rsq.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

object IncidentNavigationHelper {

    private const val TAG = "IncidentNavHelper"

    fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        return (latitude in -90.0..90.0) && (longitude in -180.0..180.0)
    }

    fun parseCoordinatesFromLocationString(location: String?): Pair<Double, Double>? {
        if (location.isNullOrBlank() || location.equals("Location Unknown", ignoreCase = true) || location.equals("Unknown", ignoreCase = true)) {
            return null
        }
        return try {
            val regex = Regex("""[-+]?\d*\.?\d+""")
            val matches = regex.findAll(location).map { it.value.toDoubleOrNull() }.filterNotNull().toList()
            if (matches.size >= 2) {
                val lat = matches[0]
                val lng = matches[1]
                if (isValidCoordinate(lat, lng)) Pair(lat, lng) else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse coordinates from location string: $location", e)
            null
        }
    }

    fun createGeoIntent(latitude: Double, longitude: Double): Intent {
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(Incident+Location)")
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun launchNavigation(context: Context, latitude: Double?, longitude: Double?): Boolean {
        if (!isValidCoordinate(latitude, longitude)) {
            Log.w(TAG, "Cannot launch navigation: Invalid or missing coordinates (lat=$latitude, lng=$longitude)")
            return false
        }

        val intent = createGeoIntent(latitude!!, longitude!!)

        return try {
            val packageManager = context.packageManager
            val resolved = intent.resolveActivity(packageManager) != null

            if (resolved) {
                context.startActivity(intent)
                Log.i(TAG, "Launched Geo Intent navigation to ($latitude, $longitude)")
                true
            } else {
                Log.w(TAG, "No activity resolved for Geo Intent")
                Toast.makeText(context, "No map application is available on this device.", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching navigation intent", e)
            Toast.makeText(context, "No map application is available on this device.", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
