package com.example.rsq.data.repository

import com.example.rsq.data.model.AuthorityDashboardStats
import com.example.rsq.data.model.Priority
import com.example.rsq.data.model.RecentReport
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.model.Report
import kotlinx.coroutines.flow.*
import java.util.Locale

class AuthorityRepositoryImpl(
    private val assignmentRepository: AssignmentRepository,
    private val donationRepository: DonationRepository,
    private val localReportRepository: LocalReportRepository? = null
) : AuthorityRepository {

    private val _reports = if (localReportRepository != null) {
        localReportRepository.observeAllReports().map { reports ->
            reports.map { it.toRecentReport() }
        }
    } else {
        flowOf(emptyList())
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

    private fun Report.toRecentReport(): RecentReport {
        val priority = when (severity.uppercase()) {
            "CRITICAL", "HIGH" -> Priority.HIGH
            "MEDIUM" -> Priority.MEDIUM
            else -> Priority.LOW
        }

        return RecentReport(
            id = id,
            type = title.ifBlank { "SOS Alert" },
            description = description,
            reporterName = "Victim",
            location = if (latitude != null && longitude != null) {
                String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", latitude, longitude)
            } else {
                "Location Unknown"
            },
            status = status.name,
            priority = priority,
            timestamp = timestamp
        )
    }
}
