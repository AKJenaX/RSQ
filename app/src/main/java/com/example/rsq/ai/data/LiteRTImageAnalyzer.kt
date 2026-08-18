package com.example.rsq.ai.data

import android.net.Uri
import com.example.rsq.ai.domain.ImageSeverityAnalyzer
import com.example.rsq.ai.model.ImageAnalysisResult

/**
 * Placeholder implementation of [ImageSeverityAnalyzer] for future LiteRT integration.
 */
class LiteRTImageAnalyzer : ImageSeverityAnalyzer {

    override suspend fun analyze(imageUri: Uri): ImageAnalysisResult {
        // Real model integration will occur in Phase AI-2.
        // For now, return unavailable state as required.
        return ImageAnalysisResult.unavailable("Image AI model (LiteRT) not yet integrated.")
    }
}
