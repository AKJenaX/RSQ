package com.example.rsq.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.auth.data.AuthRepository
import com.example.rsq.auth.model.AuthState
import com.example.rsq.auth.model.User
import com.example.rsq.data.repository.VolunteerRepository
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
    private val volunteerRepository: VolunteerRepository? = null
) : ViewModel() {

    // Persistent scope for cross-screen tasks like Mesh Relay
    val internalScope: CoroutineScope get() = viewModelScope

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<User?>(null)
    val currentUserProfile: StateFlow<User?> = _currentUserProfile.asStateFlow()

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.register(email, password)
            if (result.isSuccess) {
                val firebaseUser = result.getOrThrow()
                val uid = firebaseUser.uid
                // EXPLICITLY use the entered name and email for the Firestore profile
                val newUser = User(
                    firebaseUid = uid,
                    name = name,
                    email = email,
                    isAuthorized = false
                )

                // Save profile to Firestore
                val saveResult = repository.saveUserProfile(newUser)
                if (saveResult.isSuccess) {
                    _currentUserProfile.value = newUser
                    // Send verification email
                    repository.sendEmailVerification()
                    _authState.value = AuthState.VerificationSent
                } else {
                    val exception = saveResult.exceptionOrNull()
                    val message = when (exception) {
                        is FirebaseFirestoreException -> "Account created but profile failed: ${exception.code}"
                        else -> "Account created but profile could not be saved. Please try logging in."
                    }
                    _authState.value = AuthState.Error(message)
                }
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
                val firebaseUser = result.getOrThrow()
                if (firebaseUser.isEmailVerified) {
                    loadProfile(firebaseUser.uid)
                    _authState.value = AuthState.Success("Login successful")
                } else {
                    _authState.value = AuthState.EmailNotVerified
                }
            } else {
                _authState.value = AuthState.Error(mapAuthError(result.exceptionOrNull()))
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.signInWithGoogle(idToken)
            if (result.isSuccess) {
                val firebaseUser = result.getOrThrow()
                val uid = firebaseUser.uid

                // Check if profile exists, if not create it
                val profileResult = repository.getUserProfile(uid)
                if (profileResult.isSuccess && profileResult.getOrNull() != null) {
                    _currentUserProfile.value = profileResult.getOrNull()
                } else {
                    val newUser = User(
                        firebaseUid = uid,
                        name = firebaseUser.displayName ?: "Google User",
                        email = firebaseUser.email ?: "",
                        isAuthorized = false
                    )
                    repository.saveUserProfile(newUser)
                    _currentUserProfile.value = newUser
                }
                _authState.value = AuthState.Success("Google sign-in successful")
            } else {
                _authState.value = AuthState.Error(mapAuthError(result.exceptionOrNull()))
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.sendPasswordResetEmail(email)
            if (result.isSuccess) {
                _authState.value = AuthState.Success("Password reset email sent")
            } else {
                _authState.value = AuthState.Error(mapAuthError(result.exceptionOrNull()))
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            repository.sendEmailVerification()
        }
    }

    fun checkEmailVerification() {
        viewModelScope.launch {
            val result = repository.reloadUser()
            if (result.isSuccess) {
                val user = result.getOrThrow()
                if (user.isEmailVerified) {
                    loadProfile(user.uid)
                    _authState.value = AuthState.Success("Email verified")
                } else {
                    _authState.value = AuthState.EmailNotVerified
                }
            }
        }
    }

    private fun loadProfile(uid: String) {
        viewModelScope.launch {
            val result = repository.getUserProfile(uid)
            val profile = result.getOrNull()
            if (profile != null) {
                _currentUserProfile.value = profile
            } else {
                // Step 5 Fallback: provide basic info from Firebase Auth if Firestore document is missing
                _currentUserProfile.value = User(
                    firebaseUid = uid,
                    name = repository.getCurrentUserDisplayName() ?: "RSQ User",
                    email = repository.getCurrentUserEmail() ?: "No email available",
                    isAuthorized = false
                )
            }
        }
    }

    fun selectRole(role: String) {
        viewModelScope.launch {
            val uid = repository.getCurrentUserId() ?: return@launch
            val result = repository.updateRole(uid, role)
            if (result.isSuccess) {
                _currentUserProfile.value = _currentUserProfile.value?.copy(role = role)

                if (role == "VOLUNTEER" && volunteerRepository != null) {
                    val name = _currentUserProfile.value?.name ?: "RSQ Responder"
                    volunteerRepository.createVolunteerProfile(uid, name)
                }
            }
        }
    }

    fun logout() {
        repository.logout()
        _currentUserProfile.value = null
        _authState.value = AuthState.LoggedOut
    }

    fun checkSession() {
        if (repository.isLoggedIn()) {
            val uid = repository.getCurrentUserId() ?: ""
            if (repository.isEmailVerified()) {
                loadProfile(uid)
                _authState.value = AuthState.Success("Session restored")
            } else {
                _authState.value = AuthState.EmailNotVerified
            }
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun getCurrentUserId(): String = repository.getCurrentUserId() ?: "anonymous"

    fun getCurrentUserEmail(): String = repository.getCurrentUserEmail() ?: ""

    private fun mapAuthError(exception: Throwable?): String {
        val message = exception?.message ?: ""
        return when {
            message.contains("invalid-email", true) -> "Enter a valid email address."
            message.contains("user-not-found", true) -> "No account exists with this email."
            message.contains("wrong-password", true) -> "Incorrect password. Please try again."
            message.contains("email-already-in-use", true) -> "This email is already registered."
            message.contains("weak-password", true) -> "Password should be at least 6 characters."
            message.contains("network-request-failed", true) -> "Unable to connect. Check your internet and try again."
            message.contains("too-many-requests", true) -> "Too many requests. Please try again later."
            exception is FirebaseAuthInvalidCredentialsException -> "Invalid credentials."
            exception is FirebaseAuthInvalidUserException -> "User account not found."
            exception is FirebaseAuthUserCollisionException -> "Account already exists with this email."
            else -> exception?.localizedMessage ?: "Authentication failed. Please try again."
        }
    }
}
