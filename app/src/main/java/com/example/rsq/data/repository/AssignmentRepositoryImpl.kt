package com.example.rsq.data.repository

import com.example.rsq.data.local.AssignmentDao
import com.example.rsq.data.local.AssignmentEntity
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Priority
import kotlinx.coroutines.flow.*

class AssignmentRepositoryImpl(
    private val assignmentDao: AssignmentDao
) : AssignmentRepository {

    override fun getAssignments(): Flow<List<Assignment>> =
        assignmentDao.getAllAssignments().map { list ->
            list.map { it.toDomain() }
        }

    override fun getAssignmentById(id: String): Flow<Assignment?> =
        assignmentDao.getAssignmentById(id).map { it?.toDomain() }

    override fun getAssignmentsForVolunteer(
        volunteerId: String
    ): Flow<List<Assignment>> =
        assignmentDao.getAssignmentsForVolunteer(volunteerId).map { list ->
            list.map { it.toDomain() }
        }

    override fun getAssignmentsForReport(
        reportId: String
    ): Flow<List<Assignment>> =
        assignmentDao.getAssignmentsForReport(reportId).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun createAssignment(
        assignment: Assignment
    ) {
        assignmentDao.insertAssignment(AssignmentEntity.fromDomain(assignment))
    }

    override suspend fun updateAssignmentStatus(
        id: String,
        status: AssignmentStatus
    ) {
        val currentEntity = assignmentDao.getAssignmentByIdOneShot(id)
        if (currentEntity != null) {
            val fromStatus = AssignmentStatus.valueOf(currentEntity.status)
            if (isValidTransition(fromStatus, status)) {
                assignmentDao.updateAssignmentStatus(id, status.name, System.currentTimeMillis())
            }
        }
    }

    override suspend fun assignVolunteer(
        reportId: String,
        volunteerId: String,
        volunteerName: String
    ) {
        val existingAssignments = assignmentDao.getAssignmentsForReport(reportId).first()
        val existing = existingAssignments.firstOrNull()

        if (existing != null) {
            val updated = existing.copy(
                volunteerId = volunteerId,
                volunteerName = volunteerName,
                status = AssignmentStatus.ASSIGNED.name,
                updatedAt = System.currentTimeMillis()
            )
            assignmentDao.insertAssignment(updated)
        } else {
            val newAssignment = AssignmentEntity(
                id = "ASGN-$reportId",
                reportId = reportId,
                volunteerId = volunteerId,
                volunteerName = volunteerName,
                victimName = "Victim",
                disasterType = "SOS Alert",
                location = "Unknown",
                status = AssignmentStatus.ASSIGNED.name,
                priority = Priority.HIGH.name,
                assignedTime = "Just now",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            assignmentDao.insertAssignment(newAssignment)
        }
    }

    private fun isValidTransition(
        from: AssignmentStatus,
        to: AssignmentStatus
    ): Boolean {
        return when (from) {
            AssignmentStatus.AVAILABLE ->
                to == AssignmentStatus.ASSIGNED

            AssignmentStatus.ASSIGNED ->
                to == AssignmentStatus.IN_PROGRESS ||
                    to == AssignmentStatus.AVAILABLE

            AssignmentStatus.IN_PROGRESS ->
                to == AssignmentStatus.RESOLVED

            AssignmentStatus.RESOLVED ->
                false
        }
    }
}
