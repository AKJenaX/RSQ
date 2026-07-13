package com.example.rsq.data.repository

import com.example.rsq.data.model.Priority
import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.VolunteerAssignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class VolunteerRepositoryImpl : VolunteerRepository {
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

    override fun getAssignments(): Flow<List<VolunteerAssignment>> = flowOf(
        listOf(
            VolunteerAssignment(
                reportId = "EQ-701",
                disasterType = "Earthquake Rescue",
                location = "North Ridge Apartments, Sector 7",
                priority = Priority.HIGH,
                status = "Pending"
            ),
            VolunteerAssignment(
                reportId = "FL-322",
                disasterType = "Flood Rescue",
                location = "Lowland Plains, Riverside Drive",
                priority = Priority.HIGH,
                status = "Pending"
            ),
            VolunteerAssignment(
                reportId = "FE-105",
                disasterType = "Fire Emergency",
                location = "Downtown Commercial Hub",
                priority = Priority.MEDIUM,
                status = "Active"
            ),
            VolunteerAssignment(
                reportId = "MD-904",
                disasterType = "Medical Aid",
                location = "St. Jude Community Center",
                priority = Priority.LOW,
                status = "Pending"
            )
        )
    )
}
