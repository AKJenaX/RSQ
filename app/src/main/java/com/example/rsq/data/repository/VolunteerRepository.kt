package com.example.rsq.data.repository

import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.Assignment
import kotlinx.coroutines.flow.Flow

interface VolunteerRepository {
    fun getVolunteerData(): Flow<Volunteer>
    fun getAssignments(): Flow<List<Assignment>>
    fun getAllVolunteers(): Flow<List<Volunteer>>
}
