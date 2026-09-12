package com.example.rsq.data.repository

import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.Assignment
import kotlinx.coroutines.flow.Flow

interface VolunteerRepository {
    fun getVolunteerData(firebaseUid: String): Flow<Volunteer?>
    fun getAssignments(volunteerId: String): Flow<List<Assignment>>
    fun getAllVolunteers(): Flow<List<Volunteer>>
    fun getAvailableVolunteers(): Flow<List<Volunteer>>
    suspend fun createVolunteerProfile(firebaseUid: String, name: String)
    suspend fun updateAvailability(firebaseUid: String, isAvailable: Boolean)
    fun startRealtimeVolunteerSync()
    fun stopRealtimeVolunteerSync()
}
