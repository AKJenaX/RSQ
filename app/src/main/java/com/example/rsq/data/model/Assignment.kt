package com.example.rsq.data.model

data class Assignment(
    val id: String,
    val volunteerName: String,
    val victimName: String,
    val disasterType: String,
    val location: String,
    val assignedTime: String,
    val priority: Priority,
    val status: AssignmentStatus = AssignmentStatus.PENDING
)

enum class AssignmentStatus {
    PENDING, ACCEPTED, COMPLETED, REJECTED
}
