package com.example.rsq.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PaymentSuccessData(
    val orderId: String,
    val paymentId: String,
    val signature: String
)

data class PaymentErrorData(
    val code: Int,
    val message: String
)

object PaymentResultHandler {
    private val _successEvent = MutableSharedFlow<PaymentSuccessData>()
    val successEvent = _successEvent.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<PaymentErrorData>()
    val errorEvent = _errorEvent.asSharedFlow()

    suspend fun emitSuccess(data: PaymentSuccessData) {
        _successEvent.emit(data)
    }

    suspend fun emitError(data: PaymentErrorData) {
        _errorEvent.emit(data)
    }
}
