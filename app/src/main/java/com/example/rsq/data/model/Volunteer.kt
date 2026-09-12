package com.example.rsq.data.model

data class Volunteer(
    val id: String,
    val name: String,
    val totalAssignments: Int,
    val pendingAssignments: Int,
    val activeAssignments: Int,
    val completedAssignments: Int,
    val firebaseUid: String? = null,
    val isAvailable: Boolean = false,
    val syncState: String = "SYNCED"
)
