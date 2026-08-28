package com.example.rsq.data.repository

import com.example.rsq.data.model.AuthorityDashboardStats
import com.example.rsq.data.model.RecentReport
import kotlinx.coroutines.flow.Flow

interface AuthorityRepository {
    fun getDashboardStats(): Flow<AuthorityDashboardStats>
    fun getRecentReports(): Flow<List<RecentReport>>
}
