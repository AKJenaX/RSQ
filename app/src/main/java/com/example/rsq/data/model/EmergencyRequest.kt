package com.example.rsq.data.model

data class EmergencyRequest(
    val id: String,
    val title: String,
    val distance: Double,
    val priority: Priority,
    val latitude: Double,
    val longitude: Double,
)
