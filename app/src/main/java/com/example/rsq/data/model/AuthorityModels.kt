package com.example.rsq.data.model

data class AuthorityDashboardStats(
    val totalReports: Int,
    val activeCases: Int,
    val pendingCases: Int,
    val resolvedCases: Int,
    val activeVolunteers: Int,
    val totalDonations: Double
)

data class RecentReport(
    val id: String,
    val type: String,
    val description: String,
    val reporterName: String,
    val location: String,
    val status: String,
    val priority: Priority,
    val timestamp: Long
)
