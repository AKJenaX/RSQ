package com.example.rsq.data.repository

import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Priority
import com.google.firebase.firestore.DocumentSnapshot

object FirestoreAssignmentMapper {

    fun toFirestoreMap(assignment: Assignment): Map<String, Any?> {
        return mapOf(
            "assignmentId" to assignment.id,
            "reportId" to assignment.reportId,
            "volunteerId" to assignment.volunteerId,
            "volunteerFirebaseUid" to (assignment.volunteerFirebaseUid ?: assignment.volunteerId),
            "volunteerName" to assignment.volunteerName,
            "authorityId" to assignment.authorityId,
            "victimName" to assignment.victimName,
            "disasterType" to assignment.disasterType,
            "location" to assignment.location,
            "status" to assignment.status.name,
            "priority" to assignment.priority.name,
            "assignedTime" to assignment.assignedTime,
            "createdAt" to assignment.createdAt,
            "updatedAt" to assignment.updatedAt
        )
    }

    fun fromDocument(document: DocumentSnapshot): Assignment? {
        val id = document.getString("assignmentId") ?: document.id
        val reportId = document.getString("reportId") ?: return null
        val statusStr = document.getString("status") ?: AssignmentStatus.AVAILABLE.name
        val priorityStr = document.getString("priority") ?: Priority.MEDIUM.name

        val status = try {
            AssignmentStatus.valueOf(statusStr)
        } catch (e: Exception) {
            AssignmentStatus.AVAILABLE
        }

        val priority = try {
            Priority.valueOf(priorityStr)
        } catch (e: Exception) {
            Priority.MEDIUM
        }

        val volunteerFirebaseUid = document.getString("volunteerFirebaseUid") ?: document.getString("volunteerId")

        return Assignment(
            id = id,
            reportId = reportId,
            volunteerId = document.getString("volunteerId") ?: volunteerFirebaseUid,
            volunteerName = document.getString("volunteerName") ?: "Volunteer",
            victimName = document.getString("victimName") ?: "Victim",
            disasterType = document.getString("disasterType") ?: "Emergency Alert",
            location = document.getString("location") ?: "Unknown Location",
            status = status,
            priority = priority,
            assignedTime = document.getString("assignedTime") ?: "Just now",
            createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = document.getLong("updatedAt") ?: System.currentTimeMillis(),
            volunteerFirebaseUid = volunteerFirebaseUid,
            authorityId = document.getString("authorityId")
        )
    }
}
