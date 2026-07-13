package com.example.rsq.data.repository

import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AssignmentRepositoryImpl : AssignmentRepository {
    override fun getAssignments(): Flow<List<Assignment>> = flowOf(
        listOf(
            Assignment(
                id = "ASGN-2024-01",
                volunteerName = "Alex Rivera",
                victimName = "Sarah Connor",
                disasterType = "Earthquake Rescue",
                location = "North Ridge, Building 4",
                assignedTime = "08:45 AM",
                priority = Priority.HIGH
            ),
            Assignment(
                id = "ASGN-2024-05",
                volunteerName = "Jordan Smith",
                victimName = "Mark Miller",
                disasterType = "Flood Rescue",
                location = "Riverside Drive, Zone B",
                assignedTime = "09:15 AM",
                priority = Priority.HIGH
            ),
            Assignment(
                id = "ASGN-2024-12",
                volunteerName = "Elena Gilbert",
                victimName = "Unknown (Victim-12)",
                disasterType = "Fire Emergency",
                location = "Industrial Park, Warehouse 7",
                assignedTime = "10:30 AM",
                priority = Priority.HIGH
            )
        )
    )
}
