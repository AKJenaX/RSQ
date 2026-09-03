package com.example.rsq.data.model

data class Donation(
    val id: String = "",
    val userId: String = "",
    val donorName: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val status: String = "Pending",
    val orderId: String? = null,
    val paymentId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DonationSummary(
    val totalAmount: Double = 0.0,
    val currency: String = "₹"
)
