package com.example.rsq.auth.model

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object VerificationSent : AuthState()
    object EmailNotVerified : AuthState()
    data class Success(val message: String) : AuthState()
    object LoggedOut : AuthState()
    data class Error(val message: String) : AuthState()
}
