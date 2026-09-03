package com.example.rsq.data.repository

import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import kotlinx.coroutines.flow.*

class DonationRepositoryImpl : DonationRepository {
    companion object {
        private val _donations = MutableStateFlow<List<Donation>>(
            listOf(
                Donation("D-101", "u-1", "Anonymous", 500.0, "2024-03-01", "Completed"),
                Donation("D-102", "u-2", "Alex Rivera", 1200.0, "2024-03-02", "Completed"),
                Donation("D-103", "u-3", "Corporate Aid", 5000.0, "2024-03-05", "Pending")
            )
        )
    }

    override fun getRecentDonations(): Flow<List<Donation>> = _donations.asStateFlow()

    override fun getDonationSummary(): Flow<DonationSummary> = _donations.map { list ->
        DonationSummary(totalAmount = list.filter { it.status == "Completed" }.sumOf { it.amount })
    }

    override suspend fun addDonation(donation: Donation) {
        // No longer used in secure production flow
        _donations.update { it + donation }
    }
}
