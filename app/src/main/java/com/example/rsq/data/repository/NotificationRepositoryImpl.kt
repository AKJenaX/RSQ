package com.example.rsq.data.repository

import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import kotlinx.coroutines.flow.*

class NotificationRepositoryImpl : NotificationRepository {
    companion object {
        private val _notifications = MutableStateFlow<List<Notification>>(
            listOf(
                Notification(
                    id = "NT-001",
                    title = "Critical Alert: Earthquake",
                    message = "A magnitude 6.8 earthquake has been reported in North Ridge. Immediate response requested.",
                    timestamp = "10 mins ago",
                    type = NotificationType.SOS_ALERT,
                    isRead = false
                ),
                Notification(
                    id = "NT-002",
                    title = "New Mission Assigned",
                    message = "You have been assigned to mission ASGN-2024-05 (Flood Rescue).",
                    timestamp = "1 hour ago",
                    type = NotificationType.ASSIGNMENT_RECEIVED,
                    isRead = true
                )
            )
        )
    }

    override fun getNotifications(): Flow<List<Notification>> = _notifications.asStateFlow()

    override fun getUnreadCount(): Flow<Int> = _notifications.map { list ->
        list.count { !it.isRead }
    }

    override suspend fun markAsRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
    }

    override suspend fun addNotification(notification: Notification) {
        _notifications.update { listOf(notification) + it }
    }

    override suspend fun markAllAsRead() {
        _notifications.update { list ->
            list.map { it.copy(isRead = true) }
        }
    }
}
