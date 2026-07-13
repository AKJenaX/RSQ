package com.example.rsq.data.repository

import com.example.rsq.data.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(): Flow<List<Notification>>
}
