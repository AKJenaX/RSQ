package com.example.rsq.ai.model

/**
 * Represents the result of an image-based severity analysis.
 *
 * @property score Normalized severity score in range 0.0..1.0.
 * @property confidence Normalized confidence score in range 0.0..1.0.
 * @property detectedHazards List of hazard types identified in the image.
 * @property reason Brief justification for the result.
 * @property isAvailable Whether the image analysis was actually performed.
 */
data class ImageAnalysisResult(
    val score: Float,
    val confidence: Float,
    val detectedHazards: List<HazardType>,
    val reason: String,
    val isAvailable: Boolean
) {
    companion object {
        fun unavailable(reason: String = "Image analysis not available"): ImageAnalysisResult {
            return ImageAnalysisResult(0f, 0f, emptyList(), reason, false)
        }
    }
}
