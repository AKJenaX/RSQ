package com.example.rsq.data.model

enum class AssignmentStatus {
    AVAILABLE, ASSIGNED, IN_PROGRESS, RESOLVED
}

data class Assignment(
    val id: String,
    val reportId: String,
    val volunteerId: String?,
    val volunteerName: String, // Keeping for UI simplicity
    val victimName: String,    // Keeping for UI simplicity
    val disasterType: String,  // Keeping for UI simplicity
    val location: String,      // Keeping for UI simplicity
    val status: AssignmentStatus,
    val priority: Priority,
    val assignedTime: String,  // Keeping for UI simplicity
    val createdAt: Long,
    val updatedAt: Long,
    val volunteerFirebaseUid: String? = null,
    val authorityId: String? = null,
    val syncState: String = "SYNCED"
)
