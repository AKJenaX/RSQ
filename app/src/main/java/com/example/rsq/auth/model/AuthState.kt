package com.example.rsq.auth.model

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    object LoggedOut : AuthState()
    data class Error(val message: String) : AuthState()
}
