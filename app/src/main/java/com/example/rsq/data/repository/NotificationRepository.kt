package com.example.rsq.data.repository

import com.example.rsq.data.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(recipientId: String): Flow<List<Notification>>
    fun getUnreadCount(recipientId: String): Flow<Int>
    suspend fun markAsRead(id: String)
    suspend fun addNotification(notification: Notification)
    suspend fun markAllAsRead(recipientId: String)
}
