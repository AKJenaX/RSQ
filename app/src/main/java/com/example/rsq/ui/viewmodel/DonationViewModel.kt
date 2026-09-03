package com.example.rsq.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import com.example.rsq.data.network.OrderRequest
import com.example.rsq.data.network.RazorpayService
import com.example.rsq.data.network.VerificationRequest
import com.example.rsq.data.repository.DonationRepository
import com.example.rsq.data.repository.DonationRepositoryImpl
import com.example.rsq.data.network.NetworkModule
import com.example.rsq.data.repository.FirestoreDonationRepository
import com.example.rsq.util.PaymentResultHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class PaymentState {
    object Idle : PaymentState()
    object CreatingOrder : PaymentState()
    data class OrderCreated(val orderId: String, val amount: Double) : PaymentState()
    object Verifying : PaymentState()
    object Success : PaymentState()
    data class Failure(val message: String) : PaymentState()
}

class DonationViewModel(
    private val repository: DonationRepository = FirestoreDonationRepository(),
    private val razorpayService: RazorpayService = NetworkModule.razorpayService
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Pair<DonationSummary, List<Donation>>>>(UiState.Loading)
    val uiState: StateFlow<UiState<Pair<DonationSummary, List<Donation>>>> = _uiState.asStateFlow()

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    private var currentDonorName: String = "Anonymous"
    private var currentUserId: String = ""

    init {
        loadData()
        observePaymentResults()
    }

    private fun observePaymentResults() {
        viewModelScope.launch {
            PaymentResultHandler.successEvent.collect { data ->
                if (_paymentState.value is PaymentState.OrderCreated) {
                    val amount = (_paymentState.value as PaymentState.OrderCreated).amount
                    handlePaymentSuccess(
                        orderId = data.orderId,
                        paymentId = data.paymentId,
                        signature = data.signature,
                        amount = amount,
                        donorName = currentDonorName,
                        userId = currentUserId
                    )
                }
            }
        }

        viewModelScope.launch {
            PaymentResultHandler.errorEvent.collect { data ->
                _paymentState.value = PaymentState.Failure("Payment failed (${data.code}): ${data.message}")
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            combine(
                repository.getDonationSummary(),
                repository.getRecentDonations()
            ) { summary, donations ->
                summary to donations
            }
            .catch { e ->
                _uiState.value = UiState.Error("Access denied or connection lost: ${e.message}")
            }
            .collect { (summary, donations) ->
                if (donations.isEmpty() && summary.totalAmount == 0.0) {
                    _uiState.value = UiState.Empty
                } else {
                    _uiState.value = UiState.Success(summary to donations)
                }
            }
        }
    }

    fun startDonationFlow(amount: Double, donorName: String, userId: String) {
        currentDonorName = donorName
        currentUserId = userId
        viewModelScope.launch {
            _paymentState.value = PaymentState.CreatingOrder
            try {
                Log.d("DONATION", "[DONATION] creating Razorpay order")
                // Convert amount to paise with rounding to avoid floating point issues
                val amountInPaise = Math.round(amount * 100).toInt()
                val response = razorpayService.createOrder(
                    OrderRequest(
                        amount = amountInPaise,
                        receipt = "receipt_${System.currentTimeMillis()}"
                    )
                )
                Log.d("DONATION", "[DONATION] backend order created = ${response.id}")
                _paymentState.value = PaymentState.OrderCreated(response.id, amount)
            } catch (e: Exception) {
                Log.e("DONATION", "Order creation failed", e)
                _paymentState.value = PaymentState.Failure("Failed to initiate payment: ${e.message}")
            }
        }
    }

    fun handlePaymentSuccess(
        orderId: String,
        paymentId: String,
        signature: String,
        amount: Double,
        donorName: String,
        userId: String
    ) {
        viewModelScope.launch {
            _paymentState.value = PaymentState.Verifying
            try {
                val response = razorpayService.verifyPayment(
                    VerificationRequest(
                        razorpay_order_id = orderId,
                        razorpay_payment_id = paymentId,
                        razorpay_signature = signature,
                        amount = amount,
                        donor_name = donorName,
                        user_id = userId
                    )
                )

                if (response.status == "success") {
                    // We no longer write to repository here. 
                    // The backend has already written to Firestore.
                    // The UI will auto-refresh if it's observing the Firestore collection.
                    _paymentState.value = PaymentState.Success
                } else {
                    _paymentState.value = PaymentState.Failure("Payment verification failed")
                }
            } catch (e: Exception) {
                _paymentState.value = PaymentState.Failure("Verification error: ${e.message}")
            }
        }
    }

    fun resetPaymentState() {
        _paymentState.value = PaymentState.Idle
    }
}
