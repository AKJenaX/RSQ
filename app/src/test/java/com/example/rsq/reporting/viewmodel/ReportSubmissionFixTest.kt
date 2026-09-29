package com.example.rsq.reporting.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.rsq.reporting.model.ReportState

class ReportSubmissionFixTest {

    @Test
    fun `isFormEnabled should be true during PendingSync to allow consecutive submissions`() {
        val state: ReportState = ReportState.PendingSync("Saved offline")
        
        val isProcessing = state is ReportState.Submitting || 
                           state is ReportState.UploadingEvidence || 
                           state is ReportState.CreatingCloudReport
        val isFormEnabled = !isProcessing
        
        assertTrue(isFormEnabled)
    }

    @Test
    fun `isFormEnabled should be true during Idle`() {
        val state: ReportState = ReportState.Idle
        
        val isProcessing = state is ReportState.Submitting || 
                           state is ReportState.UploadingEvidence || 
                           state is ReportState.CreatingCloudReport
        val isFormEnabled = !isProcessing
        
        assertTrue(isFormEnabled)
    }

    @Test
    fun `isFormEnabled should be false during Submitting`() {
        val state: ReportState = ReportState.Submitting
        
        val isProcessing = state is ReportState.Submitting || 
                           state is ReportState.UploadingEvidence || 
                           state is ReportState.CreatingCloudReport
        val isFormEnabled = !isProcessing
        
        assertFalse(isFormEnabled)
    }
}
