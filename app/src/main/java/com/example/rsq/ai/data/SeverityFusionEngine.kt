package com.example.rsq.ai.data

import android.util.Log
import com.example.rsq.ai.model.ImageAnalysisResult
import com.example.rsq.ai.model.SeverityAnalysisResult
import com.example.rsq.ai.model.TextAnalysisResult

/**
 * Fuses results from text and image analyzers into a single severity prediction.
 */
object SeverityFusionEngine {

    private const val IMAGE_WEIGHT = 0.70f
    private const val TEXT_WEIGHT = 0.30f

    /**
     * Fuses results using 70/30 weighting.
     * If image analysis is unavailable, text score is used as the baseline.
     */
    fun fuse(textResult: TextAnalysisResult, imageResult: ImageAnalysisResult): SeverityAnalysisResult {
        val branch = if (imageResult.isAvailable) "IMAGE_AVAILABLE_FUSION" else "IMAGE_UNAVAILABLE_TEXT_FALLBACK"
        
        val finalScore = if (imageResult.isAvailable) {
            (imageResult.score * IMAGE_WEIGHT + textResult.score * TEXT_WEIGHT).coerceIn(0f, 1f)
        } else {
            textResult.score // Fallback to text score if image is unavailable
        }

        val severity = mapScoreToSeverity(finalScore)
        val allHazards = (textResult.detectedHazards + imageResult.detectedHazards).distinct()
        
        val recommendations = ResourceRecommender.recommend(allHazards, severity)

        Log.d("SeverityFusionEngine", "Fusion Branch: $branch")
        Log.d("SeverityFusionEngine", "Text - Score: ${textResult.score}, Conf: ${textResult.confidence}, Hazards: ${textResult.detectedHazards}")
        Log.d("SeverityFusionEngine", "Image - Available: ${imageResult.isAvailable}, Score: ${imageResult.score}, Conf: ${imageResult.confidence}, Hazards: ${imageResult.detectedHazards}, Reason: ${imageResult.reason}")
        Log.d("SeverityFusionEngine", "Final - Score: $finalScore, Severity: $severity, Hazards: $allHazards, Resources: $recommendations")

        return SeverityAnalysisResult(
            imageScore = imageResult.score,
            textScore = textResult.score,
            finalScore = finalScore,
            severity = severity,
            confidence = (textResult.confidence + imageResult.confidence) / 2f,
            reason = "Fused analysis: Text (${textResult.reason}) + Image (${imageResult.reason})",
            detectedHazards = allHazards,
            recommendedResources = recommendations
        )
    }

    private fun mapScoreToSeverity(score: Float): String {
        return when {
            score >= 0.85f -> "CRITICAL"
            score >= 0.70f -> "HIGH"
            score >= 0.40f -> "MEDIUM"
            else -> "LOW"
        }
    }
}
