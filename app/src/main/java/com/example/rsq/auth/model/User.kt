package com.example.rsq.auth.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val isAuthorized: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
