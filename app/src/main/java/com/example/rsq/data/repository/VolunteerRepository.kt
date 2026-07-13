package com.example.rsq.data.repository

import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.VolunteerAssignment
import kotlinx.coroutines.flow.Flow

interface VolunteerRepository {
    fun getVolunteerData(): Flow<Volunteer>
    fun getAssignments(): Flow<List<VolunteerAssignment>>
}
