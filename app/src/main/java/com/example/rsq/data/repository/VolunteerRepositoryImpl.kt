package com.example.rsq.data.repository

import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.Assignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class VolunteerRepositoryImpl(
    private val assignmentRepository: AssignmentRepository = AssignmentRepositoryImpl()
) : VolunteerRepository {
    override fun getVolunteerData(): Flow<Volunteer> = flowOf(
        Volunteer(
            id = "VOL-882",
            name = "Alex Rivera",
            totalAssignments = 42,
            pendingAssignments = 3,
            activeAssignments = 1,
            completedAssignments = 38
        )
    )

    override fun getAssignments(): Flow<List<Assignment>> = assignmentRepository.getAssignments().map { list ->
        list.filter { it.volunteerId == "VOL-882" || it.volunteerId == null }
    }

    override fun getAllVolunteers(): Flow<List<Volunteer>> = flowOf(
        listOf(
            Volunteer("VOL-882", "Alex Rivera", 42, 3, 1, 38),
            Volunteer("VOL-001", "Sarah Wilson", 12, 1, 0, 11),
            Volunteer("VOL-002", "Mike Johnson", 25, 2, 1, 22),
            Volunteer("VOL-003", "Elena Rodriguez", 5, 0, 0, 5)
        )
    )
}
