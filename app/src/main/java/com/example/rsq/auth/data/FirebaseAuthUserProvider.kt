package com.example.rsq.auth.data

import com.google.firebase.auth.FirebaseAuth
import com.example.rsq.auth.domain.UserIdentityProvider

class FirebaseAuthUserProvider : UserIdentityProvider {
    override fun getUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }
}