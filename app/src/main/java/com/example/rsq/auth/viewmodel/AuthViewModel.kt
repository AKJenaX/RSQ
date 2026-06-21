package com.example.rsq.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.auth.data.AuthRepository
import com.example.rsq.auth.model.AuthState
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.register(email, password)
            if (result.isSuccess) {
                _authState.value = AuthState.Success("Registration successful")
            } else {
                _authState.value = AuthState.Error(mapAuthError(result.exceptionOrNull()))
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.login(email, password)
            if (result.isSuccess) {
                _authState.value = AuthState.Success("Login successful")
            } else {
                _authState.value = AuthState.Error(mapAuthError(result.exceptionOrNull()))
            }
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.LoggedOut
    }

    fun checkSession() {
        if (repository.isLoggedIn()) {
            _authState.value = AuthState.Success("Session restored")
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    private fun mapAuthError(exception: Throwable?): String {
        return when (exception) {
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
            is FirebaseAuthInvalidUserException -> "Account does not exist"
            is FirebaseAuthUserCollisionException -> "Account already exists"
            else -> exception?.message ?: "Authentication failed"
        }
    }
}
