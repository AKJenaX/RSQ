package com.example.rsq.data.repository

import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AssignmentRepositoryTest {

    private lateinit var repository: AssignmentRepository

    @Before
    fun setUp() {
        AssignmentRepositoryImpl.resetForTests()
        repository = AssignmentRepositoryImpl()
    }

    @Test
    fun `test initial assignments`() = runBlocking {
        val assignments = repository.getAssignments().first()
        assertTrue(assignments.isNotEmpty())
    }

    @Test
    fun `test valid transition available to assigned`() = runBlocking {
        val assignments = repository.getAssignments().first()
        val available = assignments.first { it.status == AssignmentStatus.AVAILABLE }
        
        repository.updateAssignmentStatus(available.id, AssignmentStatus.ASSIGNED)
        
        val updated = repository.getAssignmentById(available.id).first()
        assertEquals(AssignmentStatus.ASSIGNED, updated?.status)
    }

    @Test
    fun `test invalid transition available to resolved`() = runBlocking {
        val assignments = repository.getAssignments().first()
        val available = assignments.first { it.status == AssignmentStatus.AVAILABLE }
        
        repository.updateAssignmentStatus(available.id, AssignmentStatus.RESOLVED)
        
        val updated = repository.getAssignmentById(available.id).first()
        assertEquals(AssignmentStatus.AVAILABLE, updated?.status) // Should not change
    }

    @Test
    fun `test valid transition assigned to in_progress`() = runBlocking {
        val assignments = repository.getAssignments().first()
        val assigned = assignments.first { it.status == AssignmentStatus.ASSIGNED }
        
        repository.updateAssignmentStatus(assigned.id, AssignmentStatus.IN_PROGRESS)
        
        val updated = repository.getAssignmentById(assigned.id).first()
        assertEquals(AssignmentStatus.IN_PROGRESS, updated?.status)
    }

    @Test
    fun `test invalid transition resolved to in_progress`() = runBlocking {
        val assignments = repository.getAssignments().first()
        val firstId = assignments.first().id
        
        // Move to resolved first
        repository.updateAssignmentStatus(firstId, AssignmentStatus.ASSIGNED)
        repository.updateAssignmentStatus(firstId, AssignmentStatus.IN_PROGRESS)
        repository.updateAssignmentStatus(firstId, AssignmentStatus.RESOLVED)
        
        // Try to move back to in_progress
        repository.updateAssignmentStatus(firstId, AssignmentStatus.IN_PROGRESS)
        
        val updated = repository.getAssignmentById(firstId).first()
        assertEquals(AssignmentStatus.RESOLVED, updated?.status)
    }

    @Test
    fun `test create assignment`() = runBlocking {
        val newAssignment = Assignment(
            id = "TEST-001",
            reportId = "REPORT-001",
            volunteerId = "VOL-001",
            volunteerName = "Test Vol",
            victimName = "Test Vic",
            disasterType = "Test Disaster",
            location = "Test Loc",
            status = AssignmentStatus.AVAILABLE,
            priority = Priority.MEDIUM,
            assignedTime = "12:00 PM",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        repository.createAssignment(newAssignment)
        
        val assignments = repository.getAssignments().first()
        assertTrue(assignments.any { it.id == "TEST-001" })
    }
}
