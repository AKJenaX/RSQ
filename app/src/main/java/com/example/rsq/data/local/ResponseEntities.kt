package com.example.rsq.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Notification
import com.example.rsq.data.model.NotificationType
import com.example.rsq.data.model.Priority
import com.example.rsq.data.model.Volunteer

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val volunteerId: String?,
    val volunteerName: String,
    val victimName: String,
    val disasterType: String,
    val location: String,
    val status: String,
    val priority: String,
    val assignedTime: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Assignment = Assignment(
        id = id,
        reportId = reportId,
        volunteerId = volunteerId,
        volunteerName = volunteerName,
        victimName = victimName,
        disasterType = disasterType,
        location = location,
        status = AssignmentStatus.valueOf(status),
        priority = Priority.valueOf(priority),
        assignedTime = assignedTime,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: Assignment): AssignmentEntity = AssignmentEntity(
            id = domain.id,
            reportId = domain.reportId,
            volunteerId = domain.volunteerId,
            volunteerName = domain.volunteerName,
            victimName = domain.victimName,
            disasterType = domain.disasterType,
            location = domain.location,
            status = domain.status.name,
            priority = domain.priority.name,
            assignedTime = domain.assignedTime,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

@Entity(tableName = "volunteers")
data class VolunteerEntity(
    @PrimaryKey val id: String,
    val firebaseUid: String?,
    val name: String,
    val totalAssignments: Int,
    val pendingAssignments: Int,
    val activeAssignments: Int,
    val completedAssignments: Int
) {
    fun toDomain(): Volunteer = Volunteer(
        id = id,
        name = name,
        totalAssignments = totalAssignments,
        pendingAssignments = pendingAssignments,
        activeAssignments = activeAssignments,
        completedAssignments = completedAssignments
    )

    companion object {
        fun fromDomain(domain: Volunteer, firebaseUid: String?): VolunteerEntity = VolunteerEntity(
            id = domain.id,
            firebaseUid = firebaseUid,
            name = domain.name,
            totalAssignments = domain.totalAssignments,
            pendingAssignments = domain.pendingAssignments,
            activeAssignments = domain.activeAssignments,
            completedAssignments = domain.completedAssignments
        )
    }
}

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val recipientId: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val type: String,
    val isRead: Boolean
) {
    fun toDomain(): Notification = Notification(
        id = id,
        recipientId = recipientId,
        title = title,
        message = message,
        timestamp = timestamp,
        type = NotificationType.valueOf(type),
        isRead = isRead
    )

    companion object {
        fun fromDomain(domain: Notification): NotificationEntity = NotificationEntity(
            id = domain.id,
            recipientId = domain.recipientId,
            title = domain.title,
            message = domain.message,
            timestamp = domain.timestamp,
            type = domain.type.name,
            isRead = domain.isRead
        )
    }
}
