package com.example.rsq.data.model

data class Donation(
    val id: String,
    val donorName: String,
    val amount: Double,
    val date: String,
    val status: String
)

data class DonationSummary(
    val totalAmount: Double,
    val currency: String = "$"
)
