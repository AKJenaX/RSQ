package com.example.rsq.data.repository

import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Priority
import kotlinx.coroutines.flow.*

class AssignmentRepositoryImpl : AssignmentRepository {

    companion object {
        private fun initialAssignments(): List<Assignment> {
            return listOf(
                Assignment(
                    id = "ASGN-2024-01",
                    reportId = "SOS-911",
                    volunteerId = "VOL-001",
                    volunteerName = "Alex Rivera",
                    victimName = "Sarah Connor",
                    disasterType = "Earthquake Rescue",
                    location = "North Ridge, Building 4",
                    status = AssignmentStatus.ASSIGNED,
                    priority = Priority.HIGH,
                    assignedTime = "08:45 AM",
                    createdAt = System.currentTimeMillis() - 86400000,
                    updatedAt = System.currentTimeMillis() - 3600000
                ),
                Assignment(
                    id = "ASGN-2024-05",
                    reportId = "SOS-402",
                    volunteerId = null,
                    volunteerName = "Unassigned",
                    victimName = "Mark Miller",
                    disasterType = "Flood Rescue",
                    location = "Riverside Drive, Zone B",
                    status = AssignmentStatus.AVAILABLE,
                    priority = Priority.HIGH,
                    assignedTime = "09:15 AM",
                    createdAt = System.currentTimeMillis() - 43200000,
                    updatedAt = System.currentTimeMillis() - 21600000
                )
            )
        }

        private val _assignments = MutableStateFlow(initialAssignments())

        internal fun resetForTests() {
            _assignments.value = initialAssignments()
        }
    }

    override fun getAssignments(): Flow<List<Assignment>> =
        _assignments.asStateFlow()

    override fun getAssignmentById(id: String): Flow<Assignment?> =
        _assignments.map { list ->
            list.find { it.id == id }
        }

    override fun getAssignmentsForVolunteer(
        volunteerId: String
    ): Flow<List<Assignment>> =
        _assignments.map { list ->
            list.filter { it.volunteerId == volunteerId }
        }

    override fun getAssignmentsForReport(
        reportId: String
    ): Flow<List<Assignment>> =
        _assignments.map { list ->
            list.filter { it.reportId == reportId }
        }

    override suspend fun createAssignment(
        assignment: Assignment
    ) {
        _assignments.update {
            it + assignment
        }
    }

    override suspend fun updateAssignmentStatus(
        id: String,
        status: AssignmentStatus
    ) {
        _assignments.update { list ->
            list.map { assignment ->
                if (assignment.id == id) {
                    if (isValidTransition(assignment.status, status)) {
                        assignment.copy(
                            status = status,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        assignment
                    }
                } else {
                    assignment
                }
            }
        }
    }

    override suspend fun assignVolunteer(
        reportId: String,
        volunteerId: String,
        volunteerName: String
    ) {
        _assignments.update { list ->
            val existingIndex =
                list.indexOfFirst { it.reportId == reportId }

            if (existingIndex != -1) {
                list.mapIndexed { index, assignment ->
                    if (index == existingIndex) {
                        assignment.copy(
                            volunteerId = volunteerId,
                            volunteerName = volunteerName,
                            status = AssignmentStatus.ASSIGNED,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        assignment
                    }
                }
            } else {
                list + Assignment(
                    id = "ASGN-$reportId",
                    reportId = reportId,
                    volunteerId = volunteerId,
                    volunteerName = volunteerName,
                    victimName = "Victim",
                    disasterType = "SOS Alert",
                    location = "Unknown",
                    status = AssignmentStatus.ASSIGNED,
                    priority = Priority.HIGH,
                    assignedTime = "Just now",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
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