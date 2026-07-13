package com.example.rsq.data.repository

import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NotificationRepositoryImpl : NotificationRepository {
    override fun getNotifications(): Flow<List<Notification>> = flowOf(
        listOf(
            Notification(
                id = "NT-101",
                title = "New SOS Alert",
                message = "CRITICAL: Multiple victims reported trapped after Earthquake at North Ridge Apartments.",
                timestamp = "Just Now",
                type = NotificationType.SOS_ALERT
            ),
            Notification(
                id = "NT-102",
                title = "Volunteer Assigned",
                message = "Lead Volunteer Alex Rivera has been assigned to the Flood Rescue mission at Riverside.",
                timestamp = "15 mins ago",
                type = NotificationType.ASSIGNMENT_RECEIVED
            ),
            Notification(
                id = "NT-103",
                title = "Assignment Completed",
                message = "The Medical Aid mission at St. Jude Center has been successfully completed.",
                timestamp = "1 hour ago",
                type = NotificationType.ASSIGNMENT_COMPLETED,
                isRead = true
            ),
            Notification(
                id = "NT-104",
                title = "Donation Received",
                message = "Generous contribution of $5,000.00 received from Global Relief Org for disaster recovery.",
                timestamp = "2 hours ago",
                type = NotificationType.DONATION_RECEIVED,
                isRead = true
            )
        )
    )
}
