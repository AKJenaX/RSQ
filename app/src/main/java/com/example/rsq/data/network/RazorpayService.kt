package com.example.rsq.data.network

import retrofit2.http.Body
import retrofit2.http.POST

data class OrderRequest(
    val amount: Int, // in paise
    val currency: String = "INR",
    val receipt: String
)

data class OrderResponse(
    val id: String,
    val entity: String,
    val amount: Int,
    val currency: String,
    val status: String
)

data class VerificationRequest(
    val razorpay_order_id: String,
    val razorpay_payment_id: String,
    val razorpay_signature: String,
    val amount: Double,
    val donor_name: String,
    val user_id: String
)

data class VerificationResponse(
    val status: String,
    val message: String,
    val donation_id: String? = null
)

interface RazorpayService {
    @POST("donations/create_order")
    suspend fun createOrder(@Body request: OrderRequest): OrderResponse

    @POST("donations/verify_payment")
    suspend fun verifyPayment(@Body request: VerificationRequest): VerificationResponse
}
