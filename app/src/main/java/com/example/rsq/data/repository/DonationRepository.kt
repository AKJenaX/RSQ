package com.example.rsq.data.repository

import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import kotlinx.coroutines.flow.Flow

interface DonationRepository {
    fun getRecentDonations(userId: String): Flow<List<Donation>>
    fun getDonationSummary(): Flow<DonationSummary>
    suspend fun addDonation(donation: Donation)

    // One-shot methods for debugging and initial load
    suspend fun getRecentDonationsOneShot(userId: String): List<Donation>
    suspend fun getDonationSummaryOneShot(): DonationSummary
}
