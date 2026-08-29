package com.example.rsq.ai.model

/**
 * Represents the final multimodal severity analysis.
 *
 * @property imageScore The score contribution from image analysis.
 * @property textScore The score contribution from text analysis.
 * @property finalScore The fused severity score in range 0.0..1.0.
 * @property severity The mapped severity label (LOW, MEDIUM, HIGH, CRITICAL).
 * @property confidence Overall confidence in the analysis.
 * @property reason Detailed justification for the final score.
 * @property detectedHazards Unified list of hazards from all inputs.
 * @property recommendedResources List of resources recommended based on hazards.
 */
data class SeverityAnalysisResult(
    val imageScore: Float,
    val textScore: Float,
    val finalScore: Float,
    val severity: String,
    val confidence: Float,
    val reason: String,
    val detectedHazards: List<HazardType>,
    val recommendedResources: List<String>
)
