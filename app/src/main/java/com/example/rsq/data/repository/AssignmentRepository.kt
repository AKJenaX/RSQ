package com.example.rsq.data.repository

import com.example.rsq.data.model.Assignment
import kotlinx.coroutines.flow.Flow

interface AssignmentRepository {
    fun getAssignments(): Flow<List<Assignment>>
}
