package com.example.rsq.data.repository

import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DonationRepositoryImpl : DonationRepository {
    override fun getRecentDonations(): Flow<List<Donation>> = flowOf(
        listOf(
            Donation("DON-771", "Global Relief Org", 5000.0, "2024-05-22", "Completed"),
            Donation("DON-882", "Emma Watson", 1200.0, "2024-05-21", "Completed"),
            Donation("DON-105", "Anonymous Donor", 250.0, "2024-05-21", "Completed"),
            Donation("DON-662", "Local Business Alliance", 3500.0, "2024-05-20", "Completed"),
            Donation("DON-904", "David Miller", 100.0, "2024-05-19", "Processing")
        )
    )

    override fun getDonationSummary(): Flow<DonationSummary> = flowOf(
        DonationSummary(totalAmount = 85640.75)
    )
}
