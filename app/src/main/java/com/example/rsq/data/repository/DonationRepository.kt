package com.example.rsq.data.repository

import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import kotlinx.coroutines.flow.Flow

interface DonationRepository {
    fun getRecentDonations(): Flow<List<Donation>>
    fun getDonationSummary(): Flow<DonationSummary>
    suspend fun addDonation(donation: Donation)
}
