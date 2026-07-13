package com.example.rsq.data.repository

import com.example.rsq.data.model.AuthorityDashboardStats
import com.example.rsq.data.model.Priority
import com.example.rsq.data.model.RecentReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AuthorityRepositoryImpl : AuthorityRepository {
    override fun getDashboardStats(): Flow<AuthorityDashboardStats> = flowOf(
        AuthorityDashboardStats(
            totalReports = 1248,
            activeCases = 15,
            pendingCases = 8,
            resolvedCases = 1225,
            activeVolunteers = 156,
            totalDonations = 85640.75
        )
    )

    override fun getRecentReports(): Flow<List<RecentReport>> = flowOf(
        listOf(
            RecentReport("SOS-911", "Earthquake", "Victim-102", "North Ridge", "Active", Priority.HIGH, System.currentTimeMillis()),
            RecentReport("SOS-402", "Flood", "Victim-215", "River Delta", "Pending", Priority.HIGH, System.currentTimeMillis() - 1800000),
            RecentReport("SOS-105", "Fire", "Victim-088", "Industrial Area", "Active", Priority.HIGH, System.currentTimeMillis() - 3600000),
            RecentReport("SOS-672", "Medical", "Victim-441", "West Suburbs", "Resolved", Priority.MEDIUM, System.currentTimeMillis() - 7200000)
        )
    )
}
