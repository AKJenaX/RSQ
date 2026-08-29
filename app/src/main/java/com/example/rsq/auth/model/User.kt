package com.example.rsq.auth.model

/**
 * Canonical RSQ User Profile model.
 * Uses firebaseUid as the primary identity key to match Firestore document IDs.
 */
data class User(
    val firebaseUid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val isAuthorized: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
