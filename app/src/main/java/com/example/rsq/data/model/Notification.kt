package com.example.rsq.data.model

enum class NotificationType {
    SOS_ALERT,
    ASSIGNMENT_RECEIVED,
    ASSIGNMENT_COMPLETED,
    DONATION_RECEIVED
}

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val type: NotificationType,
    val isRead: Boolean = false
)
