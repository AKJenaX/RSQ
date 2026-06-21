package com.example.rsq.data.model

data class SOSMessage(
    val id: String,
    val role: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val priority: Priority,
    val message: String,
)
