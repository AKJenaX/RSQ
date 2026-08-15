package com.example.rsq.data.repository

import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import kotlinx.coroutines.flow.Flow

interface AssignmentRepository {
    fun getAssignments(): Flow<List<Assignment>>
    fun getAssignmentById(id: String): Flow<Assignment?>
    fun getAssignmentsForVolunteer(volunteerId: String): Flow<List<Assignment>>
    fun getAssignmentsForReport(reportId: String): Flow<List<Assignment>>
    suspend fun createAssignment(assignment: Assignment)
    suspend fun updateAssignmentStatus(id: String, status: AssignmentStatus)
    suspend fun assignVolunteer(assignmentId: String, volunteerId: String, volunteerName: String)
}
