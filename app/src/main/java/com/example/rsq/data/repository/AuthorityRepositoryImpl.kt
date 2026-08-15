package com.example.rsq.data.repository

import com.example.rsq.data.model.AuthorityDashboardStats
import com.example.rsq.data.model.Priority
import com.example.rsq.data.model.RecentReport
import com.example.rsq.data.model.AssignmentStatus
import kotlinx.coroutines.flow.*

class AuthorityRepositoryImpl(
    private val assignmentRepository: AssignmentRepository = AssignmentRepositoryImpl(),
    private val donationRepository: DonationRepository = DonationRepositoryImpl()
) : AuthorityRepository {
    
    private val _reports = MutableStateFlow(
        listOf(
            RecentReport("SOS-911", "Earthquake", "Victim-102", "North Ridge", "Active", Priority.HIGH, System.currentTimeMillis()),
            RecentReport("SOS-402", "Flood", "Victim-215", "River Delta", "Pending", Priority.HIGH, System.currentTimeMillis() - 1800000),
            RecentReport("SOS-105", "Fire", "Victim-088", "Industrial Area", "Active", Priority.HIGH, System.currentTimeMillis() - 3600000),
            RecentReport("SOS-672", "Medical", "Victim-441", "West Suburbs", "Resolved", Priority.MEDIUM, System.currentTimeMillis() - 7200000)
        )
    )

    override fun getDashboardStats(): Flow<AuthorityDashboardStats> {
        return combine(
            _reports,
            assignmentRepository.getAssignments(),
            donationRepository.getDonationSummary()
        ) { reports, assignments, donationSummary ->
            AuthorityDashboardStats(
                totalReports = reports.size,
                activeCases = assignments.count { it.status == AssignmentStatus.IN_PROGRESS },
                pendingCases = assignments.count { it.status == AssignmentStatus.AVAILABLE || it.status == AssignmentStatus.ASSIGNED },
                resolvedCases = assignments.count { it.status == AssignmentStatus.RESOLVED },
                activeVolunteers = assignments.mapNotNull { it.volunteerId }.distinct().size,
                totalDonations = donationSummary.totalAmount
            )
        }
    }

    override fun getRecentReports(): Flow<List<RecentReport>> = _reports.asStateFlow()
}
