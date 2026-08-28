package com.example.rsq.data.repository

import com.example.rsq.data.local.AssignmentDao
import com.example.rsq.data.local.AssignmentEntity
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Priority
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AssignmentRepositoryTest {

    private lateinit var repository: AssignmentRepository
    private lateinit var fakeDao: FakeAssignmentDao

    @Before
    fun setUp() {
        fakeDao = FakeAssignmentDao()
        repository = AssignmentRepositoryImpl(fakeDao)
    }

    @Test
    fun `test create and retrieve assignment`() = runBlocking {
        val newAssignment = createTestAssignment("ASGN-001")
        repository.createAssignment(newAssignment)
        
        val assignments = repository.getAssignments().first()
        assertEquals(1, assignments.size)
        assertEquals("ASGN-001", assignments[0].id)
    }

    @Test
    fun `test valid transition available to assigned`() = runBlocking {
        val available = createTestAssignment("ASGN-AVAIL", AssignmentStatus.AVAILABLE)
        repository.createAssignment(available)
        
        repository.updateAssignmentStatus("ASGN-AVAIL", AssignmentStatus.ASSIGNED)
        
        val updated = repository.getAssignmentById("ASGN-AVAIL").first()
        assertEquals(AssignmentStatus.ASSIGNED, updated?.status)
    }

    @Test
    fun `test invalid transition available to resolved`() = runBlocking {
        val available = createTestAssignment("ASGN-AVAIL", AssignmentStatus.AVAILABLE)
        repository.createAssignment(available)
        
        repository.updateAssignmentStatus("ASGN-AVAIL", AssignmentStatus.RESOLVED)
        
        val updated = repository.getAssignmentById("ASGN-AVAIL").first()
        assertEquals(AssignmentStatus.AVAILABLE, updated?.status)
    }

    @Test
    fun `test assigned to available transition`() = runBlocking {
        val assigned = createTestAssignment("ASGN-1", AssignmentStatus.ASSIGNED)
        repository.createAssignment(assigned)
        
        repository.updateAssignmentStatus("ASGN-1", AssignmentStatus.AVAILABLE)
        
        val updated = repository.getAssignmentById("ASGN-1").first()
        assertEquals(AssignmentStatus.AVAILABLE, updated?.status)
    }

    private fun createTestAssignment(id: String, status: AssignmentStatus = AssignmentStatus.AVAILABLE): Assignment {
        return Assignment(
            id = id,
            reportId = "REPORT-001",
            volunteerId = "VOL-001",
            volunteerName = "Test Vol",
            victimName = "Test Vic",
            disasterType = "Test Disaster",
            location = "Test Loc",
            status = status,
            priority = Priority.MEDIUM,
            assignedTime = "12:00 PM",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    class FakeAssignmentDao : AssignmentDao {
        private val assignments = MutableStateFlow<Map<String, AssignmentEntity>>(emptyMap())

        override fun getAllAssignments(): Flow<List<AssignmentEntity>> = 
            assignments.map { it.values.toList().sortedByDescending { a -> a.updatedAt } }

        override fun getAssignmentById(id: String): Flow<AssignmentEntity?> = 
            assignments.map { it[id] }

        override suspend fun getAssignmentByIdOneShot(id: String): AssignmentEntity? = 
            assignments.value[id]

        override fun getAssignmentsForVolunteer(volunteerId: String): Flow<List<AssignmentEntity>> = 
            assignments.map { it.values.filter { a -> a.volunteerId == volunteerId } }

        override fun getAssignmentsForReport(reportId: String): Flow<List<AssignmentEntity>> = 
            assignments.map { it.values.filter { a -> a.reportId == reportId } }

        override suspend fun insertAssignment(assignment: AssignmentEntity) {
            assignments.update { it + (assignment.id to assignment) }
        }

        override suspend fun updateAssignment(assignment: AssignmentEntity) {
            assignments.update { it + (assignment.id to assignment) }
        }

        override suspend fun updateAssignmentStatus(id: String, status: String, updatedAt: Long) {
            assignments.update { map ->
                map[id]?.let { map + (id to it.copy(status = status, updatedAt = updatedAt)) } ?: map
            }
        }
    }
}
