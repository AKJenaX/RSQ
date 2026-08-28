package com.example.rsq.data.repository

import com.example.rsq.data.local.VolunteerDao
import com.example.rsq.data.local.VolunteerEntity
import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.Assignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class VolunteerRepositoryImpl(
    private val volunteerDao: VolunteerDao,
    private val assignmentRepository: AssignmentRepository
) : VolunteerRepository {
    
    override fun getVolunteerData(firebaseUid: String): Flow<Volunteer?> = 
        volunteerDao.getVolunteerByFirebaseUid(firebaseUid)
            .map { it?.toDomain() }

    override fun getAssignments(volunteerId: String): Flow<List<Assignment>> = 
        assignmentRepository.getAssignmentsForVolunteer(volunteerId)

    override fun getAllVolunteers(): Flow<List<Volunteer>> = 
        volunteerDao.getAllVolunteers()
            .onStart { seedInitialVolunteers() }
            .map { list ->
                list.map { it.toDomain() }
            }

    override suspend fun createVolunteerProfile(firebaseUid: String, name: String) {
        val existing = volunteerDao.getVolunteerByFirebaseUidOneShot(firebaseUid)
        if (existing == null) {
            val newVolunteer = Volunteer(
                id = "VOL-${firebaseUid.take(6).uppercase()}",
                name = name,
                totalAssignments = 0,
                pendingAssignments = 0,
                activeAssignments = 0,
                completedAssignments = 0
            )
            volunteerDao.insertVolunteer(VolunteerEntity.fromDomain(newVolunteer, firebaseUid))
        }
    }

    private suspend fun seedInitialVolunteers() {
        val current = volunteerDao.getAllVolunteers().first()
        if (current.isEmpty()) {
            val initialList = listOf(
                Volunteer("VOL-DEMO-01", "Sarah Wilson", 12, 1, 0, 11),
                Volunteer("VOL-DEMO-02", "Mike Johnson", 25, 2, 1, 22),
                Volunteer("VOL-DEMO-03", "Elena Rodriguez", 5, 0, 0, 5)
            )
            initialList.forEach { 
                volunteerDao.insertVolunteer(VolunteerEntity.fromDomain(it, null))
            }
        }
    }
}
