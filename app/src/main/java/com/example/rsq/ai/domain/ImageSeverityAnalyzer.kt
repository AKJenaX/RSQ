package com.example.rsq.ai.domain

import android.net.Uri
import com.example.rsq.ai.model.ImageAnalysisResult

/**
 * Interface for analyzing the severity of an emergency based on image input.
 */
interface ImageSeverityAnalyzer {
    /**
     * Analyzes an image from a local file URI.
     */
    suspend fun analyze(imageUri: Uri): ImageAnalysisResult
}
