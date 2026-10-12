package com.example.rsq.auth.domain

interface UserIdentityProvider {
    fun getUserId(): String
}