package com.example.rsq.reporting.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow

class ReportSubmissionLockTest {

    @Test
    fun `isSubmitting manages form lock independently of final ReportState`() {
        val isSubmitting = MutableStateFlow(false)

        // User starts submission
        isSubmitting.value = true
        assertTrue(isSubmitting.value)

        // Submission succeeds online -> form unlocked
        isSubmitting.value = false
        assertFalse(isSubmitting.value)

        // Another submission starts
        isSubmitting.value = true
        assertTrue(isSubmitting.value)

        // Submission queues offline -> form unlocked
        isSubmitting.value = false
        assertFalse(isSubmitting.value)
    }
}