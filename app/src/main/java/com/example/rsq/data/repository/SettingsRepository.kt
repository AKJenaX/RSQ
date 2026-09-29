package com.example.rsq.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rsq_settings", Context.MODE_PRIVATE)

    private val _visibilityRadiusKm = MutableStateFlow(prefs.getInt("visibility_radius_km", 50))
    val visibilityRadiusKm: StateFlow<Int> = _visibilityRadiusKm.asStateFlow()

    private val _visibilityDurationHours = MutableStateFlow(prefs.getInt("visibility_duration_hours", 24))
    val visibilityDurationHours: StateFlow<Int> = _visibilityDurationHours.asStateFlow()

    fun setVisibilityRadiusKm(radius: Int) {
        prefs.edit().putInt("visibility_radius_km", radius).apply()
        _visibilityRadiusKm.value = radius
    }

    fun setVisibilityDurationHours(hours: Int) {
        prefs.edit().putInt("visibility_duration_hours", hours).apply()
        _visibilityDurationHours.value = hours
    }
}
