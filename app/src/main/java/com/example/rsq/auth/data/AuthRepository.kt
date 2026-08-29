package com.example.rsq.auth.data

import android.util.Log
import com.example.rsq.auth.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

class AuthRepository(
    internal val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "AuthRepository"

    suspend fun register(email: String, password: String): Result<FirebaseUser> {
        Log.i(TAG, "AUTH_REGISTER_STARTED: $email")
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("User creation failed")
            Log.i(TAG, "AUTH_REGISTER_SUCCESS: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "AUTH_REGISTER_FAILED for $email: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Login failed")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed for $email: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("Google sign-in failed")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Verification email failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed for $email: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun reloadUser(): Result<FirebaseUser> {
        return try {
            val user = firebaseAuth.currentUser ?: throw Exception("No user logged in")
            user.reload().await()
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "User reload failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(user: User): Result<Unit> {
        val uid = user.firebaseUid.ifBlank { firebaseAuth.currentUser?.uid }
        if (uid.isNullOrBlank()) {
            Log.e(TAG, "USER_PROFILE_CREATE_FAILED: UID is empty")
            return Result.failure(Exception("Cannot save profile: UID is empty"))
        }

        Log.i(TAG, "USER_PROFILE_CREATE_STARTED: users/$uid")
        return try {
            firestore.collection("users").document(uid).set(user).await()
            Log.i(TAG, "USER_PROFILE_CREATE_SUCCESS: users/$uid")
            Result.success(Unit)
        } catch (e: Exception) {
            val errorType = when (e) {
                is FirebaseFirestoreException -> "FIRESTORE_ERROR(${e.code})"
                else -> "UNKNOWN_ERROR"
            }
            Log.e(TAG, "USER_PROFILE_CREATE_FAILED for users/$uid. Type: $errorType, Message: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): Result<User?> {
        if (uid.isBlank()) return Result.failure(Exception("UID is blank"))

        return try {
            val document = firestore.collection("users").document(uid).get().await()
            if (document.exists()) {
                val profile = document.toObject(User::class.java)
                Result.success(profile)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore GET failed for users/$uid: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateRole(uid: String, role: String): Result<Unit> {
        return try {
            firestore.collection("users").document(uid).update("role", role).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Role update failed for users/$uid: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun logout() {
        firebaseAuth.signOut()
    }

    fun isLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    fun isEmailVerified(): Boolean {
        return firebaseAuth.currentUser?.isEmailVerified ?: false
    }

    fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

    fun getCurrentUserDisplayName(): String? {
        return firebaseAuth.currentUser?.displayName
    }
}
