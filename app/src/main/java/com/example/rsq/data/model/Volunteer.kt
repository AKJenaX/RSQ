package com.example.rsq.data.model

data class Volunteer(
    val id: String,
    val name: String,
    val totalAssignments: Int,
    val pendingAssignments: Int,
    val activeAssignments: Int,
    val completedAssignments: Int
)

data class VolunteerAssignment(
    val reportId: String,
    val disasterType: String,
    val location: String,
    val priority: Priority,
    val status: String
)
