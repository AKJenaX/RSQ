package com.example.rsq.data.repository

import com.example.rsq.data.model.AuthorityDashboardStats
import com.example.rsq.data.model.Priority
import com.example.rsq.data.model.RecentReport
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.SOSMessage
import kotlinx.coroutines.flow.*
import java.util.Locale

class AuthorityRepositoryImpl(
    private val assignmentRepository: AssignmentRepository = AssignmentRepositoryImpl(),
    private val donationRepository: DonationRepository = DonationRepositoryImpl(),
    messageRepository: MessageRepository = MessageRepository
) : AuthorityRepository {
    
    private val _mockReports = listOf(
        RecentReport("SOS-911", "Earthquake", "Victim-102", "North Ridge", "Active", Priority.HIGH, System.currentTimeMillis() - 3600000),
        RecentReport("SOS-402", "Flood", "Victim-215", "River Delta", "Pending", Priority.HIGH, System.currentTimeMillis() - 1800000)
    )

    private val _reports = messageRepository.messages.map { messages ->
        _mockReports + messages.map { it.toRecentReport() }
    }

    override fun getDashboardStats(): Flow<AuthorityDashboardStats> {
        return combine(
            getRecentReports(),
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

    override fun getRecentReports(): Flow<List<RecentReport>> = _reports

    private fun SOSMessage.toRecentReport(): RecentReport {
        return RecentReport(
            id = id,
            type = "SOS Alert",
            reporterName = "Victim",
            location = String.format(Locale.getDefault(), "Lat: %.2f, Lon: %.2f", latitude, longitude),
            status = "Pending",
            priority = priority,
            timestamp = timestamp
        )
    }
}
