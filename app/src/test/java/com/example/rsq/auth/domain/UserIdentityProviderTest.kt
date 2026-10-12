package com.example.rsq.auth.domain

import org.junit.Assert.assertNotNull
import org.junit.Test

class UserIdentityProviderTest {
    @Test
    fun `interface can be implemented`() {
        val provider = object : UserIdentityProvider {
            override fun getUserId() = "12345"
        }
        assertNotNull(provider.getUserId())
    }
}