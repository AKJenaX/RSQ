package com.example.rsq.data.repository

import com.example.rsq.data.local.NotificationDao
import com.example.rsq.data.local.NotificationEntity
import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import kotlinx.coroutines.flow.*

class NotificationRepositoryImpl(
    private val notificationDao: NotificationDao
) : NotificationRepository {

    override fun getNotifications(recipientId: String): Flow<List<Notification>> =
        notificationDao.getNotificationsForRecipient(recipientId).map { list ->
            list.map { it.toDomain() }
        }

    override fun getUnreadCount(recipientId: String): Flow<Int> =
        notificationDao.getUnreadCount(recipientId)

    override suspend fun markAsRead(id: String) {
        notificationDao.markAsRead(id)
    }

    override suspend fun addNotification(notification: Notification) {
        notificationDao.insertNotification(NotificationEntity.fromDomain(notification))
    }

    override suspend fun addNotificationUnique(notification: Notification): Boolean {
        val reportId = notification.associatedReportId
        if (!reportId.isNullOrBlank()) {
            val existing = notificationDao.getNotificationForReport(reportId, notification.recipientId, notification.type.name)
            if (existing != null) {
                return false
            }
        }
        val rowId = notificationDao.insertNotificationIgnore(NotificationEntity.fromDomain(notification))
        return rowId != -1L
    }

    override suspend fun markAllAsRead(recipientId: String) {
        notificationDao.markAllAsRead(recipientId)
    }
}
