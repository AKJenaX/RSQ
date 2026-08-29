package com.example.rsq.ai.model

/**
 * Represents the result of a textual severity analysis.
 *
 * @property score Normalized severity score in range 0.0..1.0.
 * @property confidence Normalized confidence score in range 0.0..1.0.
 * @property detectedHazards List of hazard types identified in the text.
 * @property reason Brief justification for the result.
 */
data class TextAnalysisResult(
    val score: Float,
    val confidence: Float,
    val detectedHazards: List<HazardType>,
    val reason: String
)
