package com.example.rsq.data.model

enum class AssignmentStatus {
    AVAILABLE, ASSIGNED, IN_PROGRESS, RESOLVED
}

data class Assignment(
    val id: String,
    val reportId: String,
    val volunteerId: String?,
    val volunteerName: String, // Keeping for UI simplicity in Phase 1
    val victimName: String,    // Keeping for UI simplicity in Phase 1
    val disasterType: String,  // Keeping for UI simplicity in Phase 1
    val location: String,      // Keeping for UI simplicity in Phase 1
    val status: AssignmentStatus,
    val priority: Priority,
    val assignedTime: String,  // Keeping for UI simplicity in Phase 1
    val createdAt: Long,
    val updatedAt: Long
)
